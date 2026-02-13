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

                if (type == PascalTokenTypes.IDENTIFIER || type == PascalElementTypes.METHOD_NAME_REFERENCE) {
                    PsiElement parent = element.getParent();

                    // Routine implementation navigation — only for the routine's own name identifier
                    PsiElement routineParent = null;
                    if (parent instanceof nl.akiar.pascal.psi.PascalRoutine) {
                        routineParent = parent;
                    } else if (parent != null && parent.getNode() != null
                            && parent.getNode().getElementType() == PascalElementTypes.METHOD_NAME_REFERENCE) {
                        PsiElement grandParent = parent.getParent();
                        if (grandParent instanceof nl.akiar.pascal.psi.PascalRoutine) {
                            routineParent = grandParent;
                        }
                    }
                    if (routineParent instanceof nl.akiar.pascal.psi.PascalRoutine rp) {
                        PsiElement nameId = rp.getNameIdentifier();
                        if (nameId == element || (nameId != null && PsiTreeUtil.isAncestor(nameId, element, false))) {
                            if (rp.isImplementation() || rp.getImplementation() != null) {
                                return new PsiReference[]{new PascalRoutineImplementationReference(element, rp)};
                            }
                            return PsiReference.EMPTY_ARRAY;
                        }
                    }

                    // For METHOD_NAME_REFERENCE, proceed to other reference types after routine check
                    if (type != PascalTokenTypes.IDENTIFIER) {
                        // METHOD_NAME_REFERENCE nodes that weren't handled as routine implementations
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

                    // Property specifier identifiers (after read/write/stored/default keywords)
                    if (nl.akiar.pascal.psi.PsiUtil.hasParent(element, PascalElementTypes.PROPERTY_DEFINITION)) {
                        PsiElement prev = nl.akiar.pascal.psi.PsiUtil.getPrevNoneIgnorableSibling(element);
                        if (prev != null) {
                            IElementType prevType = prev.getNode().getElementType();
                            if (prevType == PascalTokenTypes.KW_READ ||
                                prevType == PascalTokenTypes.KW_WRITE ||
                                prevType == PascalTokenTypes.KW_STORED ||
                                prevType == PascalTokenTypes.KW_DEFAULT) {
                                return new PsiReference[]{new PascalPropertySpecifierReference(element)};
                            }
                        }
                    }

                    // Check if parent is TYPE_REFERENCE
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

                    // Check if this identifier is a routine call (followed by LPAREN)
                    PsiElement nextSibling = nl.akiar.pascal.psi.PsiUtil.getNextNoneIgnorableSibling(element);
                    if (nextSibling != null && nextSibling.getNode() != null
                            && nextSibling.getNode().getElementType() == PascalTokenTypes.LPAREN) {
                        return new PsiReference[]{new PascalRoutineCallReference(element)};
                    }

                    return new PsiReference[]{new PascalIdentifierReference(element, new TextRange(0, text.length()))};
                }

                return PsiReference.EMPTY_ARRAY;
            }
        };

        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(),
                provider);
        LOG.debug("[PascalNav] Registration complete");
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
