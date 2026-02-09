package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PSI element representing a formal parameter list in a routine declaration.
 * <p>
 * This element includes the parentheses and all parameters within them.
 * Maps to sonar-delphi's RoutineParametersNode.
 * <p>
 * Structure: ( param1: Type; param2: Type )
 * <p>
 * Examples:
 * <pre>
 * ()                                       // empty parameter list
 * (A: Integer)                             // single parameter
 * (var A: Integer; const B: String)        // multiple parameters with modifiers
 * </pre>
 */
public interface PascalFormalParameterList extends PsiElement {

    /**
     * Get all parameters in this parameter list.
     * <p>
     * Returns parameters in declaration order.
     * Returns empty list if parameter list is empty.
     *
     * @return list of parameters, never null
     */
    @NotNull
    List<PascalVariableDefinition> getParameters();

    /**
     * Get the opening parenthesis token.
     *
     * @return the LPAREN element, or null if not found
     */
    @Nullable
    PsiElement getOpenParen();

    /**
     * Get the closing parenthesis token.
     *
     * @return the RPAREN element, or null if not found
     */
    @Nullable
    PsiElement getCloseParen();

    /**
     * Check if this parameter list is empty (no parameters).
     *
     * @return true if empty
     */
    boolean isEmpty();

    /**
     * Get the number of parameters in this list.
     *
     * @return parameter count
     */
    int getParameterCount();

    /**
     * Get the complete text of the parameter list including parentheses.
     *
     * @return the parameter list text
     */
    @NotNull
    String getParameterListText();
}
