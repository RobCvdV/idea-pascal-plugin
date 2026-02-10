package nl.akiar.pascal.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Pascal if statement (if/then/else).
 */
public interface PascalIfStatement extends PascalStatement {

    /**
     * Gets the condition expression of the if statement.
     *
     * @return the condition expression, or null if not found
     */
    @Nullable
    PascalExpression getCondition();

    /**
     * Gets the then branch statement.
     *
     * @return the then branch, or null if not found
     */
    @Nullable
    PascalStatement getThenBranch();

    /**
     * Gets the else branch statement.
     *
     * @return the else branch, or null if there is no else clause
     */
    @Nullable
    PascalStatement getElseBranch();

    /**
     * Checks if this if statement has an else clause.
     *
     * @return true if there is an else branch
     */
    default boolean hasElseBranch() {
        return getElseBranch() != null;
    }
}
