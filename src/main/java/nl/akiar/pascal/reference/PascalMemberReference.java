package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference for member access (e.g., Obj.Member).
 */
public class PascalMemberReference extends PsiReferenceBase<PsiElement> {
    private static final Logger LOG = Logger.getInstance(PascalMemberReference.class);
    private final String memberName;

    public PascalMemberReference(@NotNull PsiElement element, TextRange range) {
        super(element, range);
        this.memberName = element.getText().substring(range.getStartOffset(), range.getEndOffset());
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        // 1. Identify the qualifier
        PsiElement prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(myElement);
        while (prev != null && (prev instanceof PsiWhiteSpace || prev instanceof com.intellij.psi.PsiComment)) {
            prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev);
        }

        if (prev == null || prev.getNode().getElementType() != nl.akiar.pascal.PascalTokenTypes.DOT) {
            return null;
        }

        PsiElement qualifier = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev);
        while (qualifier != null && (qualifier instanceof PsiWhiteSpace || qualifier instanceof com.intellij.psi.PsiComment)) {
            qualifier = com.intellij.psi.util.PsiTreeUtil.prevLeaf(qualifier);
        }

        if (qualifier == null) return null;

        System.out.println("[DEBUG_LOG] [PascalNav] Qualifier for member access: '" + qualifier.getText() + "'");

        // 2. Resolve the qualifier to find its type
        PsiElement resolvedQualifier = null;
        PsiReference[] refs = qualifier.getReferences();
        System.out.println("[DEBUG_LOG] [PascalNav] Qualifier '" + qualifier.getText() + "' has " + refs.length + " references");
        for (PsiReference ref : refs) {
            resolvedQualifier = ref.resolve();
            if (resolvedQualifier != null) {
                System.out.println("[DEBUG_LOG] [PascalNav] Qualifier resolved via reference to: " + resolvedQualifier);
                break;
            }
        }

        if (resolvedQualifier == null) {
            System.out.println("[DEBUG_LOG] [PascalNav] Attempting manual resolution for qualifier: " + qualifier.getText());
            String qualifierName = qualifier.getText();
            resolvedQualifier = nl.akiar.pascal.stubs.PascalVariableIndex.findVariableAtPosition(qualifierName, qualifier.getContainingFile(), qualifier.getTextOffset());
            if (resolvedQualifier != null) {
                System.out.println("[DEBUG_LOG] [PascalNav] Qualifier resolved manually to variable: " + resolvedQualifier);
            } else {
                nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult typeResult =
                        nl.akiar.pascal.stubs.PascalTypeIndex.findTypesWithUsesValidation(qualifierName, qualifier.getContainingFile(), qualifier.getTextOffset());
                if (!typeResult.getInScopeTypes().isEmpty()) {
                    resolvedQualifier = typeResult.getInScopeTypes().get(0);
                    System.out.println("[DEBUG_LOG] [PascalNav] Qualifier resolved manually to type: " + resolvedQualifier);
                }
            }
        }

        if (resolvedQualifier == null) {
            LOG.info("[PascalNav] Could not resolve qualifier: " + qualifier.getText());
            return null;
        }

        // 3. Find the type of the resolved qualifier
        PascalTypeDefinition typeDef = null;
        if (resolvedQualifier instanceof PascalVariableDefinition) {
            String typeName = ((PascalVariableDefinition) resolvedQualifier).getTypeName();
            if (typeName != null) {
                // Look up the type definition
                typeDef = findTypeDefinition(typeName, resolvedQualifier);
            }
        } else if (resolvedQualifier instanceof PascalTypeDefinition) {
            // Static member access or class reference
            typeDef = (PascalTypeDefinition) resolvedQualifier;
        }

        if (typeDef == null) {
            LOG.info("[PascalNav] Could not determine type for qualifier: " + resolvedQualifier.getText());
            return null;
        }

        LOG.info("[PascalNav] Searching in type: " + typeDef.getName());

        // 4. Search for the member in the type and its ancestors
        return findMemberInType(typeDef, memberName, true);
    }

    @Nullable
    private PascalTypeDefinition findTypeDefinition(String typeName, PsiElement context) {
        // Use existing type index
        nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult result =
                nl.akiar.pascal.stubs.PascalTypeIndex.findTypesWithUsesValidation(typeName, context.getContainingFile(), context.getTextOffset());
        
        if (!result.getInScopeTypes().isEmpty()) {
            return result.getInScopeTypes().get(0);
        }
        return null;
    }

    @Nullable
    private PsiElement findMemberInType(PascalTypeDefinition typeDef, String name, boolean includeAncestors) {
        List<PsiElement> members = typeDef.getMembers(includeAncestors);
        for (PsiElement member : members) {
            if (member instanceof PsiNameIdentifierOwner) {
                String memberName = ((PsiNameIdentifierOwner) member).getName();
                if (name.equalsIgnoreCase(memberName)) {
                    // Check visibility
                    if (isAccessible(member)) {
                        return member;
                    }
                }
            }
        }
        return null;
    }

    private boolean isAccessible(PsiElement member) {
        String visibility = null;
        if (member instanceof PascalRoutine) {
            visibility = ((PascalRoutine) member).getVisibility();
        } else if (member instanceof PascalProperty) {
            visibility = ((PascalProperty) member).getVisibility();
        } else if (member instanceof PascalVariableDefinition) {
            visibility = ((PascalVariableDefinition) member).getVisibility();
        }

        if (visibility == null || visibility.equalsIgnoreCase("public") || visibility.equalsIgnoreCase("published")) {
            return true;
        }

        // For private/protected, we need more context (caller's class and unit)
        // Basic check for now: if in the same unit, it's accessible
        if (member.getContainingFile().equals(myElement.getContainingFile())) {
            return true;
        }

        if (visibility.equalsIgnoreCase("protected")) {
            // Check if caller is in a class that is a descendant of member's class
            PascalTypeDefinition memberClass = null;
            if (member instanceof PascalRoutine) {
                memberClass = ((PascalRoutine) member).getContainingClass();
            } else if (member instanceof PascalProperty) {
                memberClass = ((PascalProperty) member).getContainingClass();
            } else if (member instanceof PascalVariableDefinition) {
                memberClass = ((PascalVariableDefinition) member).getContainingClass();
            }

            if (memberClass == null) return true;

            // Find caller's class
            PascalTypeDefinition callerClass = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myElement, PascalTypeDefinition.class);
            if (callerClass != null) {
                if (isDescendantOf(callerClass, memberClass)) {
                    return true;
                }
            }
            
            // Also check if caller is in an implementation of a method of a descendant class
            PascalRoutine callerRoutine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myElement, PascalRoutine.class);
            if (callerRoutine != null) {
                PascalTypeDefinition routineClass = callerRoutine.getContainingClass();
                if (routineClass != null && isDescendantOf(routineClass, memberClass)) {
                    return true;
                }
            }

            return false;
        }

        return false;
    }

    private boolean isDescendantOf(PascalTypeDefinition subClass, PascalTypeDefinition superClass) {
        if (subClass == null || superClass == null) return false;
        if (subClass.equals(superClass)) return true;
        
        PascalTypeDefinition current = subClass.getSuperClass();
        while (current != null) {
            if (current.equals(superClass)) return true;
            current = current.getSuperClass();
        }
        return false;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        // Code completion would go here
        return new Object[0];
    }
}
