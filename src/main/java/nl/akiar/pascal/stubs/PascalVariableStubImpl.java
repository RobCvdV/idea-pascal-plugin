package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalVariableStub.
 */
public class PascalVariableStubImpl extends StubBase<PascalVariableDefinition> implements PascalVariableStub {

    private final String name;
    private final String typeName;
    private final VariableKind variableKind;
    private final String containingScopeName;
    private final String ownerTypeName;
    private final String visibility;

    public PascalVariableStubImpl(
            @Nullable StubElement<?> parent,
            @Nullable String name,
            @Nullable String typeName,
            @NotNull VariableKind variableKind,
            @Nullable String containingScopeName,
            @Nullable String ownerTypeName,
            @Nullable String visibility) {
        super(parent, PascalElementTypes.VARIABLE_DEFINITION);
        this.name = name;
        this.typeName = typeName;
        this.variableKind = variableKind;
        this.containingScopeName = containingScopeName;
        this.ownerTypeName = ownerTypeName;
        this.visibility = visibility;
    }

    @Override
    @Nullable
    public String getName() { return name; }

    @Override
    @Nullable
    public String getTypeName() { return typeName; }

    @Override
    @NotNull
    public VariableKind getVariableKind() { return variableKind; }

    @Override
    @Nullable
    public String getContainingScopeName() { return containingScopeName; }

    @Override
    @Nullable
    public String getOwnerTypeName() { return ownerTypeName; }

    @Override
    @Nullable
    public String getVisibility() { return visibility; }
}
