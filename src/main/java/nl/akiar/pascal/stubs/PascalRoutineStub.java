package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalRoutine;

public interface PascalRoutineStub extends StubElement<PascalRoutine> {
    String getName();
    boolean isImplementation();
}
