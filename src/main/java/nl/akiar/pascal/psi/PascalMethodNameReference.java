package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI element representing a method name in a qualified method implementation.
 * <p>
 * In Pascal, method implementations use qualified names:
 * <pre>
 * procedure TClassName.MethodName(AParam: Integer);
 * </pre>
 * <p>
 * In this pattern, "MethodName" is marked as METHOD_NAME_REFERENCE to distinguish
 * it from regular identifiers and provide better semantic highlighting and navigation.
 * <p>
 * The class part "TClassName" should be marked as CLASS_TYPE_REFERENCE or TYPE_REFERENCE.
 */
public interface PascalMethodNameReference extends PsiElement, PsiNamedElement {

    /**
     * Get the class type reference that precedes this method name.
     * <p>
     * Example: In "TMyClass.MyMethod", returns the element for "TMyClass".
     *
     * @return the class type reference, or null if not found
     */
    @Nullable
    PsiElement getClassReference();

    /**
     * Get the method name text.
     * <p>
     * Example: In "TMyClass.MyMethod", returns "MyMethod".
     *
     * @return the method name, never null
     */
    @NotNull
    String getMethodName();

    /**
     * Get the qualified name including class.
     * <p>
     * Example: "TMyClass.MyMethod"
     *
     * @return the qualified name
     */
    @NotNull
    String getQualifiedName();
}

