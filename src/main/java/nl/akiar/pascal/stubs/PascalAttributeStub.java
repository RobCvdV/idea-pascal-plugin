package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.AttributeTargetType;
import nl.akiar.pascal.psi.PascalAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stub for Pascal attributes, enabling indexed lookup.
 */
public interface PascalAttributeStub extends StubElement<PascalAttribute> {
    /**
     * Get the attribute name (without "Attribute" suffix).
     */
    @NotNull
    String getName();

    /**
     * Get the raw argument text if present.
     */
    @Nullable
    String getArguments();

    /**
     * Get the type of element this attribute is attached to.
     */
    @NotNull
    AttributeTargetType getTargetType();
}
