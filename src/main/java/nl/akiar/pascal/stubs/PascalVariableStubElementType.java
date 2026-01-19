package nl.akiar.pascal.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;
import nl.akiar.pascal.psi.impl.PascalVariableDefinitionImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Stub element type for Pascal variable definitions.
 * Handles serialization/deserialization and indexing.
 */
public class PascalVariableStubElementType extends IStubElementType<PascalVariableStub, PascalVariableDefinition> {
    private static final Logger LOG = Logger.getInstance(PascalVariableStubElementType.class);

    public PascalVariableStubElementType() {
        super("PASCAL_VARIABLE_DEFINITION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalVariableDefinition createPsi(@NotNull PascalVariableStub stub) {
        return new PascalVariableDefinitionImpl(stub, this);
    }

    @Override
    @NotNull
    public PascalVariableStub createStub(@NotNull PascalVariableDefinition psi, StubElement<?> parentStub) {
        return new PascalVariableStubImpl(
                parentStub,
                psi.getName(),
                psi.getTypeName(),
                psi.getVariableKind(),
                psi.getContainingScopeName()
        );
    }

    @Override
    @NotNull
    public String getExternalId() {
        return "pascal.variableDefinition";
    }

    @Override
    public void serialize(@NotNull PascalVariableStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeName(stub.getTypeName());
        dataStream.writeInt(stub.getVariableKind().ordinal());
        dataStream.writeName(stub.getContainingScopeName());
    }

    @Override
    @NotNull
    public PascalVariableStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        String typeName = dataStream.readNameString();
        int kindOrdinal = dataStream.readInt();
        VariableKind kind = VariableKind.values()[kindOrdinal];
        String containingScopeName = dataStream.readNameString();
        return new PascalVariableStubImpl(parentStub, name, typeName, kind, containingScopeName);
    }

    @Override
    public void indexStub(@NotNull PascalVariableStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(PascalVariableIndex.KEY, name.toLowerCase());
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();
        return psi instanceof PascalVariableDefinition;
    }
}
