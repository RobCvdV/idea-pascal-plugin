package nl.akiar.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Scoped index helpers for properties and fields using composite keys.
 * Key format: unit|owner|name|kind
 */
public final class PascalScopedMemberIndex {
    public static final StubIndexKey<String, PascalProperty> PROPERTY_KEY = StubIndexKey.createIndexKey("pascal.scoped.property.index");
    public static final StubIndexKey<String, PascalVariableDefinition> FIELD_KEY = StubIndexKey.createIndexKey("pascal.scoped.field.index");

    private PascalScopedMemberIndex() {}

    public static String compositeKey(String unitName, String ownerTypeName, String name, String kind) {
        return (unitName == null ? "" : unitName.toLowerCase()) + "|" +
               (ownerTypeName == null ? "" : ownerTypeName.toLowerCase()) + "|" +
               (name == null ? "" : name.toLowerCase()) + "|" +
               (kind == null ? "" : kind.toLowerCase());
    }

    public static Collection<PascalProperty> findProperties(@NotNull String unitOwnerName, @NotNull String typeOwnerName, @NotNull String name, @NotNull Project project) {
        String key = compositeKey(unitOwnerName, typeOwnerName, name, "property");
        return StubIndex.getElements(PROPERTY_KEY, key, project, GlobalSearchScope.allScope(project), PascalProperty.class);
    }

    public static Collection<PascalVariableDefinition> findFields(@NotNull String unitOwnerName, @NotNull String typeOwnerName, @NotNull String name, @NotNull Project project) {
        String key = compositeKey(unitOwnerName, typeOwnerName, name, "field");
        return StubIndex.getElements(FIELD_KEY, key, project, GlobalSearchScope.allScope(project), PascalVariableDefinition.class);
    }
}

