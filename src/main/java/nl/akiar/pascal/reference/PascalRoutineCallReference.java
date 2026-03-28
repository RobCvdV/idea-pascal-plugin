package nl.akiar.pascal.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.PascalTokenTypes;
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
        boolean isInherited = isPrecededByInherited(myElement);

        // 1. Check containing class members (implicit Self.MethodName)
        // Prefer implementations (code body) over declarations for callsite navigation
        PsiElement at = file.findElementAt(offset);
        PascalTypeDefinition containingClass = findContainingClass(at);
        if (containingClass != null) {
            // For 'inherited Method()', skip the current class and search only in ancestors
            PascalTypeDefinition searchClass = isInherited ? containingClass.getSuperClass() : containingClass;
            if (searchClass != null) {
                PsiElement declarationFallback = null;
                for (PsiElement member : searchClass.getMembers(true)) {
                    if (member instanceof PascalRoutine r
                            && name.equalsIgnoreCase(r.getName())) {
                        if (r.isImplementation()) {
                            return member;
                        }
                        if (declarationFallback == null) {
                            declarationFallback = member;
                        }
                    }
                }
                if (declarationFallback != null) {
                    return declarationFallback;
                }
            }
        }

        // 2. Uses-clause-validated routine lookup
        // Prefer implementations (code body) over declarations for callsite navigation
        PascalRoutineIndex.RoutineLookupResult result =
            PascalRoutineIndex.findRoutinesWithUsesValidation(name, file, offset);
        for (PascalRoutine r : result.getInScopeRoutines()) {
            if (r.isImplementation()) return r;
        }
        for (PascalRoutine r : result.getInScopeRoutines()) {
            if (!r.isImplementation()) return r;
        }
        return null;
    }

    private static boolean isPrecededByInherited(@NotNull PsiElement element) {
        PsiElement prev = PsiTreeUtil.prevLeaf(element);
        while (prev instanceof PsiWhiteSpace || prev instanceof PsiComment) {
            prev = PsiTreeUtil.prevLeaf(prev);
        }
        return prev != null && prev.getNode().getElementType() == PascalTokenTypes.KW_INHERITED;
    }

    private PascalTypeDefinition findContainingClass(PsiElement element) {
        // Walk up through nested routines (anonymous routines) to find the owning class.
        PascalRoutine routine = PsiTreeUtil.getParentOfType(element, PascalRoutine.class);
        while (routine != null) {
            PascalTypeDefinition cls = routine.getContainingClass();
            if (cls != null) return cls;
            routine = PsiTreeUtil.getParentOfType(routine, PascalRoutine.class);
        }
        return PsiTreeUtil.getParentOfType(element, PascalTypeDefinition.class);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
