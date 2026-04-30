package nl.akiar.pascal;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * Base PSI element for Pascal language.
 * Overrides getReferences() to include references from PsiReferenceContributor,
 * which is required for Find Usages and Rename to discover callsites.
 */
public class PascalPsiElement extends ASTWrapperPsiElement {
    private static final Logger LOG = Logger.getInstance(PascalPsiElement.class);

    public PascalPsiElement(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public PsiReference[] getReferences() {
        return com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
                .getReferencesFromProviders(this);
    }
}
