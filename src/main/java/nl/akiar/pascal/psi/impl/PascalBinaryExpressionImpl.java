package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalBinaryExpression;
import nl.akiar.pascal.psi.PascalExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Implementation of PascalBinaryExpression PSI element.
 */
public class PascalBinaryExpressionImpl extends ASTWrapperPsiElement implements PascalBinaryExpression {

    private static final Set<IElementType> OPERATOR_TYPES = Set.of(
            // Arithmetic
            PascalTokenTypes.PLUS, PascalTokenTypes.MINUS,
            PascalTokenTypes.MULT, PascalTokenTypes.DIVIDE,
            PascalTokenTypes.KW_DIV, PascalTokenTypes.KW_MOD,
            // Comparison
            PascalTokenTypes.EQ, PascalTokenTypes.NE,
            PascalTokenTypes.LT, PascalTokenTypes.LE,
            PascalTokenTypes.GT, PascalTokenTypes.GE,
            // Logical
            PascalTokenTypes.KW_AND, PascalTokenTypes.KW_OR,
            PascalTokenTypes.KW_XOR,
            // Bitwise
            PascalTokenTypes.KW_SHL, PascalTokenTypes.KW_SHR,
            // String
            PascalTokenTypes.KW_IN, PascalTokenTypes.KW_IS,
            PascalTokenTypes.KW_AS
    );

    public PascalBinaryExpressionImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @Nullable
    public PascalExpression getLeftOperand() {
        // The left operand is the first expression child
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PascalExpression) {
                return (PascalExpression) child;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalExpression getRightOperand() {
        // The right operand is the second expression child
        boolean foundFirst = false;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PascalExpression) {
                if (foundFirst) {
                    return (PascalExpression) child;
                }
                foundFirst = true;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PsiElement getOperator() {
        // Find the operator token between the two operands
        for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (OPERATOR_TYPES.contains(child.getElementType())) {
                return child.getPsi();
            }
        }
        return null;
    }

    @Override
    @NotNull
    public String getOperatorText() {
        PsiElement op = getOperator();
        return op != null ? op.getText() : "";
    }

    @Override
    public String toString() {
        return "PascalBinaryExpression(" + getOperatorText() + ")";
    }
}
