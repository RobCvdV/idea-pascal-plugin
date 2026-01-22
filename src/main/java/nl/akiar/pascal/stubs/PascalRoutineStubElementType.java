package nl.akiar.pascal.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.impl.PascalRoutineImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PascalRoutineStubElementType extends IStubElementType<PascalRoutineStub, PascalRoutine> {
    public PascalRoutineStubElementType() {
        super("ROUTINE_DECLARATION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalRoutine createPsi(@NotNull PascalRoutineStub stub) {
        return new PascalRoutineImpl(stub, this);
    }

    @NotNull
    @Override
    public PascalRoutineStub createStub(@NotNull PascalRoutine psi, StubElement parentStub) {
        return new PascalRoutineStubImpl(parentStub, psi.getName(), psi.isImplementation());
    }

    @NotNull
    @Override
    public String getExternalId() {
        return "pascal.routine";
    }

    @Override
    public void serialize(@NotNull PascalRoutineStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeBoolean(stub.isImplementation());
    }

    @NotNull
    @Override
    public PascalRoutineStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        boolean isImplementation = dataStream.readBoolean();
        return new PascalRoutineStubImpl(parentStub, name, isImplementation);
    }

    @Override
    public void indexStub(@NotNull PascalRoutineStub stub, @NotNull IndexSink sink) {
        if (stub.getName() != null) {
            sink.occurrence(PascalRoutineIndex.KEY, stub.getName().toLowerCase());
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();
        return psi instanceof nl.akiar.pascal.psi.PascalRoutine;
    }
}
