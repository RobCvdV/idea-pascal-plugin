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
        addPreferringFullDefinitions(results, result.getInScopeTypes());
        if (results.isEmpty()) {
            addPreferringFullDefinitions(results, result.getOutOfScopeTypes());
        }
        return results.toArray(new ResolveResult[0]);
    }

    /**
     * Add types to results, preferring full definitions over forward declarations.
     * If any full definitions exist, only full definitions are added; otherwise all
     * (forward) definitions are added so resolve still finds something.
     */
    private static void addPreferringFullDefinitions(List<ResolveResult> results,
                                                     List<PascalTypeDefinition> candidates) {
        List<PascalTypeDefinition> fullDefs = new ArrayList<>();
        for (PascalTypeDefinition type : candidates) {
            if (!type.isForwardDeclaration()) fullDefs.add(type);
        }
        List<PascalTypeDefinition> picked = fullDefs.isEmpty() ? candidates : fullDefs;
        for (PascalTypeDefinition type : picked) {
            results.add(new PsiElementResolveResult(type));
        }
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

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        PsiElement resolved = resolve();
        return PascalReferenceUtil.isEquivalentTarget(resolved, element);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) {
        return nl.akiar.pascal.psi.PascalPsiFactory.INSTANCE.replaceIdentifier(myElement, newElementName);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
