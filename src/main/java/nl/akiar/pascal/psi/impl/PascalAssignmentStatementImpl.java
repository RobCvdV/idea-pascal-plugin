package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalAssignmentStatement;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalAssignmentStatement PSI element.
 */
public class PascalAssignmentStatementImpl extends ASTWrapperPsiElement implements PascalAssignmentStatement {

    public PascalAssignmentStatementImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @Nullable
    public PsiElement getTarget() {
        // The target is the first child expression before ':='
        // Look for NAME_REFERENCE or PRIMARY_EXPRESSION as the first expression child
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            // Stop at assignment operator
            if (childNode.getElementType() == PascalTokenTypes.ASSIGN) {
                break;
            }

            // Check for expression types
            if (childNode.getElementType() == PascalElementTypes.NAME_REFERENCE ||
                childNode.getElementType() == PascalElementTypes.PRIMARY_EXPRESSION ||
                childNode.getElementType() == PascalElementTypes.ARRAY_ACCESS) {
                return child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalExpression getSource() {
        // The source is the expression after ':='
        boolean foundAssign = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.ASSIGN) {
                foundAssign = true;
                continue;
            }

            if (foundAssign && child instanceof PascalExpression) {
                return (PascalExpression) child;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "PascalAssignmentStatement";
    }
}
