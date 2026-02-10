package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Pascal for statement (for-to or for-in loop).
 */
public interface PascalForStatement extends PascalStatement {

    /**
     * Gets the loop variable.
     *
     * @return the loop variable element, or null if not found
     */
    @Nullable
    PsiElement getLoopVariable();

    /**
     * Gets the start expression (for for-to loops).
     * Example: In "for I := 1 to 10", this returns the expression for 1.
     *
     * @return the start expression, or null if this is a for-in loop or not found
     */
    @Nullable
    PascalExpression getStartExpression();

    /**
     * Gets the end expression (for for-to loops).
     * Example: In "for I := 1 to 10", this returns the expression for 10.
     *
     * @return the end expression, or null if this is a for-in loop or not found
     */
    @Nullable
    PascalExpression getEndExpression();

    /**
     * Gets the iterable expression (for for-in loops).
     * Example: In "for Item in Collection", this returns the expression for Collection.
     *
     * @return the iterable expression, or null if this is a for-to loop or not found
     */
    @Nullable
    PascalExpression getIterableExpression();

    /**
     * Gets the loop body statement.
     *
     * @return the body statement, or null if not found
     */
    @Nullable
    PascalStatement getBody();

    /**
     * Checks if this is a for-in loop (as opposed to for-to/downto).
     *
     * @return true if for-in, false if for-to/downto
     */
    boolean isForIn();
}
