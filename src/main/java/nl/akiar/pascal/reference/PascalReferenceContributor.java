package nl.akiar.pascal.reference;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.psi.tree.IElementType;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.PascalPsiElement;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;

public class PascalReferenceContributor extends PsiReferenceContributor {
    private static final Logger LOG = Logger.getInstance(PascalReferenceContributor.class);

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        LOG.info("[PascalNav] Registering reference provider for Pascal IDENTIFIER tokens");
        LOG.info("[PascalNav] IDENTIFIER type instance: " + PascalTokenTypes.IDENTIFIER + " hash: " + System.identityHashCode(PascalTokenTypes.IDENTIFIER));
        LOG.info("[PascalNav] Language instance: " + PascalLanguage.INSTANCE + " hash: " + System.identityHashCode(PascalLanguage.INSTANCE));

        PsiReferenceProvider provider = new PsiReferenceProvider() {
            @NotNull
            @Override
            public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                         @NotNull ProcessingContext context) {
                String text = element.getText();
                ASTNode node = element.getNode();
                IElementType type = node != null ? node.getElementType() : null;

                LOG.info("[PascalNav] getReferencesByElement called for: '" + text + "' type: " + type +
                         " typeHash: " + (type != null ? System.identityHashCode(type) : "null") +
                         " expectedHash: " + System.identityHashCode(PascalTokenTypes.IDENTIFIER) +
                         " match: " + (type == PascalTokenTypes.IDENTIFIER));

                if (type == PascalElementTypes.UNIT_REFERENCE) {
                    return new PsiReference[]{new PascalUnitReference(element)};
                }

                if (type == PascalTokenTypes.IDENTIFIER) {
                    // Check if inside a unit reference
                    PsiElement parent = element.getParent();
                    if (parent != null && parent.getNode() != null && parent.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
                        return new PsiReference[]{new PascalUnitReference(element)};
                    }
                }

                if (type != PascalTokenTypes.IDENTIFIER) {
                    return PsiReference.EMPTY_ARRAY;
                }

                // Skip if it's a definition
                PsiElement parent = element.getParent();
                if (parent instanceof PascalTypeDefinition) {
                    if (((PascalTypeDefinition) parent).getNameIdentifier() == element) {
                        LOG.info("[PascalNav]  -> Skipping definition name: " + text);
                        return PsiReference.EMPTY_ARRAY;
                    }
                }
                PascalTypeReference ref = new PascalTypeReference(element, new TextRange(0, element.getTextLength()));
                LOG.info("[PascalNav]  -> Created reference for: " + text);
                return new PsiReference[]{ref};
            }
        };

        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PascalPsiElement.class)
                        .withLanguage(PascalLanguage.INSTANCE),
                provider);
        LOG.info("[PascalNav] Registration complete (broad pattern for debugging)");
    }
}
