package nl.akiar.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PascalPropertyIndex extends StringStubIndexExtension<PascalProperty> {
    public static final StubIndexKey<String, PascalProperty> KEY =
            StubIndexKey.createIndexKey("pascal.property.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalProperty> getKey() {
        return KEY;
    }

    public static Collection<PascalProperty> findProperties(@NotNull String name, @NotNull Project project) {
        return StubIndex.getElements(
                KEY,
                name.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalProperty.class
        );
    }
}
