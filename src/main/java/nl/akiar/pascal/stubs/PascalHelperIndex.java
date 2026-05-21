package nl.akiar.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import nl.akiar.pascal.uses.PascalUsesClauseUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Stub index mapping the helped type name to class/record helper definitions.
 * <p>
 * Key: lowercase simple name of the helped type (e.g., {@code "trride"}, {@code "string"}),
 * stripped of generic arguments and unit prefixes.
 * Value: the helper {@link PascalTypeDefinition} (the {@code TFoo} in
 * {@code TFoo = class helper for TBar}).
 * <p>
 * Used by {@code MemberChainResolver} and {@code PascalMemberCompletionProvider}
 * to surface helper members on the helped type and its descendants.
 */
public class PascalHelperIndex extends StringStubIndexExtension<PascalTypeDefinition> {
    public static final StubIndexKey<String, PascalTypeDefinition> KEY =
            StubIndexKey.createIndexKey("pascal.helper.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalTypeDefinition> getKey() {
        return KEY;
    }

    /** All helpers in the project that target the given type name (no uses filtering). */
    @NotNull
    public static Collection<PascalTypeDefinition> findAllHelpersFor(@NotNull String helpedTypeName,
                                                                     @NotNull Project project) {
        return StubIndex.getElements(
                KEY,
                helpedTypeName.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalTypeDefinition.class
        );
    }

    /**
     * Helpers visible from {@code fromFile} at {@code offset}: their declaring unit
     * must appear in {@code fromFile}'s uses clause (or be the same file), mirroring
     * the uses-validation pattern used elsewhere in this package.
     */
    @NotNull
    public static List<PascalTypeDefinition> findHelpersFor(@NotNull String helpedTypeName,
                                                            @NotNull PsiFile fromFile,
                                                            int offset) {
        Collection<PascalTypeDefinition> all = findAllHelpersFor(helpedTypeName, fromFile.getProject());
        if (all.isEmpty()) return List.of();

        PascalUsesClauseUtil.UsesClauseInfo usesInfo = PascalUsesClauseUtil.parseUsesClause(fromFile);
        List<String> scopes = PascalSourcePathsSettings.getInstance(fromFile.getProject()).getUnitScopeNames();

        List<PascalTypeDefinition> inScope = new ArrayList<>();
        for (PascalTypeDefinition helper : all) {
            PsiFile targetFile = helper.getContainingFile();
            if (targetFile == null) continue;
            if (targetFile.equals(fromFile)) {
                inScope.add(helper);
                continue;
            }
            if (usesInfo.findUnitInUses(helper.getUnitName(), offset, scopes) != null) {
                inScope.add(helper);
            }
        }
        return inScope;
    }
}
