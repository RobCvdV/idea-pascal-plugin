package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.stubs.PascalVariableIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reference for Pascal identifiers that can be either types or variables/constants.
 */
public class PascalIdentifierReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    private static final Logger LOG = Logger.getInstance(PascalIdentifierReference.class);
    private final String name;

    public PascalIdentifierReference(@NotNull PsiElement element, TextRange textRange) {
        super(element, textRange);
        name = element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        LOG.info("[PascalNav] Identifier resolution for: " + name);
        List<ResolveResult> results = new ArrayList<>();

        // 1. Try to resolve as a variable (includes local, fields, globals)
        PascalVariableDefinition var = PascalVariableIndex.findVariableAtPosition(name, myElement.getContainingFile(), myElement.getTextOffset());
        if (var != null) {
            LOG.info("[PascalNav]  -> Resolved to variable: " + var.getName());
            results.add(new PsiElementResolveResult(var));
        }

        // 2. Try to resolve as a type
        nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult typeResult =
                PascalTypeIndex.findTypesWithUsesValidation(name, myElement.getContainingFile(), myElement.getTextOffset());
        
        for (PascalTypeDefinition type : typeResult.getInScopeTypes()) {
            LOG.info("[PascalNav]  -> Resolved to in-scope type: " + type.getName());
            results.add(new PsiElementResolveResult(type));
        }
        
        // 3. Try to resolve as a routine
        Collection<nl.akiar.pascal.psi.PascalRoutine> routines = nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutines(name, myElement.getProject());
        for (nl.akiar.pascal.psi.PascalRoutine routine : routines) {
            // Filter to only include routines in scope (same unit or in uses clause)
            // For now, simple implementation
            LOG.info("[PascalNav]  -> Resolved to routine: " + routine.getName());
            results.add(new PsiElementResolveResult(routine));
        }

        // If no in-scope matches, maybe add out-of-scope matches?
        // Usually it's better to keep it restricted or show them with lower priority.
        if (results.isEmpty()) {
            for (PascalTypeDefinition type : typeResult.getOutOfScopeTypes()) {
                LOG.info("[PascalNav]  -> Resolved to out-of-scope type: " + type.getName());
                results.add(new PsiElementResolveResult(type));
            }
        }

        return results.toArray(new ResolveResult[0]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length > 0 ? resolveResults[0].getElement() : null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
