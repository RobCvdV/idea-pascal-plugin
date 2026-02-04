package nl.akiar.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalAttribute;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Stub index for Pascal attributes.
 * Allows fast lookup of attributes by name across the project.
 */
public class PascalAttributeIndex extends StringStubIndexExtension<PascalAttribute> {
    public static final StubIndexKey<String, PascalAttribute> KEY =
        StubIndexKey.createIndexKey("pascal.attribute");

    @Override
    @NotNull
    public StubIndexKey<String, PascalAttribute> getKey() {
        return KEY;
    }

    /**
     * Find all usages of an attribute by name (case-insensitive).
     *
     * @param name The attribute name without "Attribute" suffix
     * @param project The project to search in
     * @return Collection of attribute usages
     */
    public static Collection<PascalAttribute> findAttributes(String name, Project project) {
        return StubIndex.getElements(KEY, name.toLowerCase(), project,
            GlobalSearchScope.allScope(project), PascalAttribute.class);
    }

    /**
     * Find all usages of an attribute by name within a specific scope.
     *
     * @param name The attribute name without "Attribute" suffix
     * @param project The project to search in
     * @param scope The search scope
     * @return Collection of attribute usages
     */
    public static Collection<PascalAttribute> findAttributes(String name, Project project, GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, name.toLowerCase(), project, scope, PascalAttribute.class);
    }
}
