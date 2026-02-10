package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalExpression;
import nl.akiar.pascal.psi.PascalIfStatement;
import nl.akiar.pascal.psi.PascalStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalIfStatement PSI element.
 */
public class PascalIfStatementImpl extends ASTWrapperPsiElement implements PascalIfStatement {

    public PascalIfStatementImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @Nullable
    public PascalExpression getCondition() {
        // The condition is the expression between 'if' and 'then'
        boolean foundIf = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.KW_IF) {
                foundIf = true;
                continue;
            }

            if (childNode.getElementType() == PascalTokenTypes.KW_THEN) {
                break;
            }

            if (foundIf && child instanceof PascalExpression) {
                return (PascalExpression) child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalStatement getThenBranch() {
        // The then branch is the statement after 'then' and before 'else' (if any)
        boolean foundThen = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.KW_THEN) {
                foundThen = true;
                continue;
            }

            if (childNode.getElementType() == PascalTokenTypes.KW_ELSE) {
                break;
            }

            if (foundThen && child instanceof PascalStatement) {
                return (PascalStatement) child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalStatement getElseBranch() {
        // The else branch is the statement after 'else'
        boolean foundElse = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode == null) continue;

            if (childNode.getElementType() == PascalTokenTypes.KW_ELSE) {
                foundElse = true;
                continue;
            }

            if (foundElse && child instanceof PascalStatement) {
                return (PascalStatement) child;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "PascalIfStatement";
    }
}
