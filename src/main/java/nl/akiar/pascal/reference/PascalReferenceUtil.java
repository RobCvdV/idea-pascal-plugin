package nl.akiar.pascal.reference;

import com.intellij.psi.PsiElement;
import nl.akiar.pascal.psi.PascalRoutine;
import org.jetbrains.annotations.Nullable;

/**
 * Shared utility for Pascal reference resolution.
 */
public class PascalReferenceUtil {

    /**
     * Check if a resolved element is the same as (or declaration/implementation pair of) the target.
     * Handles the Delphi pattern where routines have both an interface declaration
     * and an implementation body — both should be considered the same symbol.
     */
    public static boolean isEquivalentTarget(@Nullable PsiElement resolved, @Nullable PsiElement target) {
        if (resolved == null || target == null) return false;
        if (resolved.equals(target)) return true;

        // Routine declaration ↔ implementation pairing: walk from target to its counterpart
        // and compare to resolved. One direction is sufficient — getDeclaration() and
        // getImplementation() both look up the pair via stub indices.
        if (resolved instanceof PascalRoutine && target instanceof PascalRoutine) {
            PascalRoutine targetRoutine = (PascalRoutine) target;
            PascalRoutine counterpart = targetRoutine.isImplementation()
                ? targetRoutine.getDeclaration()
                : targetRoutine.getImplementation();
            return counterpart != null && counterpart.equals(resolved);
        }

        return false;
    }
}
