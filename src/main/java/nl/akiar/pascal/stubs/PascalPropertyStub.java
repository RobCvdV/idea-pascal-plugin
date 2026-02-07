package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Stub for Pascal property definitions.
 */
public interface PascalPropertyStub extends StubElement<PascalProperty> {

    @Nullable
    String getName();

    @Nullable
    String getTypeName();

    @Nullable
    String getContainingClassName();

    @Nullable
    String getUnitName();

    @Nullable
    String getVisibility();
}
