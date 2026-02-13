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
import java.util.List;

/**
 * Reference for Pascal identifiers that can be either types or variables/constants.
 * Routine calls are handled by {@link PascalRoutineCallReference}.
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
        // Built-in functions and types don't resolve to any specific element
        if (DelphiBuiltIns.isBuiltInFunction(name)) {
            return new ResolveResult[0];
        }

        // 1. Variables (local > field > global) — early return if found
        PascalVariableDefinition var = PascalVariableIndex.findVariableAtPosition(
            name, myElement.getContainingFile(), myElement.getTextOffset());
        if (var != null) {
            return new ResolveResult[]{new PsiElementResolveResult(var)};
        }

        // 2. Types (with uses-clause validation)
        List<ResolveResult> results = new ArrayList<>();
        if (!DelphiBuiltIns.isBuiltInType(name)) {
            PascalTypeIndex.TypeLookupResult typeResult =
                    PascalTypeIndex.findTypesWithUsesValidation(name, myElement.getContainingFile(), myElement.getTextOffset());

            for (PascalTypeDefinition type : typeResult.getInScopeTypes()) {
                results.add(new PsiElementResolveResult(type));
            }
            if (results.isEmpty()) {
                for (PascalTypeDefinition type : typeResult.getOutOfScopeTypes()) {
                    results.add(new PsiElementResolveResult(type));
                }
            }
        }

        // 3. Routines (with uses-clause validation) — needed for Pascal procedure calls without parens
        if (results.isEmpty()) {
            nl.akiar.pascal.stubs.PascalRoutineIndex.RoutineLookupResult routineResult =
                    nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutinesWithUsesValidation(
                        name, myElement.getContainingFile(), myElement.getTextOffset());
            for (nl.akiar.pascal.psi.PascalRoutine routine : routineResult.getInScopeRoutines()) {
                results.add(new PsiElementResolveResult(routine));
            }
            if (results.isEmpty()) {
                for (nl.akiar.pascal.psi.PascalRoutine routine : routineResult.getOutOfScopeRoutines()) {
                    results.add(new PsiElementResolveResult(routine));
                }
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
