package nl.akiar.pascal.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference from an attribute name to its defining class.
 * For example, [Required] resolves to RequiredAttribute class.
 */
public class PascalAttributeReference extends PsiReferenceBase<PsiElement> {
    @Nullable
    private final PascalAttribute attribute;

    public PascalAttributeReference(@NotNull PsiElement element, @Nullable PascalAttribute attribute) {
        super(element, new TextRange(0, element.getTextLength()));
        this.attribute = attribute;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        String attrName;
        if (attribute != null) {
            if (attribute.isGUID()) {
                return null;
            }
            attrName = attribute.getName();
        } else {
            attrName = getElement().getText();
        }

        String attrClassName = attrName.endsWith("Attribute") ? attrName : attrName + "Attribute";

        PsiFile file = getElement().getContainingFile();
        if (file == null) {
            return null;
        }

        PascalTypeIndex.TypeLookupResult result = PascalTypeIndex.findTypesWithUsesValidation(
                attrClassName, file, getElement().getTextOffset());

        if (!result.getInScopeTypes().isEmpty()) {
            return result.getInScopeTypes().get(0);
        }
        if (!result.getOutOfScopeTypes().isEmpty()) {
            return result.getOutOfScopeTypes().get(0);
        }
        return null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        // Could provide completion suggestions for attribute classes here
        return EMPTY_ARRAY;
    }
}
