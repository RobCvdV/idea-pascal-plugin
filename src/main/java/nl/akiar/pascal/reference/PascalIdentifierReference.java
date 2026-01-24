package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.resolution.DelphiBuiltIns;
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

        // 0. Built-in functions and types don't resolve to any specific element
        // They are compiler intrinsics - return empty to prevent wrong resolution
        if (DelphiBuiltIns.isBuiltInFunction(name)) {
            LOG.info("[PascalNav]  -> Built-in function (no target): " + name);
            return new ResolveResult[0]; // No specific target for built-ins
        }

        // 1. Try to resolve as a variable (includes local, fields, globals)
        PascalVariableDefinition var = PascalVariableIndex.findVariableAtPosition(name, myElement.getContainingFile(), myElement.getTextOffset());
        if (var != null) {
            LOG.info("[PascalNav]  -> Resolved to variable: " + var.getName());
            results.add(new PsiElementResolveResult(var));
        }

        // 2. Try to resolve as a type (skip for built-in types - they have no PSI element)
        if (!DelphiBuiltIns.isBuiltInType(name)) {
            nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult typeResult =
                    PascalTypeIndex.findTypesWithUsesValidation(name, myElement.getContainingFile(), myElement.getTextOffset());

            for (PascalTypeDefinition type : typeResult.getInScopeTypes()) {
                LOG.info("[PascalNav]  -> Resolved to in-scope type: " + type.getName());
                results.add(new PsiElementResolveResult(type));
            }
        }

        // 3. Try to resolve as a routine
        nl.akiar.pascal.stubs.PascalRoutineIndex.RoutineLookupResult routineResult =
                nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutinesWithUsesValidation(name, myElement.getContainingFile(), myElement.getTextOffset());

        for (nl.akiar.pascal.psi.PascalRoutine routine : routineResult.getInScopeRoutines()) {
            LOG.info("[PascalNav]  -> Resolved to in-scope routine: " + routine.getName());
            results.add(new PsiElementResolveResult(routine));
        }

        // If no in-scope matches, maybe add out-of-scope matches?
        // Usually it's better to keep it restricted or show them with lower priority.
        if (results.isEmpty() && !DelphiBuiltIns.isBuiltInType(name)) {
            nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult typeResult =
                    PascalTypeIndex.findTypesWithUsesValidation(name, myElement.getContainingFile(), myElement.getTextOffset());
            for (PascalTypeDefinition type : typeResult.getOutOfScopeTypes()) {
                LOG.info("[PascalNav]  -> Resolved to out-of-scope type: " + type.getName());
                results.add(new PsiElementResolveResult(type));
            }
            for (nl.akiar.pascal.psi.PascalRoutine routine : routineResult.getOutOfScopeRoutines()) {
                LOG.info("[PascalNav]  -> Resolved to out-of-scope routine: " + routine.getName());
                results.add(new PsiElementResolveResult(routine));
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
