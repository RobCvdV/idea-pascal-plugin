package nl.akiar.pascal.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference for the Self keyword that resolves to the containing class.
 */
public class PascalSelfReference extends PsiReferenceBase<PsiElement> {

    public PascalSelfReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()));
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        PascalRoutine routine = PsiTreeUtil.getParentOfType(myElement, PascalRoutine.class);
        while (routine != null) {
            PascalTypeDefinition cls = routine.getContainingClass();
            if (cls != null) return cls;
            routine = PsiTreeUtil.getParentOfType(routine, PascalRoutine.class);
        }
        return PsiTreeUtil.getParentOfType(myElement, PascalTypeDefinition.class);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
