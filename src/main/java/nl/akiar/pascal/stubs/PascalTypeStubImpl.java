package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.TypeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Implementation of PascalTypeStub.
 */
public class PascalTypeStubImpl extends StubBase<PascalTypeDefinition> implements PascalTypeStub {
    private final String name;
    private final TypeKind typeKind;
    private final List<String> typeParameters;
    private final List<String> allAncestorNames;
    private final List<String> enumValueNames;
    private final String helpedTypeName;

    public PascalTypeStubImpl(StubElement<?> parent, @Nullable String name, @NotNull TypeKind typeKind,
                              @NotNull List<String> typeParameters, @NotNull List<String> allAncestorNames,
                              @NotNull List<String> enumValueNames, @Nullable String helpedTypeName) {
        super(parent, PascalElementTypes.TYPE_DEFINITION);
        this.name = name;
        this.typeKind = typeKind;
        this.typeParameters = typeParameters;
        this.allAncestorNames = allAncestorNames;
        this.enumValueNames = enumValueNames;
        this.helpedTypeName = helpedTypeName;
    }

    /** Backwards-compatible constructor for non-helper types. */
    public PascalTypeStubImpl(StubElement<?> parent, @Nullable String name, @NotNull TypeKind typeKind,
                              @NotNull List<String> typeParameters, @NotNull List<String> allAncestorNames,
                              @NotNull List<String> enumValueNames) {
        this(parent, name, typeKind, typeParameters, allAncestorNames, enumValueNames, null);
    }

    /** Backwards-compatible constructor without enum value names — used only by older deserialization paths. */
    public PascalTypeStubImpl(StubElement<?> parent, @Nullable String name, @NotNull TypeKind typeKind,
                              @NotNull List<String> typeParameters, @NotNull List<String> allAncestorNames) {
        this(parent, name, typeKind, typeParameters, allAncestorNames, Collections.emptyList(), null);
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
        return allAncestorNames.isEmpty() ? null : allAncestorNames.get(0);
    }

    @Override
    @NotNull
    public List<String> getAllAncestorNames() {
        return allAncestorNames;
    }

    @Override
    @NotNull
    public List<String> getEnumValueNames() {
        return enumValueNames;
    }

    @Override
    @Nullable
    public String getHelpedTypeName() {
        return helpedTypeName;
    }
}
