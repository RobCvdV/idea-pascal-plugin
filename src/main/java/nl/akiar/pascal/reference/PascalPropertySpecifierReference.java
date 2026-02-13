package nl.akiar.pascal.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference for property getter/setter specifier identifiers.
 * Resolves the identifier after read/write to the corresponding field or method
 * in the containing class or record.
 */
public class PascalPropertySpecifierReference extends PsiReferenceBase<PsiElement> {
    private final String name;

    public PascalPropertySpecifierReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()));
        this.name = element.getText();
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        PascalProperty property = PsiTreeUtil.getParentOfType(myElement, PascalProperty.class);
        if (property == null) return null;

        PascalTypeDefinition containingClass = property.getContainingClass();
        if (containingClass == null) return null;

        for (PsiElement member : containingClass.getMembers(true)) {
            if (member instanceof PsiNameIdentifierOwner) {
                String memberName = ((PsiNameIdentifierOwner) member).getName();
                if (name.equalsIgnoreCase(memberName)) {
                    return member;
                }
            }
        }
        return null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
