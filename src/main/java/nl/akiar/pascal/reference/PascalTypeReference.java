package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reference to a Pascal type definition.
 */
public class PascalTypeReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    private static final Logger LOG = Logger.getInstance(PascalTypeReference.class);
    private final String name;

    public PascalTypeReference(@NotNull PsiElement element, TextRange textRange) {
        super(element, textRange);
        name = element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        LOG.info("[PascalNav] Resolving name: " + name + " for element: " + myElement.getText());
        Collection<PascalTypeDefinition> types = PascalTypeIndex.findTypes(name, myElement.getProject());
        LOG.info("[PascalNav] Found " + types.size() + " types for name: " + name);
        List<ResolveResult> results = new ArrayList<>();
        for (PascalTypeDefinition type : types) {
            LOG.info("[PascalNav]  -> Match: " + type.getName() + " in " + type.getContainingFile().getName());
            results.add(new PsiElementResolveResult(type));
        }
        return results.toArray(new ResolveResult[0]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        LOG.info("[PascalNav] resolve() called for " + name + " (element: " + myElement + ")");
        ResolveResult[] resolveResults = multiResolve(false);
        LOG.info("[PascalNav] Final resolve for " + name + " returned " + resolveResults.length + " results");
        if (resolveResults.length > 0) {
            PsiElement element = resolveResults[0].getElement();
            LOG.info("[PascalNav]  -> resolved to: " + element);
            return element;
        }
        return null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        // For now, we don't implement completion variants here
        return new Object[0];
    }
}
