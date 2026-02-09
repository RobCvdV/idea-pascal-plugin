package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI element representing the return type of a function.
 * <p>
 * This element wraps the return type declaration after the colon in a function signature.
 * Applies to ALL functions: standalone, methods, and anonymous functions.
 * <p>
 * This element is important for linking to:
 * <ul>
 *   <li>The implicit "Result" variable in functions</li>
 *   <li>The Exit() function when called with a value</li>
 * </ul>
 * <p>
 * Examples:
 * <pre>
 * function GetValue: Integer;        // RETURN_TYPE wraps "Integer"
 * function TClass.GetName: String;   // RETURN_TYPE wraps "String"
 * function GetList: TList&lt;Integer&gt;; // RETURN_TYPE wraps "TList&lt;Integer&gt;"
 * </pre>
 */
public interface PascalReturnType extends PsiElement {

    /**
     * Get the type name as a string.
     * <p>
     * Examples: "Integer", "String", "TList&lt;Integer&gt;"
     *
     * @return the type name, never null
     */
    @NotNull
    String getTypeName();

    /**
     * Get the type element (usually a TYPE_REFERENCE).
     *
     * @return the type element, or null if not found
     */
    @Nullable
    PsiElement getTypeElement();

    /**
     * Resolve the return type to its definition if possible.
     *
     * @return the resolved type definition, or null if not resolvable
     */
    @Nullable
    PascalTypeDefinition resolveType();

    /**
     * Check if the return type is a simple built-in type.
     * <p>
     * Built-in types include: Integer, String, Boolean, etc.
     *
     * @return true if this is a built-in type
     */
    boolean isBuiltInType();
}
