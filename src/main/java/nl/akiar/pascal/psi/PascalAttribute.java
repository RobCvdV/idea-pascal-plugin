package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import nl.akiar.pascal.stubs.PascalAttributeStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Pascal attribute/decorator, e.g., [Required] or [Map('Id')].
 *
 * Delphi attributes use the syntax:
 * <pre>
 *   [AttributeName]
 *   [AttributeName(argument)]
 *   [AttributeName(arg1, arg2)]
 * </pre>
 *
 * The actual class name follows the convention: [Foo] maps to class FooAttribute.
 */
public interface PascalAttribute extends PsiNameIdentifierOwner, StubBasedPsiElement<PascalAttributeStub> {

    /**
     * Get the attribute name as written in the source (without "Attribute" suffix).
     * For [Required], returns "Required".
     * For [Map('Id')], returns "Map".
     *
     * @return The attribute name
     */
    @Override
    @NotNull
    String getName();

    /**
     * Get the raw argument text if present.
     * For [Map('Id')], returns "'Id'".
     * For [Required], returns null.
     *
     * @return The arguments without parentheses, or null if none
     */
    @Nullable
    String getArguments();

    /**
     * Get the full attribute text including brackets.
     * For [Map('Id')], returns "[Map('Id')]".
     *
     * @return The complete attribute text
     */
    @NotNull
    String getFullText();

    /**
     * Get the full class name of the attribute.
     * For [Foo], returns "FooAttribute".
     *
     * @return The attribute class name with "Attribute" suffix
     */
    @NotNull
    default String getAttributeClassName() {
        return getName() + "Attribute";
    }

    /**
     * Resolve this attribute to its class definition.
     * Looks up the class with name getAttributeClassName() in scope.
     *
     * @return The resolved attribute class, or null if not found
     */
    @Nullable
    PascalTypeDefinition resolveAttributeClass();

    /**
     * Get the element this attribute is attached to.
     * This is the type, routine, property, or field that follows the attribute.
     *
     * @return The target element, or null if not determinable
     */
    @Nullable
    PsiElement getTarget();

    /**
     * Get the type of target this attribute is attached to.
     *
     * @return The target type
     */
    @NotNull
    AttributeTargetType getTargetType();

    /**
     * Check if this is a GUID attribute for an interface.
     * GUID attributes have the format ['{GUID-STRING}'].
     *
     * @return true if this is a GUID attribute
     */
    default boolean isGUID() {
        String name = getName();
        return name.startsWith("'{") && name.endsWith("}'");
    }

    /**
     * Get the GUID string if this is a GUID attribute.
     * For ['{285DEA8A-B865-11D1-AAA7-00C04FB17A72}'], returns "285DEA8A-B865-11D1-AAA7-00C04FB17A72".
     *
     * @return The GUID string without braces, or null if not a GUID attribute
     */
    @Nullable
    default String getGUIDValue() {
        if (!isGUID()) return null;
        String name = getName();
        // Strip '{ and }'
        return name.substring(2, name.length() - 2);
    }
}
