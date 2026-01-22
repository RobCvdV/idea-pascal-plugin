package nl.akiar.pascal.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import nl.akiar.pascal.PascalPsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for Pascal routine definitions (procedures, functions, methods).
 */
public interface PascalRoutine extends PsiNameIdentifierOwner {
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
}
