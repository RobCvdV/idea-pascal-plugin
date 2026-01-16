package com.mendrix.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.mendrix.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Stub index for Pascal type definitions.
 * Allows looking up type definitions by name across the project.
 */
public class PascalTypeIndex extends StringStubIndexExtension<PascalTypeDefinition> {
    public static final StubIndexKey<String, PascalTypeDefinition> KEY =
            StubIndexKey.createIndexKey("pascal.type.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalTypeDefinition> getKey() {
        return KEY;
    }

    /**
     * Find all type definitions with the given name (case-insensitive).
     */
    public static Collection<PascalTypeDefinition> findTypes(@NotNull String name, @NotNull Project project) {
        return StubIndex.getElements(
                KEY,
                name.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalTypeDefinition.class
        );
    }
}
