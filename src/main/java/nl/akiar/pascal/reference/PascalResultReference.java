package nl.akiar.pascal.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.psi.PascalRoutine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference for the Result keyword that resolves to the enclosing function.
 */
public class PascalResultReference extends PsiReferenceBase<PsiElement> {

    public PascalResultReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()));
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        PascalRoutine routine = PsiTreeUtil.getParentOfType(myElement, PascalRoutine.class);
        while (routine != null) {
            if (routine.getReturnTypeName() != null) return routine;
            routine = PsiTreeUtil.getParentOfType(routine, PascalRoutine.class);
        }
        return null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
