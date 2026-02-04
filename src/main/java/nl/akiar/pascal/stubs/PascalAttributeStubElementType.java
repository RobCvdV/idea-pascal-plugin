package nl.akiar.pascal.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.AttributeTargetType;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.impl.PascalAttributeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Stub element type for Pascal attributes.
 * Handles serialization/deserialization and indexing.
 */
public class PascalAttributeStubElementType extends IStubElementType<PascalAttributeStub, PascalAttribute> {

    public PascalAttributeStubElementType() {
        super("PASCAL_ATTRIBUTE_DEFINITION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalAttribute createPsi(@NotNull PascalAttributeStub stub) {
        return new PascalAttributeImpl(stub, this);
    }

    @NotNull
    @Override
    public PascalAttributeStub createStub(@NotNull PascalAttribute psi, StubElement<?> parentStub) {
        return new PascalAttributeStubImpl(
            parentStub,
            psi.getName(),
            psi.getArguments(),
            psi.getTargetType()
        );
    }

    @NotNull
    @Override
    public String getExternalId() {
        return "pascal.attribute";
    }

    @Override
    public void serialize(@NotNull PascalAttributeStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeName(stub.getArguments());
        dataStream.writeInt(stub.getTargetType().ordinal());
    }

    @NotNull
    @Override
    public PascalAttributeStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        String arguments = dataStream.readNameString();
        int targetTypeOrdinal = dataStream.readInt();
        AttributeTargetType targetType = AttributeTargetType.values()[targetTypeOrdinal];
        return new PascalAttributeStubImpl(parentStub, name != null ? name : "", arguments, targetType);
    }

    @Override
    public void indexStub(@NotNull PascalAttributeStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null && !name.isEmpty()) {
            // Index by attribute name (lowercase for case-insensitive lookup)
            sink.occurrence(PascalAttributeIndex.KEY, name.toLowerCase());
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();
        return psi instanceof PascalAttribute;
    }
}
