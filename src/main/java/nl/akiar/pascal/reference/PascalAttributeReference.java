package nl.akiar.pascal.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Reference from an attribute name to its defining class.
 * For example, [Required] resolves to RequiredAttribute class.
 */
public class PascalAttributeReference extends PsiReferenceBase<PsiElement> {
    private final PascalAttribute attribute;

    public PascalAttributeReference(@NotNull PsiElement element, @NotNull PascalAttribute attribute) {
        super(element, new TextRange(0, element.getTextLength()));
        this.attribute = attribute;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        // GUIDs don't resolve to a class
        if (attribute.isGUID()) {
            return null;
        }

        String attrClassName = attribute.getAttributeClassName();

        // Look up the attribute class
        Collection<PascalTypeDefinition> types =
                PascalTypeIndex.findTypes(attrClassName, getElement().getProject());

        // Return the first match
        return types.isEmpty() ? null : types.iterator().next();
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        // Could provide completion suggestions for attribute classes here
        return EMPTY_ARRAY;
    }
}
