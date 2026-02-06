package nl.akiar.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalRoutine;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Scoped routine index keyed by unit + owner type + routine name.
 * Key format: (unit + "#" + owner + "#" + name).toLowerCase()
 */
public class PascalScopedRoutineIndex extends StringStubIndexExtension<PascalRoutine> {
    public static final StubIndexKey<String, PascalRoutine> KEY =
            StubIndexKey.createIndexKey("pascal.scoped.routine.index");

    @Override
    public @NotNull StubIndexKey<String, PascalRoutine> getKey() {
        return KEY;
    }

    public static Collection<PascalRoutine> find(@NotNull String unitOwnerName, @NotNull Project project) {
        return StubIndex.getElements(
                KEY,
                unitOwnerName.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalRoutine.class
        );
    }
}
