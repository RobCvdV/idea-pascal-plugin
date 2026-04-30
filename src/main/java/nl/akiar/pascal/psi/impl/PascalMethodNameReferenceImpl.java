package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalMethodNameReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalMethodNameReference PSI element.
 */
public class PascalMethodNameReferenceImpl extends ASTWrapperPsiElement implements PascalMethodNameReference {

    public PascalMethodNameReferenceImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @Nullable
    public PsiElement getClassReference() {
        // Look backwards for TYPE_REFERENCE or IDENTIFIER before DOT
        PsiElement sibling = getPrevSibling();
        while (sibling != null) {
            if (sibling.getNode().getElementType() == PascalTokenTypes.DOT) {
                // Found the dot, now look for the class name before it
                PsiElement prev = sibling.getPrevSibling();
                while (prev != null) {
                    if (prev.getNode().getElementType() == PascalElementTypes.TYPE_REFERENCE ||
                        prev.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
                        return prev;
                    }
                    if (prev.getNode().getElementType() != PascalTokenTypes.WHITE_SPACE) {
                        break;
                    }
                    prev = prev.getPrevSibling();
                }
                break;
            }
            if (sibling.getNode().getElementType() != PascalTokenTypes.WHITE_SPACE) {
                break;
            }
            sibling = sibling.getPrevSibling();
        }
        return null;
    }

    @Override
    @NotNull
    public String getMethodName() {
        return getText().trim();
    }

    @Override
    @NotNull
    public String getQualifiedName() {
        PsiElement classRef = getClassReference();
        if (classRef != null) {
            return classRef.getText().trim() + "." + getMethodName();
        }
        return getMethodName();
    }

    @Override
    @Nullable
    public String getName() {
        return getMethodName();
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        // Find the IDENTIFIER child within this METHOD_NAME_REFERENCE node
        ASTNode node = getNode();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == PascalTokenTypes.IDENTIFIER) {
                nl.akiar.pascal.psi.PascalPsiFactory.INSTANCE.replaceIdentifier(child.getPsi(), name);
                return this;
            }
        }
        // Fallback: replace the entire node text
        nl.akiar.pascal.psi.PascalPsiFactory.INSTANCE.replaceIdentifier(this, name);
        return this;
    }

    @Override
    public String toString() {
        return "PascalMethodNameReference(" + getMethodName() + ")";
    }
}

