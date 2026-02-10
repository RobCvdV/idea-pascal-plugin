package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Pascal assignment statement (variable := expression).
 */
public interface PascalAssignmentStatement extends PascalStatement {

    /**
     * Gets the left-hand side (target) of the assignment.
     * This can be a simple variable, array element, or member chain.
     *
     * @return the assignment target, or null if not found
     */
    @Nullable
    PsiElement getTarget();

    /**
     * Gets the right-hand side (source) expression of the assignment.
     *
     * @return the source expression, or null if not found
     */
    @Nullable
    PascalExpression getSource();
}
