package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Stub index keyed on lowercased enum-value name → containing PascalTypeDefinition.
 *
 * Each TYPE_DEFINITION stub registers an occurrence under every ENUM_ELEMENT
 * name declared anywhere in its PSI tree, including nested enum types inside
 * classes. This lets unqualified enum-value resolution (e.g. {@code crNormal}
 * referencing a value of an enum nested in some class somewhere in the
 * project) succeed without loading the AST of every file: the lookup returns
 * the candidate type definitions purely from stub data.
 */
public class PascalEnumValueStubIndex extends StringStubIndexExtension<PascalTypeDefinition> {
    public static final StubIndexKey<String, PascalTypeDefinition> KEY =
            StubIndexKey.createIndexKey("pascal.enumValue.byName.index");

    @NotNull
    @Override
    public StubIndexKey<String, PascalTypeDefinition> getKey() {
        return KEY;
    }
}
