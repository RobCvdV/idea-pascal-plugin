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

    /** Unit name where this routine is declared (e.g., "System.Classes"). */
    @Nullable
    String getUnitName();

    /** Lightweight signature hash for decl/impl pairing and overload selection. */
    @Nullable
    String getSignatureHash();

    /** Visibility (public, private, protected, published) for class methods; null for global routines. */
    @Nullable
    String getVisibility();

    /** Section (interface, implementation) where the routine is declared; null if not determined. */
    @Nullable
    String getSection();
}
