package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import com.intellij.openapi.diagnostic.Logger;
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
        LOG.debug("[MemberTraversal] PascalMemberReference.resolve element='" + myElement.getText() + "' file='" + myElement.getContainingFile().getName() + "'");
        // Use unified chain resolver so this reference benefits from full member-chain context
        PsiElement resolved = nl.akiar.pascal.resolution.MemberChainResolver.resolveElement(myElement);
        LOG.debug("[MemberTraversal] PascalMemberReference.chain resolved -> " + (resolved != null ? resolved.getClass().getSimpleName() : "<unresolved>"));
        if (resolved != null) {
            // Enforce accessibility rules for routines/properties/fields
            if (resolved instanceof nl.akiar.pascal.psi.PascalRoutine ||
                resolved instanceof nl.akiar.pascal.psi.PascalProperty ||
                resolved instanceof nl.akiar.pascal.psi.PascalVariableDefinition) {
                if (!isAccessible(resolved)) {
                    return null;
                }
            }
            return resolved;
        }
        // Fallback to previous logic if chain resolver couldn't resolve; keep minimal guard
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

        // 2. Resolve the qualifier to find its type
        PsiElement resolvedQualifier = null;
        PsiReference[] refs = qualifier.getReferences();
        for (PsiReference ref : refs) {
            resolvedQualifier = ref.resolve();
            if (resolvedQualifier != null) {
                break;
            }
        }

        if (resolvedQualifier == null) {
            String qualifierName = qualifier.getText();
            resolvedQualifier = nl.akiar.pascal.stubs.PascalVariableIndex.findVariableAtPosition(qualifierName, qualifier.getContainingFile(), qualifier.getTextOffset());
            if (resolvedQualifier == null) {
                nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult typeResult =
                        nl.akiar.pascal.stubs.PascalTypeIndex.findTypesWithUsesValidation(qualifierName, qualifier.getContainingFile(), qualifier.getTextOffset());
                if (!typeResult.getInScopeTypes().isEmpty()) {
                    resolvedQualifier = typeResult.getInScopeTypes().get(0);
                }
            }
            if (resolvedQualifier == null) {
                nl.akiar.pascal.stubs.PascalRoutineIndex.RoutineLookupResult routineResult =
                        nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutinesWithUsesValidation(qualifierName, qualifier.getContainingFile(), qualifier.getTextOffset());
                if (!routineResult.getInScopeRoutines().isEmpty()) {
                    resolvedQualifier = routineResult.getInScopeRoutines().get(0);
                }
            }
        }

        if (resolvedQualifier == null) {
            LOG.debug("[MemberTraversal] qualifier unresolved for '" + myElement.getText() + "'");
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
        } else if (resolvedQualifier instanceof PascalProperty) {
            // Property access - get the property's type
            String typeName = ((PascalProperty) resolvedQualifier).getTypeName();
            if (typeName != null) {
                typeDef = findTypeDefinition(typeName, resolvedQualifier);
            }
        } else if (resolvedQualifier instanceof PascalRoutine) {
            // Function call - use return type for member lookup
            String returnTypeName = ((PascalRoutine) resolvedQualifier).getReturnTypeName();
            if (returnTypeName != null) {
                typeDef = findTypeDefinition(returnTypeName, resolvedQualifier);
            }
        } else if (resolvedQualifier instanceof PascalTypeDefinition) {
            // Static member access or class reference
            typeDef = (PascalTypeDefinition) resolvedQualifier;
        }

        if (typeDef == null) {
            LOG.debug("[MemberTraversal] qualifier type unresolved for '" + myElement.getText() + "'");
            return null;
        }
        PsiElement member = findMemberInType(typeDef, memberName, true);
        LOG.debug("[MemberTraversal] fallback member search result -> " + (member != null ? member.getClass().getSimpleName() : "<unresolved>"));
        return member;
    }

    @Nullable
    private PascalTypeDefinition findTypeDefinition(String typeName, PsiElement context) {
        // IMPORTANT: Use the ORIGINAL file where the code is written, not the intermediate type's file.
        // This ensures transitive dependencies are resolved correctly.
        // For example, when resolving "meResult.Lines.Add":
        // - meResult is resolved in the original file
        // - Lines is a property of type TStrings (from System.Classes)
        // - Add is a method of TStrings
        // We need to use the original file's uses clause for all lookups to ensure TStrings is found.
        PsiFile originFile = myElement.getContainingFile();

        nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult result =
                nl.akiar.pascal.stubs.PascalTypeIndex.findTypeWithTransitiveDeps(typeName, originFile, myElement.getTextOffset());

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
            
            // Also check if caller is in an implementation of a method of a descendant class.
            // Walk up through anonymous routines to find the owning named method.
            PascalRoutine callerRoutine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myElement, PascalRoutine.class);
            while (callerRoutine != null) {
                PascalTypeDefinition routineClass = callerRoutine.getContainingClass();
                if (routineClass != null) {
                    if (isDescendantOf(routineClass, memberClass)) {
                        return true;
                    }
                    break;
                }
                callerRoutine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(callerRoutine, PascalRoutine.class);
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
