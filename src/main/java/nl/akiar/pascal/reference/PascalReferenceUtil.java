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

        // Handle routine declaration ↔ implementation equivalence
        if (resolved instanceof PascalRoutine && target instanceof PascalRoutine) {
            PascalRoutine resolvedRoutine = (PascalRoutine) resolved;
            PascalRoutine targetRoutine = (PascalRoutine) target;

            // If target is declaration, check if resolved is its implementation (or vice versa)
            if (!targetRoutine.isImplementation()) {
                PascalRoutine targetImpl = targetRoutine.getImplementation();
                if (targetImpl != null && targetImpl.equals(resolved)) return true;
            }
            if (targetRoutine.isImplementation()) {
                PascalRoutine targetDecl = targetRoutine.getDeclaration();
                if (targetDecl != null && targetDecl.equals(resolved)) return true;
            }
            if (!resolvedRoutine.isImplementation()) {
                PascalRoutine resolvedImpl = resolvedRoutine.getImplementation();
                if (resolvedImpl != null && resolvedImpl.equals(target)) return true;
            }
            if (resolvedRoutine.isImplementation()) {
                PascalRoutine resolvedDecl = resolvedRoutine.getDeclaration();
                if (resolvedDecl != null && resolvedDecl.equals(target)) return true;
            }
        }

        return false;
    }
}
