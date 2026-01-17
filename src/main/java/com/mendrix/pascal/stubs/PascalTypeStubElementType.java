package com.mendrix.pascal.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.mendrix.pascal.PascalLanguage;
import com.mendrix.pascal.psi.PascalTypeDefinition;
import com.mendrix.pascal.psi.TypeKind;
import com.mendrix.pascal.psi.impl.PascalTypeDefinitionImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stub element type for Pascal type definitions.
 * Handles serialization/deserialization and indexing.
 */
public class PascalTypeStubElementType extends IStubElementType<PascalTypeStub, PascalTypeDefinition> {
    private static final Logger LOG = Logger.getInstance(PascalTypeStubElementType.class);

    public PascalTypeStubElementType() {
        super("PASCAL_TYPE_DEFINITION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalTypeDefinition createPsi(@NotNull PascalTypeStub stub) {
        return new PascalTypeDefinitionImpl(stub, this);
    }

    @Override
    @NotNull
    public PascalTypeStub createStub(@NotNull PascalTypeDefinition psi, StubElement<?> parentStub) {
        LOG.info("[PascalStub] Creating stub for: " + psi.getName() + " (" + psi.getTypeKind() + ")");
        return new PascalTypeStubImpl(parentStub, psi.getName(), psi.getTypeKind(), psi.getTypeParameters());
    }

    @Override
    @NotNull
    public String getExternalId() {
        return "pascal.typeDefinition";
    }

    @Override
    public void serialize(@NotNull PascalTypeStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeInt(stub.getTypeKind().ordinal());
        List<String> typeParameters = stub.getTypeParameters();
        dataStream.writeInt(typeParameters.size());
        for (String param : typeParameters) {
            dataStream.writeName(param);
        }
    }

    @Override
    @NotNull
    public PascalTypeStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        int kindOrdinal = dataStream.readInt();
        TypeKind kind = TypeKind.values()[kindOrdinal];
        int paramCount = dataStream.readInt();
        List<String> typeParameters = new ArrayList<>(paramCount);
        for (int i = 0; i < paramCount; i++) {
            typeParameters.add(dataStream.readNameString());
        }
        return new PascalTypeStubImpl(parentStub, name, kind, typeParameters);
    }

    @Override
    public void indexStub(@NotNull PascalTypeStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            LOG.info("[PascalStub] Indexing type: " + name + " (" + stub.getTypeKind() + ")");
            sink.occurrence(PascalTypeIndex.KEY, name.toLowerCase());
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();
        return psi instanceof PascalTypeDefinition;
    }
}
