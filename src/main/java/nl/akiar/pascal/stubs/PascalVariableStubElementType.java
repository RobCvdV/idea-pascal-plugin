package nl.akiar.pascal.stubs;

import com.intellij.lang.ASTNode;
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

    public PascalVariableStubElementType() {
        super("VARIABLE_DEFINITION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalVariableDefinition createPsi(@NotNull PascalVariableStub stub) {
        return new PascalVariableDefinitionImpl(stub, this);
    }

    @Override
    @NotNull
    public PascalVariableStub createStub(@NotNull PascalVariableDefinition psi, StubElement<?> parentStub) {
        String name = psi.getName();
        String typeName = psi.getTypeName();
        VariableKind kind = psi.getVariableKind();
        String containingScopeName = psi.getContainingScopeName();

        // Extract owner type name safely (local AST only)
        String ownerTypeName = null;
        try {
            nl.akiar.pascal.psi.PascalTypeDefinition owner = psi.getContainingClass();
            if (owner != null) {
                ownerTypeName = owner.getName();
            }
        } catch (Exception ignored) {
            // Guard against any exceptions during stub creation
        }

        // Extract visibility safely (local AST only)
        String visibility = null;
        try {
            visibility = psi.getVisibility();
        } catch (Exception ignored) {
            // Guard against any exceptions during stub creation
        }

        return new PascalVariableStubImpl(parentStub, name, typeName, kind, containingScopeName, ownerTypeName, visibility);
    }

    @Override
    @NotNull
    public String getExternalId() {
        return "pascal.variable.definition";
    }

    @Override
    public void serialize(@NotNull PascalVariableStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeName(stub.getTypeName());
        dataStream.writeInt(stub.getVariableKind().ordinal());
        dataStream.writeName(stub.getContainingScopeName());
        dataStream.writeName(stub.getOwnerTypeName());
        dataStream.writeName(stub.getVisibility());
    }

    @Override
    @NotNull
    public PascalVariableStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        String typeName = dataStream.readNameString();
        int kindOrdinal = dataStream.readInt();
        VariableKind kind = VariableKind.values()[kindOrdinal];
        String containingScopeName = dataStream.readNameString();
        String ownerTypeName = dataStream.readNameString();
        String visibility = dataStream.readNameString();
        return new PascalVariableStubImpl(parentStub, name, typeName, kind, containingScopeName, ownerTypeName, visibility);
    }

    @Override
    public void indexStub(@NotNull PascalVariableStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(PascalVariableIndex.KEY, name.toLowerCase());
            if (stub.getVariableKind() == nl.akiar.pascal.psi.VariableKind.FIELD) {
                String key = PascalScopedMemberIndex.compositeKey(
                        stub.getContainingScopeName(),
                        stub.getOwnerTypeName(),
                        name,
                        "field");
                sink.occurrence(PascalScopedMemberIndex.FIELD_KEY, key);
            }
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();
        return psi instanceof PascalVariableDefinition;
    }
}
