package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.AttributeTargetType;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.PascalElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalAttributeStub.
 */
public class PascalAttributeStubImpl extends StubBase<PascalAttribute> implements PascalAttributeStub {
    private final String name;
    private final @Nullable String arguments;
    private final AttributeTargetType targetType;

    public PascalAttributeStubImpl(StubElement<?> parent, @NotNull String name,
                                    @Nullable String arguments, @NotNull AttributeTargetType targetType) {
        super(parent, PascalElementTypes.ATTRIBUTE_DEFINITION);
        this.name = name;
        this.arguments = arguments;
        this.targetType = targetType;
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @Nullable
    public String getArguments() {
        return arguments;
    }

    @Override
    @NotNull
    public AttributeTargetType getTargetType() {
        return targetType;
    }
}
