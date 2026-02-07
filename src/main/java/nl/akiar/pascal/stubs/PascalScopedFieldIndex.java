package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Stub index extension for scoped field lookups.
 * Uses composite keys: unit|owner|name|field
 */
public class PascalScopedFieldIndex extends StringStubIndexExtension<PascalVariableDefinition> {
    @Override
    public @NotNull StubIndexKey<String, PascalVariableDefinition> getKey() {
        return PascalScopedMemberIndex.FIELD_KEY;
    }
}

