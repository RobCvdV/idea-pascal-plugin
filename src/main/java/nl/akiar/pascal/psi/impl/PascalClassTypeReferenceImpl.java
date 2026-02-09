package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.psi.PascalClassTypeReference;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalClassTypeReference PSI element.
 */
public class PascalClassTypeReferenceImpl extends ASTWrapperPsiElement implements PascalClassTypeReference {

    public PascalClassTypeReferenceImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public String getClassName() {
        return getText().trim();
    }

    @Override
    @Nullable
    public PascalTypeDefinition resolveClass() {
        // TODO: Implement class resolution using PascalSymbolResolver
        // For now, return null - will be implemented when type resolution is integrated
        return null;
    }

    @Override
    @Nullable
    public PsiElement getMethodNameReference() {
        // Look for METHOD_NAME_REFERENCE as next sibling (after the dot)
        PsiElement current = this;
        while (current != null) {
            current = current.getNextSibling();
            if (current != null) {
                // Skip whitespace and dot
                String text = current.getText().trim();
                if (text.isEmpty() || text.equals(".")) {
                    continue;
                }
                // Check if it's a METHOD_NAME_REFERENCE
                if (current.getNode().getElementType() == PascalElementTypes.METHOD_NAME_REFERENCE) {
                    return current;
                }
                // If we hit any other meaningful element, stop
                break;
            }
        }
        return null;
    }

    @Override
    @NotNull
    public String getQualifiedName() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClassName());

        PsiElement methodRef = getMethodNameReference();
        if (methodRef != null) {
            sb.append(".").append(methodRef.getText().trim());
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "PascalClassTypeReference(" + getClassName() + ")";
    }
}
