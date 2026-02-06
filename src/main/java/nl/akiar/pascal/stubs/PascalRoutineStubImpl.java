package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalRoutine;
import org.jetbrains.annotations.Nullable;

public class PascalRoutineStubImpl extends StubBase<PascalRoutine> implements PascalRoutineStub {
    private final String name;
    private final boolean isImplementation;
    private final @Nullable String containingClassName;
    private final @Nullable String returnTypeName;
    private final @Nullable String unitName;
    private final @Nullable String signatureHash;

    public PascalRoutineStubImpl(StubElement parent, String name, boolean isImplementation,
                                  @Nullable String containingClassName, @Nullable String returnTypeName,
                                  @Nullable String unitName, @Nullable String signatureHash) {
        super(parent, PascalElementTypes.ROUTINE_DECLARATION);
        this.name = name;
        this.isImplementation = isImplementation;
        this.containingClassName = containingClassName;
        this.returnTypeName = returnTypeName;
        this.unitName = unitName;
        this.signatureHash = signatureHash;
    }

    @Override
    public String getName() { return name; }

    @Override
    public boolean isImplementation() { return isImplementation; }

    @Override
    public @Nullable String getContainingClassName() { return containingClassName; }

    @Override
    public @Nullable String getReturnTypeName() { return returnTypeName; }

    @Override
    public @Nullable String getUnitName() { return unitName; }

    @Override
    public @Nullable String getSignatureHash() { return signatureHash; }
}
