package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PSI element representing a complete routine signature.
 * <p>
 * Wraps the entire signature including:
 * <ul>
 *   <li>Opening parenthesis {@code (}</li>
 *   <li>All formal parameters with semicolons</li>
 *   <li>Closing parenthesis {@code )}</li>
 *   <li>Optional colon and return type (for functions)</li>
 * </ul>
 * <p>
 * Example signatures:
 * <pre>
 * (AParam1: Integer; AParam2: String): Boolean  // function signature
 * (var AValue: Integer; const AName: String)    // procedure signature
 * ()                                             // no parameters
 * </pre>
 */
public interface PascalRoutineSignature extends PsiElement {

    /**
     * Get all formal parameters in this signature.
     * <p>
     * Returns parameters in declaration order.
     * Returns empty list if signature has no parameters.
     *
     * @return list of parameters, never null
     */
    @NotNull
    List<PascalVariableDefinition> getParameters();

    /**
     * Get the return type element for functions.
     * <p>
     * For procedures (no return type), this returns null.
     * For functions, returns the TYPE_REFERENCE or other type element.
     *
     * @return the return type element, or null for procedures
     */
    @Nullable
    PsiElement getReturnType();

    /**
     * Check if this is a function signature (has a return type).
     *
     * @return true if this signature has a return type
     */
    boolean isFunction();

    /**
     * Get the return type name as a string.
     * <p>
     * Examples: "Integer", "TList", "Boolean"
     *
     * @return the return type name, or null for procedures
     */
    @Nullable
    String getReturnTypeName();

    /**
     * Get the complete signature text without the routine body.
     * <p>
     * Example: "(AParam: Integer; AValue: String): Boolean"
     *
     * @return the signature text
     */
    @NotNull
    String getSignatureText();

    /**
     * Get the number of parameters in this signature.
     *
     * @return parameter count
     */
    int getParameterCount();
}

