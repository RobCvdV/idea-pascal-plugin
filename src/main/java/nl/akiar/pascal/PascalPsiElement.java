package nl.akiar.pascal;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * Base PSI element for Pascal language
 */
public class PascalPsiElement extends ASTWrapperPsiElement {
    private static final Logger LOG = Logger.getInstance(PascalPsiElement.class);

    public PascalPsiElement(@NotNull ASTNode node) {
        super(node);
        LOG.info("[PascalPSI] Created PascalPsiElement for: " + node.getElementType());
    }

    @Override
    public PsiReference getReference() {
        PsiReference[] refs = getReferences();
        if (refs.length > 0) {
            return refs[0];
        }
        return super.getReference();
    }
}
