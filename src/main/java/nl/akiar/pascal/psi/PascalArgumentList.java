package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a Pascal argument list in a function/procedure call.
 */
public interface PascalArgumentList extends PsiElement {

    /**
     * Gets all arguments in the list.
     *
     * @return list of argument expressions
     */
    @NotNull
    List<PascalExpression> getArguments();

    /**
     * Gets the number of arguments.
     *
     * @return argument count
     */
    int getArgumentCount();
}
