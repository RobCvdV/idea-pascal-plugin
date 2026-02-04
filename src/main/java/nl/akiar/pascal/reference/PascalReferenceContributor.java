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
                
                if (type == PascalTokenTypes.IDENTIFIER) {
                    // Skip definition names
                    PsiElement parent = element.getParent();
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
                    if (parent instanceof nl.akiar.pascal.psi.PascalRoutine) {
                        if (((nl.akiar.pascal.psi.PascalRoutine) parent).getNameIdentifier() == element) {
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
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement().withElementType(PascalElementTypes.UNIT_REFERENCE),
                provider);
        LOG.info("[PascalNav] Registration complete (broad pattern for debugging)");
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
