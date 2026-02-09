package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI element representing the class type reference in method implementations.
 * <p>
 * In Pascal, method implementations use qualified names:
 * <pre>
 * procedure TClassName.MethodName(AParam: Integer);
 * function TClassName.GetValue: Integer;
 * </pre>
 * <p>
 * In these patterns, "TClassName" is marked as CLASS_TYPE_REFERENCE.
 * This allows for:
 * <ul>
 *   <li>Navigation to the class definition</li>
 *   <li>Semantic highlighting for class names</li>
 *   <li>Validation that the class exists</li>
 *   <li>Finding all method implementations for a class</li>
 * </ul>
 */
public interface PascalClassTypeReference extends PsiElement {

    /**
     * Get the class name as a string.
     * <p>
     * Examples: "TMyClass", "TList", "TOuterClass.TInnerClass"
     *
     * @return the class name, never null
     */
    @NotNull
    String getClassName();

    /**
     * Resolve this reference to its class definition.
     *
     * @return the resolved class definition, or null if not resolvable
     */
    @Nullable
    PascalTypeDefinition resolveClass();

    /**
     * Get the method name reference that follows this class reference.
     * <p>
     * Example: In "TClassName.MethodName", returns the element for "MethodName".
     *
     * @return the method name reference, or null if not found
     */
    @Nullable
    PsiElement getMethodNameReference();

    /**
     * Get the fully qualified name including the class and method.
     * <p>
     * Example: "TClassName.MethodName"
     *
     * @return the fully qualified name
     */
    @NotNull
    String getQualifiedName();
}
