package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.TypeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implementation of PascalTypeStub.
 */
public class PascalTypeStubImpl extends StubBase<PascalTypeDefinition> implements PascalTypeStub {
    private final String name;
    private final TypeKind typeKind;
    private final List<String> typeParameters;
    private final String superClassName;

    public PascalTypeStubImpl(StubElement<?> parent, @Nullable String name, @NotNull TypeKind typeKind,
                              @NotNull List<String> typeParameters, @Nullable String superClassName) {
        super(parent, PascalElementTypes.TYPE_DEFINITION);
        this.name = name;
        this.typeKind = typeKind;
        this.typeParameters = typeParameters;
        this.superClassName = superClassName;
    }

    @Override
    @Nullable
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public TypeKind getTypeKind() {
        return typeKind;
    }

    @Override
    @NotNull
    public List<String> getTypeParameters() {
        return typeParameters;
    }

    @Override
    @Nullable
    public String getSuperClassName() {
        return superClassName;
    }
}
