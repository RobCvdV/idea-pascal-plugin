package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
        PascalTypeIndex.TypeLookupResult result = PascalTypeIndex.findTypesWithUsesValidation(
            name, myElement.getContainingFile(), myElement.getTextOffset());
        List<ResolveResult> results = new ArrayList<>();
        for (PascalTypeDefinition type : result.getInScopeTypes()) {
            results.add(new PsiElementResolveResult(type));
        }
        if (results.isEmpty()) {
            for (PascalTypeDefinition type : result.getOutOfScopeTypes()) {
                results.add(new PsiElementResolveResult(type));
            }
        }
        return results.toArray(new ResolveResult[0]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        if (resolveResults.length > 0) {
            return resolveResults[0].getElement();
        }
        return null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
