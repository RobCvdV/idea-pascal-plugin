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
                    // Skip if it's a definition
                    PsiElement parent = element.getParent();
                    if (parent instanceof PascalTypeDefinition) {
                        if (((PascalTypeDefinition) parent).getNameIdentifier() == element) {
                            LOG.info("[PascalNav]  -> Skipping definition name: " + text);
                            return PsiReference.EMPTY_ARRAY;
                        }
                    }
                    if (parent instanceof nl.akiar.pascal.psi.PascalVariableDefinition) {
                        if (((nl.akiar.pascal.psi.PascalVariableDefinition) parent).getNameIdentifier() == element) {
                            LOG.info("[PascalNav]  -> Skipping variable definition name: " + text);
                            return PsiReference.EMPTY_ARRAY;
                        }
                    }
                    if (parent instanceof nl.akiar.pascal.psi.PascalRoutine) {
                        if (((nl.akiar.pascal.psi.PascalRoutine) parent).getNameIdentifier() == element) {
                            LOG.info("[PascalNav]  -> Skipping routine definition name: " + text);
                            return PsiReference.EMPTY_ARRAY;
                        }
                    }

                    // Check if inside a unit reference
                    if (parent != null && parent.getNode() != null && parent.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
                        return new PsiReference[]{new PascalUnitReference(element)};
                    }

                    // Check for member access (Obj.Member)
                    PsiElement prev = PsiTreeUtil.prevLeaf(element);
                    while (prev != null && (prev instanceof PsiWhiteSpace || prev instanceof com.intellij.psi.PsiComment)) {
                        prev = PsiTreeUtil.prevLeaf(prev);
                    }

                    if (prev != null) {
                        System.out.println("[DEBUG_LOG] [PascalNav] Prev leaf for '" + text + "' is: '" + prev.getText() + "' type: " + prev.getNode().getElementType());
                    }

                    if (prev != null && prev.getNode().getElementType() == PascalTokenTypes.DOT) {
                        System.out.println("[DEBUG_LOG] [PascalNav] Creating member reference for: " + text);
                        return new PsiReference[]{new PascalMemberReference(element, new TextRange(0, text.length()))};
                    }
                    
                    System.out.println("[DEBUG_LOG] [PascalNav] Creating identifier reference for: " + text);
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
}
