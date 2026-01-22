package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalRoutine;

public class PascalRoutineStubImpl extends StubBase<PascalRoutine> implements PascalRoutineStub {
    private final String name;
    private final boolean isImplementation;

    public PascalRoutineStubImpl(StubElement parent, String name, boolean isImplementation) {
        super(parent, PascalElementTypes.ROUTINE_DECLARATION);
        this.name = name;
        this.isImplementation = isImplementation;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isImplementation() {
        return isImplementation;
    }
}
