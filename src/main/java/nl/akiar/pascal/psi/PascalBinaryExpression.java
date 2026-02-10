package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Pascal binary expression (e.g., a + b, x and y, i < count).
 */
public interface PascalBinaryExpression extends PascalExpression {

    /**
     * Gets the left operand expression.
     *
     * @return the left operand, or null if not found
     */
    @Nullable
    PascalExpression getLeftOperand();

    /**
     * Gets the right operand expression.
     *
     * @return the right operand, or null if not found
     */
    @Nullable
    PascalExpression getRightOperand();

    /**
     * Gets the operator element.
     *
     * @return the operator PsiElement, or null if not found
     */
    @Nullable
    PsiElement getOperator();

    /**
     * Gets the operator as text.
     *
     * @return the operator text (e.g., "+", "and", "<>")
     */
    @NotNull
    String getOperatorText();
}
