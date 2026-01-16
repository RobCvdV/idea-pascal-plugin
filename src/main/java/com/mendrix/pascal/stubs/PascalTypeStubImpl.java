package com.mendrix.pascal.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.mendrix.pascal.psi.PascalElementTypes;
import com.mendrix.pascal.psi.PascalTypeDefinition;
import com.mendrix.pascal.psi.TypeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalTypeStub.
 */
public class PascalTypeStubImpl extends StubBase<PascalTypeDefinition> implements PascalTypeStub {
    private final String name;
    private final TypeKind typeKind;

    public PascalTypeStubImpl(StubElement<?> parent, @Nullable String name, @NotNull TypeKind typeKind) {
        super(parent, PascalElementTypes.TYPE_DEFINITION);
        this.name = name;
        this.typeKind = typeKind;
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
}
