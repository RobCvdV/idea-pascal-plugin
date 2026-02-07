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
        super("PROPERTY_DEFINITION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalProperty createPsi(@NotNull PascalPropertyStub stub) {
        return new PascalPropertyImpl(stub, this);
    }

    @NotNull
    @Override
    public PascalPropertyStub createStub(@NotNull PascalProperty psi, StubElement parentStub) {
        String name = psi.getName();
        String typeName = psi.getTypeName();
        String owner = psi.getContainingClassName();

        // Extract unit name from file name directly (local AST only)
        String unitName = null;
        try {
            com.intellij.psi.PsiFile file = psi.getContainingFile();
            if (file != null) {
                String fileName = file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                unitName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
            }
        } catch (Exception ignored) {
            // Guard against any exceptions during stub creation
        }

        // Extract visibility from local AST only
        String visibility = null;
        try {
            visibility = nl.akiar.pascal.psi.PsiUtil.getVisibility(psi);
        } catch (Exception ignored) {
            // Guard against any exceptions during stub creation
        }

        return new PascalPropertyStubImpl(parentStub, name, typeName, owner, unitName, visibility);
    }

    @NotNull
    @Override
    public String getExternalId() {
        return "pascal.property.definition";
    }

    @Override
    public void serialize(@NotNull PascalPropertyStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeName(stub.getTypeName());
        dataStream.writeName(stub.getContainingClassName());
        dataStream.writeName(stub.getUnitName() == null ? "" : stub.getUnitName());
        dataStream.writeName(stub.getVisibility() == null ? "" : stub.getVisibility());
    }

    @NotNull
    @Override
    public PascalPropertyStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        String typeName = dataStream.readNameString();
        String owner = dataStream.readNameString();
        String unitName = dataStream.readNameString();
        String visibility = dataStream.readNameString();
        return new PascalPropertyStubImpl(parentStub, name, typeName, owner, unitName, visibility);
    }

    @Override
    public void indexStub(@NotNull PascalPropertyStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(PascalPropertyIndex.KEY, name.toLowerCase());
            String key = PascalScopedMemberIndex.compositeKey(stub.getUnitName(), stub.getContainingClassName(), name, "property");
            sink.occurrence(PascalScopedMemberIndex.PROPERTY_KEY, key);
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();
        return psi instanceof PascalProperty;
    }
}
