package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Stub index extension for scoped property lookups.
 * Uses composite keys: unit|owner|name|property
 */
public class PascalScopedPropertyIndex extends StringStubIndexExtension<PascalProperty> {
    @Override
    public @NotNull StubIndexKey<String, PascalProperty> getKey() {
        return PascalScopedMemberIndex.PROPERTY_KEY;
    }
}

