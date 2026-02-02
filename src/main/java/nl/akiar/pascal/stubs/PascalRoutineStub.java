package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalRoutine;
import org.jetbrains.annotations.Nullable;

public interface PascalRoutineStub extends StubElement<PascalRoutine> {
    String getName();
    boolean isImplementation();
    /** Returns simple name of containing class (e.g., "TStrings") for methods; null for global routines. */
    @Nullable
    String getContainingClassName();

    /**
     * Returns the return type name for functions (e.g., "TStrings", "Integer").
     * Returns null for procedures (no return type).
     */
    @Nullable
    String getReturnTypeName();
}
