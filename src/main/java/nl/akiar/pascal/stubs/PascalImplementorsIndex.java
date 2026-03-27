package nl.akiar.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Reverse stub index mapping ancestor names to implementing/extending types.
 * Key: simple lowercase ancestor name (no unit prefix, no generics).
 * Value: PascalTypeDefinition elements that list that ancestor in their parent list.
 */
public class PascalImplementorsIndex extends StringStubIndexExtension<PascalTypeDefinition> {
    public static final StubIndexKey<String, PascalTypeDefinition> KEY =
            StubIndexKey.createIndexKey("pascal.implementors.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalTypeDefinition> getKey() {
        return KEY;
    }

    /**
     * Find all types that list the given ancestor name in their parent list.
     *
     * @param ancestorName simple name (e.g., "IFoo", not "MyUnit.IFoo")
     * @param project the project to search in
     * @return collection of implementing/extending type definitions
     */
    public static Collection<PascalTypeDefinition> findImplementors(@NotNull String ancestorName, @NotNull Project project) {
        return StubIndex.getElements(
                KEY,
                ancestorName.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalTypeDefinition.class
        );
    }
}
