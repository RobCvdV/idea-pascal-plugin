package nl.akiar.pascal.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.impl.PascalPropertyImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PascalPropertyStubElementType extends IStubElementType<PascalPropertyStub, PascalProperty> {
    public PascalPropertyStubElementType() {
        super("PASCAL_PROPERTY_DEFINITION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalProperty createPsi(@NotNull PascalPropertyStub stub) {
        return new PascalPropertyImpl(stub, this);
    }

    @NotNull
    @Override
    public PascalPropertyStub createStub(@NotNull PascalProperty psi, StubElement parentStub) {
        return new PascalPropertyStubImpl(
                parentStub,
                psi.getName(),
                psi.getTypeName(),
                psi.getContainingClassName()
        );
    }

    @NotNull
    @Override
    public String getExternalId() {
        return "pascal.propertyDefinition";
    }

    @Override
    public void serialize(@NotNull PascalPropertyStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeName(stub.getTypeName());
        dataStream.writeName(stub.getContainingClassName());
    }

    @NotNull
    @Override
    public PascalPropertyStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        String typeName = dataStream.readNameString();
        String containingClassName = dataStream.readNameString();
        return new PascalPropertyStubImpl(parentStub, name, typeName, containingClassName);
    }

    @Override
    public void indexStub(@NotNull PascalPropertyStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(PascalPropertyIndex.KEY, name.toLowerCase());
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();
        return psi instanceof PascalProperty;
    }
}
