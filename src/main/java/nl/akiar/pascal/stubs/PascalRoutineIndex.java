package nl.akiar.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import nl.akiar.pascal.uses.PascalUsesClauseUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PascalRoutineIndex extends StringStubIndexExtension<PascalRoutine> {
    public static final StubIndexKey<String, PascalRoutine> KEY =
            StubIndexKey.createIndexKey("pascal.routine.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalRoutine> getKey() {
        return KEY;
    }

    public static Collection<PascalRoutine> findRoutines(@NotNull String name, @NotNull Project project) {
        return StubIndex.getElements(
                KEY,
                name.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalRoutine.class
        );
    }

    public static RoutineLookupResult findRoutinesWithUsesValidation(
            @NotNull String name,
            @NotNull PsiFile fromFile,
            int offset) {

        Collection<PascalRoutine> allRoutines = findRoutines(name, fromFile.getProject());
        PascalUsesClauseUtil.UsesClauseInfo usesInfo = PascalUsesClauseUtil.parseUsesClause(fromFile);

        List<PascalRoutine> inScope = new ArrayList<>();
        List<PascalRoutine> outOfScope = new ArrayList<>();

        List<String> scopes = PascalSourcePathsSettings.getInstance(fromFile.getProject()).getUnitScopeNames();

        for (PascalRoutine routine : allRoutines) {
            String targetUnit = routine.getUnitName();
            PsiFile targetFile = routine.getContainingFile();
            if (targetFile == null) continue;

            // Same file is always in scope
            if (targetFile.equals(fromFile)) {
                inScope.add(routine);
                continue;
            }

            if (usesInfo.findUnitInUses(targetUnit, offset, scopes) != null) {
                inScope.add(routine);
            } else {
                outOfScope.add(routine);
            }
        }

        return new RoutineLookupResult(inScope, outOfScope, usesInfo, offset);
    }

    public static class RoutineLookupResult {
        private final List<PascalRoutine> inScopeRoutines;
        private final List<PascalRoutine> outOfScopeRoutines;
        private final PascalUsesClauseUtil.UsesClauseInfo usesInfo;
        private final int referenceOffset;

        public RoutineLookupResult(List<PascalRoutine> inScope, List<PascalRoutine> outOfScope,
                                   PascalUsesClauseUtil.UsesClauseInfo usesInfo, int referenceOffset) {
            this.inScopeRoutines = inScope;
            this.outOfScopeRoutines = outOfScope;
            this.usesInfo = usesInfo;
            this.referenceOffset = referenceOffset;
        }

        public List<PascalRoutine> getInScopeRoutines() {
            return inScopeRoutines;
        }

        public List<PascalRoutine> getOutOfScopeRoutines() {
            return outOfScopeRoutines;
        }

        public boolean isEmpty() {
            return inScopeRoutines.isEmpty() && outOfScopeRoutines.isEmpty();
        }

        @org.jetbrains.annotations.Nullable
        public String getErrorMessage() {
            java.util.Set<String> inScopeUnits = new java.util.LinkedHashSet<>();
            for (PascalRoutine routine : inScopeRoutines) {
                inScopeUnits.add(routine.getUnitName());
            }

            if (inScopeUnits.size() <= 1 && !inScopeRoutines.isEmpty()) {
                return null;
            }

            if (inScopeUnits.size() > 1) {
                return "Ambiguous reference. Found in multiple units: " + String.join(", ", inScopeUnits);
            }

            if (outOfScopeRoutines.isEmpty()) {
                return null;
            }

            java.util.Set<String> outOfScopeUnits = new java.util.LinkedHashSet<>();
            for (PascalRoutine routine : outOfScopeRoutines) {
                outOfScopeUnits.add(routine.getUnitName());
            }

            if (outOfScopeUnits.size() > 1) {
                return "Ambiguous reference found in multiple units (none in uses clause): " + String.join(", ", outOfScopeUnits);
            }

            String unitName = outOfScopeUnits.iterator().next();
            if (usesInfo.isInInterfaceSection(referenceOffset)) {
                if (usesInfo.getImplementationUses().contains(unitName.toLowerCase())) {
                    return "Unit '" + unitName + "' is in implementation uses, but routine is referenced in interface section. Add it to interface uses.";
                }
                return "Unit '" + unitName + "' is not in uses clause. Add it to interface uses.";
            } else {
                return "Unit '" + unitName + "' is not in uses clause. Add it to uses clause.";
            }
        }
    }
}
