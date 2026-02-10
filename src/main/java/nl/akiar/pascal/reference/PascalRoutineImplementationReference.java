package nl.akiar.pascal.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.psi.PascalRoutine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference for routine implementation names that resolves to their declaration in the interface section.
 */
public class PascalRoutineImplementationReference extends PsiReferenceBase<PsiElement> {
    private final PascalRoutine routine;

    public PascalRoutineImplementationReference(@NotNull PsiElement element, @NotNull PascalRoutine routine) {
        super(element, new TextRange(0, element.getTextLength()));
        this.routine = routine;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        PascalRoutine counterpart = routine.isImplementation() ? routine.getDeclaration() : routine.getImplementation();
        // If the counterpart is the same as the routine itself (already in interface or no counterpart), 
        // return null to avoid self-reference which usually disables navigation.
        if (counterpart == routine) {
            return null;
        }
        return counterpart;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
