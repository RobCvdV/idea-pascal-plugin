package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalExpression;
import nl.akiar.pascal.psi.PascalForStatement;
import nl.akiar.pascal.psi.PascalStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalForStatement PSI element.
 * Handles both for-to and for-in loops.
 */
public class PascalForStatementImpl extends ASTWrapperPsiElement implements PascalForStatement {

    public PascalForStatementImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @Nullable
    public PsiElement getLoopVariable() {
        // The loop variable is the identifier/expression after 'for'
        boolean foundFor = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.KW_FOR) {
                foundFor = true;
                continue;
            }

            // Stop at := (for-to) or 'in' (for-in)
            if (childNode.getElementType() == PascalTokenTypes.ASSIGN ||
                childNode.getElementType() == PascalTokenTypes.KW_IN) {
                break;
            }

            if (foundFor) {
                // Skip whitespace
                if (childNode.getElementType() == PascalTokenTypes.WHITE_SPACE) continue;

                // Return the first non-whitespace element (the loop variable)
                return child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalExpression getStartExpression() {
        if (isForIn()) return null;

        // For for-to: the expression after ':=' and before 'to' or 'downto'
        boolean foundAssign = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.ASSIGN) {
                foundAssign = true;
                continue;
            }

            if (childNode.getElementType() == PascalTokenTypes.KW_TO ||
                childNode.getElementType() == PascalTokenTypes.KW_DOWNTO) {
                break;
            }

            if (foundAssign && child instanceof PascalExpression) {
                return (PascalExpression) child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalExpression getEndExpression() {
        if (isForIn()) return null;

        // For for-to: the expression after 'to' or 'downto' and before 'do'
        boolean foundTo = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.KW_TO ||
                childNode.getElementType() == PascalTokenTypes.KW_DOWNTO) {
                foundTo = true;
                continue;
            }

            if (childNode.getElementType() == PascalTokenTypes.KW_DO) {
                break;
            }

            if (foundTo && child instanceof PascalExpression) {
                return (PascalExpression) child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalExpression getIterableExpression() {
        if (!isForIn()) return null;

        // For for-in: the expression after 'in' and before 'do'
        boolean foundIn = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.KW_IN) {
                foundIn = true;
                continue;
            }

            if (childNode.getElementType() == PascalTokenTypes.KW_DO) {
                break;
            }

            if (foundIn && child instanceof PascalExpression) {
                return (PascalExpression) child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalStatement getBody() {
        // The body is the statement after 'do'
        boolean foundDo = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.KW_DO) {
                foundDo = true;
                continue;
            }

            if (foundDo && child instanceof PascalStatement) {
                return (PascalStatement) child;
            }
        }
        return null;
    }

    @Override
    public boolean isForIn() {
        // Check if this contains 'in' keyword
        for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == PascalTokenTypes.KW_IN) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "PascalForStatement(" + (isForIn() ? "for-in" : "for-to") + ")";
    }
}
