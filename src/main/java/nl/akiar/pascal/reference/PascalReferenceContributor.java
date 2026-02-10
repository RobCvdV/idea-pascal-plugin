package nl.akiar.pascal.reference;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.PascalPsiElement;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;

public class PascalReferenceContributor extends PsiReferenceContributor {
    private static final Logger LOG = Logger.getInstance(PascalReferenceContributor.class);
    
    static {
        System.out.println("[DEBUG_LOG] [PascalNav] PascalReferenceContributor class loaded");
    }

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        PsiReferenceProvider provider = new PsiReferenceProvider() {
            @NotNull
            @Override
            public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                         @NotNull ProcessingContext context) {
                String text = element.getText();
                ASTNode node = element.getNode();
                IElementType type = node != null ? node.getElementType() : null;
                
                System.out.println("[DEBUG_LOG] [PascalRefProvider] Called for element: '" + text + "' type: " + type + " language: " + (element.getLanguage() != null ? element.getLanguage().getID() : "null"));
                
                if (type == PascalTokenTypes.IDENTIFIER || type == nl.akiar.pascal.psi.PascalElementTypes.METHOD_NAME_REFERENCE) {
                    PsiElement parent = element.getParent();

                    System.out.println("[DEBUG_LOG] [PascalNav] ID=" + text + " type=" + type + " parent=" + (parent != null ? parent.getClass().getSimpleName() : "null") + " parentType=" + (parent != null && parent.getNode() != null ? parent.getNode().getElementType() : "null"));
                    
                    // Routine implementation navigation (e.g. procedure TMyClass.MyMethod)
                    // The identifier could be a direct child of ROUTINE_DECLARATION
                    // or nested under METHOD_NAME_REFERENCE.
                    nl.akiar.pascal.psi.PascalRoutine routine = PsiTreeUtil.getParentOfType(element, nl.akiar.pascal.psi.PascalRoutine.class);
                    System.out.println("[DEBUG_LOG] [PascalNav] Found routine: " + (routine != null ? routine.getClass().getSimpleName() : "null"));
                    if (routine != null) {
                        System.out.println("[DEBUG_LOG] [PascalNav] routine.isImplementation()=" + routine.isImplementation() + " routine.getImplementation()=" + (routine.getImplementation() != null ? "found" : "null"));
                    }
                    if (routine != null && (routine.isImplementation() || routine.getImplementation() != null)) {
                        PsiElement nameId = routine.getNameIdentifier();
                        System.out.println("[DEBUG_LOG] [PascalNav] nameId=" + (nameId != null ? nameId.getText() : "null") + " element=" + element.getText());
                        System.out.println("[DEBUG_LOG] [PascalNav] nameId == element: " + (nameId == element));
                        if (nameId != null) {
                            System.out.println("[DEBUG_LOG] [PascalNav] PsiTreeUtil.isAncestor(nameId, element, false): " + PsiTreeUtil.isAncestor(nameId, element, false));
                        }
                        if (nameId == element || (nameId != null && PsiTreeUtil.isAncestor(nameId, element, false))) {
                            System.out.println("[DEBUG_LOG] [PascalNav] Creating reference for routine (impl=" + routine.isImplementation() + "): " + text);
                            return new PsiReference[]{new PascalRoutineImplementationReference(element, routine)};
                        }
                    }

                    // For METHOD_NAME_REFERENCE, proceed to other reference types after routine check
                    if (type != PascalTokenTypes.IDENTIFIER) {
                        // METHOD_NAME_REFERENCE nodes that weren't handled as routine implementations
                        // can proceed to other reference logic (like member access, etc.)
                        // but skip type/variable definition checks which only apply to IDENTIFIER tokens
                        if (parent != null && parent.getNode() != null && parent.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
                            return new PsiReference[]{new PascalUnitReference(element)};
                        }
                        if (nl.akiar.pascal.psi.PsiUtil.hasParent(element, PascalElementTypes.UNIT_DECL_SECTION) ||
                            nl.akiar.pascal.psi.PsiUtil.hasParent(element, PascalElementTypes.USES_SECTION)) {
                            return new PsiReference[]{new PascalUnitReference(element)};
                        }
                        // Member access for METHOD_NAME_REFERENCE
                        PsiElement prev = PsiTreeUtil.prevLeaf(element);
                        while (prev != null && (prev instanceof PsiWhiteSpace || prev instanceof com.intellij.psi.PsiComment)) {
                            prev = PsiTreeUtil.prevLeaf(prev);
                        }
                        if (prev != null && prev.getNode().getElementType() == PascalTokenTypes.DOT) {
                            return new PsiReference[]{new PascalMemberReference(element, new TextRange(0, text.length()))};
                        }
                        return PsiReference.EMPTY_ARRAY;
                    }

                    // NEW: Check if parent is TYPE_REFERENCE
                    if (parent instanceof nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) {
                        nl.akiar.pascal.psi.impl.PascalTypeReferenceElement typeRef =
                            (nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) parent;

                        // For SIMPLE_TYPE, skip resolution (always valid, no navigation needed)
                        if (typeRef.getKind() == nl.akiar.pascal.psi.TypeReferenceKind.SIMPLE_TYPE) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // For USER_TYPE, create specific type reference
                        return new PsiReference[]{new PascalTypeReference(element, new TextRange(0, text.length()))};
                    }

                    // Skip definition names
                    if (parent instanceof PascalTypeDefinition) {
                        if (((PascalTypeDefinition) parent).getNameIdentifier() == element) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                    }
                    if (parent instanceof nl.akiar.pascal.psi.PascalVariableDefinition) {
                        if (((nl.akiar.pascal.psi.PascalVariableDefinition) parent).getNameIdentifier() == element) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                    }
                    if (parent instanceof nl.akiar.pascal.psi.PascalRoutine routineParent) {
                        LOG.info("[PascalNav] ID=" + text + " parent is Routine, nameID=" + (routineParent.getNameIdentifier() == element));
                        if (routineParent.getNameIdentifier() == element) {
                            if (routineParent.isImplementation()) {
                                LOG.info("[PascalNav] Creating reference for implementation (via parent): " + text);
                                return new PsiReference[]{new PascalRoutineImplementationReference(element, routineParent)};
                            }
                            return PsiReference.EMPTY_ARRAY;
                        }
                    }

                    // If inside an attribute, treat as attribute reference
                    PascalAttribute attribute = findContainingAttribute(element);
                    if (attribute != null && !attribute.isGUID()) {
                        return new PsiReference[]{new PascalAttributeReference(element, attribute)};
                    }

                    // If inside a UNIT_REFERENCE (header or uses), treat as unit reference
                    if (parent != null && parent.getNode() != null && parent.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
                        return new PsiReference[]{new PascalUnitReference(element)};
                    }

                    // If inside unit declaration or uses section, any dotted identifiers should be unit references
                    if (nl.akiar.pascal.psi.PsiUtil.hasParent(element, PascalElementTypes.UNIT_DECL_SECTION) ||
                        nl.akiar.pascal.psi.PsiUtil.hasParent(element, PascalElementTypes.USES_SECTION)) {
                        return new PsiReference[]{new PascalUnitReference(element)};
                    }

                    // Member access (Obj.Member) outside unit/uses
                    PsiElement prev = PsiTreeUtil.prevLeaf(element);
                    while (prev != null && (prev instanceof PsiWhiteSpace || prev instanceof com.intellij.psi.PsiComment)) {
                        prev = PsiTreeUtil.prevLeaf(prev);
                    }
                    if (prev != null && prev.getNode().getElementType() == PascalTokenTypes.DOT) {
                        return new PsiReference[]{new PascalMemberReference(element, new TextRange(0, text.length()))};
                    }
                    return new PsiReference[]{new PascalIdentifierReference(element, new TextRange(0, text.length()))};
                }

                return PsiReference.EMPTY_ARRAY;
            }
        };

        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(),
                provider);
        LOG.info("[PascalNav] Registration complete (broad pattern for debugging)");
        System.out.println("[DEBUG_LOG] [PascalNav] Reference contributor registration complete");
    }

    /**
     * Find the PascalAttribute containing the given element, if any.
     */
    private static PascalAttribute findContainingAttribute(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PascalAttribute) {
                return (PascalAttribute) current;
            }
            // Stop at type/routine boundaries
            if (current instanceof PascalTypeDefinition ||
                current instanceof nl.akiar.pascal.psi.PascalRoutine ||
                current instanceof nl.akiar.pascal.psi.PascalProperty ||
                current instanceof nl.akiar.pascal.psi.PascalVariableDefinition) {
                break;
            }
            current = current.getParent();
        }
        return null;
    }
}
