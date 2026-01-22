package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalRoutine;
import org.jetbrains.annotations.NotNull;

public class PascalRoutineIndex extends StringStubIndexExtension<PascalRoutine> {
    public static final StubIndexKey<String, PascalRoutine> KEY =
            StubIndexKey.createIndexKey("pascal.routine.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalRoutine> getKey() {
        return KEY;
    }
}
