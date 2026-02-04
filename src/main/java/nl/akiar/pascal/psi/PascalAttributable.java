package nl.akiar.pascal.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mixin interface for Pascal elements that can have attributes/decorators.
 * Implemented by PascalTypeDefinition, PascalRoutine, PascalProperty, and PascalVariableDefinition.
 */
public interface PascalAttributable {
    /**
     * Get all attributes attached to this element.
     * For example, for a field declared as:
     * <pre>
     *   [Unsafe] [Weak] FOwner: TPersistent;
     * </pre>
     * This would return [Unsafe, Weak].
     *
     * @return List of attributes, may be empty but never null
     */
    @NotNull
    List<PascalAttribute> getAttributes();

    /**
     * Find an attribute by name (case-insensitive).
     *
     * @param name The attribute name without "Attribute" suffix (e.g., "Required" not "RequiredAttribute")
     * @return The attribute, or null if not found
     */
    @Nullable
    PascalAttribute findAttribute(@NotNull String name);

    /**
     * Check if this element has an attribute with the given name.
     *
     * @param name The attribute name without "Attribute" suffix
     * @return true if the attribute is present
     */
    default boolean hasAttribute(@NotNull String name) {
        return findAttribute(name) != null;
    }
}
