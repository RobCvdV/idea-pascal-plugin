package nl.akiar.pascal.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import nl.akiar.pascal.stubs.PascalRoutineStub;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for Pascal routine definitions (procedures, functions, methods).
 */
public interface PascalRoutine extends PsiNameIdentifierOwner, StubBasedPsiElement<PascalRoutineStub> {
    /**
     * Check if this is an implementation (has a body).
     */
    boolean isImplementation();

    /**
     * Find the counterpart declaration in the interface section.
     * Only valid for implementations.
     */
    @Nullable
    PascalRoutine getDeclaration();

    /**
     * Find the counterpart implementation in the implementation section.
     * Only valid for declarations in the interface.
     */
    @Nullable
    PascalRoutine getImplementation();

    /**
     * Returns true if this routine is a class method.
     */
    boolean isMethod();

    /**
     * Returns the class that contains this method, or null if it's a global routine.
     */
    @Nullable
    PascalTypeDefinition getContainingClass();

    /**
     * Returns the visibility of the routine (e.g., "private", "protected", "public", "published").
     * For global routines, this is usually null or an empty string.
     */
    @Nullable
    String getVisibility();

    /**
     * Get the documentation comment preceding this routine definition.
     */
    @Nullable
    String getDocComment();
}
