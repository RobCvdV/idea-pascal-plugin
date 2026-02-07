package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalPropertyStub.
 */
public class PascalPropertyStubImpl extends StubBase<PascalProperty> implements PascalPropertyStub {

    private final String name;
    private final String typeName;
    private final String containingClassName;
    private final String unitName;
    private final String visibility;

    public PascalPropertyStubImpl(
            @Nullable StubElement<?> parent,
            @Nullable String name,
            @Nullable String typeName,
            @Nullable String containingClassName,
            @Nullable String unitName,
            @Nullable String visibility) {
        super(parent, PascalElementTypes.PROPERTY_DEFINITION);
        this.name = name;
        this.typeName = typeName;
        this.containingClassName = containingClassName;
        this.unitName = unitName;
        this.visibility = visibility;
    }

    @Override
    @Nullable
    public String getName() {
        return name;
    }

    @Override
    @Nullable
    public String getTypeName() {
        return typeName;
    }

    @Override
    @Nullable
    public String getContainingClassName() {
        return containingClassName;
    }

    @Override
    @Nullable
    public String getUnitName() {
        return unitName;
    }

    @Override
    @Nullable
    public String getVisibility() {
        return visibility;
    }
}
