package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalReturnType;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Implementation of PascalReturnType PSI element.
 */
public class PascalReturnTypeImpl extends ASTWrapperPsiElement implements PascalReturnType {

    /**
     * Built-in Pascal types that don't require resolution.
     */
    private static final Set<String> BUILTIN_TYPES = Set.of(
            "integer", "cardinal", "shortint", "smallint", "longint", "int64", "byte", "word", "longword", "uint64",
            "single", "double", "extended", "real", "comp", "currency",
            "char", "ansichar", "widechar",
            "string", "shortstring", "ansistring", "widestring", "unicodestring",
            "boolean", "bytebool", "wordbool", "longbool",
            "pointer", "nativeint", "nativeuint",
            "variant", "olevariant",
            "tguid", "tpoint", "trect"
    );

    public PascalReturnTypeImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public String getTypeName() {
        PsiElement typeElement = getTypeElement();
        if (typeElement != null) {
            return typeElement.getText().trim();
        }
        // Fallback to the whole text, stripping leading colon and whitespace
        String text = getText().trim();
        // RETURN_TYPE includes ": TypeName" - strip the leading ": "
        if (text.startsWith(":")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    @Override
    @Nullable
    public PsiElement getTypeElement() {
        // Look for TYPE_REFERENCE child
        for (PsiElement child : getChildren()) {
            if (child.getNode().getElementType() == PascalElementTypes.TYPE_REFERENCE) {
                return child;
            }
        }
        // If no TYPE_REFERENCE, return first meaningful child
        for (PsiElement child : getChildren()) {
            String text = child.getText().trim();
            if (!text.isEmpty() && !text.equals(":")) {
                return child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalTypeDefinition resolveType() {
        // TODO: Implement type resolution using PascalSymbolResolver
        // For now, return null - will be implemented when type resolution is integrated
        return null;
    }

    @Override
    public boolean isBuiltInType() {
        String typeName = getTypeName().toLowerCase();
        // Strip generic parameters for checking
        int genericStart = typeName.indexOf('<');
        if (genericStart > 0) {
            typeName = typeName.substring(0, genericStart).trim();
        }
        return BUILTIN_TYPES.contains(typeName);
    }

    @Override
    public String toString() {
        return "PascalReturnType(" + getTypeName() + ")";
    }
}
