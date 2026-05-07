package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.stubs.PascalVariableIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

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
        long t0 = System.nanoTime();
        long tVar = t0, tType = t0, tRoutine = t0, tEnum = t0, tBeforeReturn = 0L;
        try {
            PsiFile file = myElement.getContainingFile();
            int offset = myElement.getTextOffset();
            String nameLc = name.toLowerCase();

            // 1. Variables (local > field > global) — early return if found
            PascalVariableDefinition var = PascalVariableIndex.findVariableAtPosition(name, file, offset);
            tVar = System.nanoTime();
            if (var != null) {
                return new ResolveResult[]{new PsiElementResolveResult(var)};
            }

            // 2. Types (with uses-clause validation, in-scope only)
            List<ResolveResult> results = new ArrayList<>();
            PascalTypeIndex.TypeLookupResult typeResult =
                    PascalTypeIndex.findTypesWithUsesValidation(name, file, offset);
            tType = System.nanoTime();

            List<PascalTypeDefinition> inScopeTypes = typeResult.getInScopeTypes();
            // Filter out forward declarations, preferring full definitions
            List<PascalTypeDefinition> fullDefs = new ArrayList<>();
            for (PascalTypeDefinition type : inScopeTypes) {
                if (!type.isForwardDeclaration()) fullDefs.add(type);
            }
            for (PascalTypeDefinition type : (fullDefs.isEmpty() ? inScopeTypes : fullDefs)) {
                results.add(new PsiElementResolveResult(type));
            }

            // 3. Routines (with uses-clause validation, in-scope only) — needed for Pascal procedure calls without parens
            if (results.isEmpty()) {
                nl.akiar.pascal.stubs.PascalRoutineIndex.RoutineLookupResult routineResult =
                        nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutinesWithUsesValidation(name, file, offset);
                for (nl.akiar.pascal.psi.PascalRoutine routine : routineResult.getInScopeRoutines()) {
                    results.add(new PsiElementResolveResult(routine));
                }
            }
            tRoutine = System.nanoTime();

            // 4. Enum values — O(1) lookup via cached project-wide enum value map.
            // First call per project pays the build cost; subsequent calls are instant.
            if (results.isEmpty()) {
                PsiElement enumElement = nl.akiar.pascal.resolution.PascalEnumValueIndex.findEnumValueInScope(
                        name, file, offset);
                if (enumElement != null) {
                    results.add(new PsiElementResolveResult(enumElement));
                }
            }
            tEnum = System.nanoTime();

            ResolveResult[] out = results.toArray(new ResolveResult[0]);
            tBeforeReturn = System.nanoTime();
            return out;
        } finally {
            long tFinally = System.nanoTime();
            long total = (tFinally - t0) / 1_000_000L;
            if (total >= 100L) {
                long varMs = (tVar - t0) / 1_000_000L;
                long typeMs = (tType - tVar) / 1_000_000L;
                long routineMs = (tRoutine - tType) / 1_000_000L;
                long enumMs = (tEnum - tRoutine) / 1_000_000L;
                long toArrayMs = tBeforeReturn > 0 ? (tBeforeReturn - tEnum) / 1_000_000L : -1L;
                long postReturnMs = tBeforeReturn > 0 ? (tFinally - tBeforeReturn) / 1_000_000L : -1L;
                LOG.warn("[PascalIdentRef] SLOW multiResolve name='" + name + "' total=" + total + "ms"
                        + " var=" + varMs + "ms type=" + typeMs + "ms routine=" + routineMs + "ms enum=" + enumMs + "ms"
                        + " toArray=" + toArrayMs + "ms postReturn=" + postReturnMs + "ms");
            }
        }
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length > 0 ? resolveResults[0].getElement() : null;
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
