package nl.akiar.pascal.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.stubs.PascalRoutineIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context-aware reference for free-standing routine calls (not member access).
 * Resolution order:
 * 1. Containing class members (implicit Self.Method)
 * 2. Uses-clause-validated routine lookup
 * Prefers declarations over implementations.
 */
public class PascalRoutineCallReference extends PsiReferenceBase<PsiElement> {
    private final String name;

    public PascalRoutineCallReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()));
        this.name = element.getText();
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        PsiFile file = myElement.getContainingFile();
        int offset = myElement.getTextOffset();

        // 1. Check containing class members (implicit Self.MethodName)
        PsiElement at = file.findElementAt(offset);
        PascalTypeDefinition containingClass = findContainingClass(at);
        if (containingClass != null) {
            for (PsiElement member : containingClass.getMembers(true)) {
                if (member instanceof PascalRoutine r
                        && name.equalsIgnoreCase(r.getName())
                        && !r.isImplementation()) {
                    return member;
                }
            }
        }

        // 2. Uses-clause-validated routine lookup
        PascalRoutineIndex.RoutineLookupResult result =
            PascalRoutineIndex.findRoutinesWithUsesValidation(name, file, offset);
        for (PascalRoutine r : result.getInScopeRoutines()) {
            if (!r.isImplementation()) return r;
        }
        if (!result.getInScopeRoutines().isEmpty()) {
            return result.getInScopeRoutines().get(0);
        }
        return null;
    }

    private PascalTypeDefinition findContainingClass(PsiElement element) {
        PascalRoutine routine = PsiTreeUtil.getParentOfType(element, PascalRoutine.class);
        if (routine != null) return routine.getContainingClass();
        return PsiTreeUtil.getParentOfType(element, PascalTypeDefinition.class);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
