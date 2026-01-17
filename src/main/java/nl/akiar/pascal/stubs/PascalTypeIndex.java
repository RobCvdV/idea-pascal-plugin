package nl.akiar.pascal.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.dpr.DprProjectService;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Stub index for Pascal type definitions.
 * Allows looking up type definitions by name across the project.
 */
public class PascalTypeIndex extends StringStubIndexExtension<PascalTypeDefinition> {
    private static final Logger LOG = Logger.getInstance(PascalTypeIndex.class);

    public static final StubIndexKey<String, PascalTypeDefinition> KEY =
            StubIndexKey.createIndexKey("pascal.type.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalTypeDefinition> getKey() {
        return KEY;
    }

    /**
     * Find all type definitions with the given name (case-insensitive).
     * Searches both the project scope and files referenced by .dpr files.
     */
    public static Collection<PascalTypeDefinition> findTypes(@NotNull String name, @NotNull Project project) {
        // First, search in project scope
        Collection<PascalTypeDefinition> results = StubIndex.getElements(
                KEY,
                name.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalTypeDefinition.class
        );

        // If found in project, return immediately
        if (!results.isEmpty()) {
            return results;
        }

        // Otherwise, search in DPR-referenced files
        GlobalSearchScope dprScope = getDprReferencedFilesScope(project);
        if (dprScope != null) {
            return StubIndex.getElements(
                    KEY,
                    name.toLowerCase(),
                    project,
                    dprScope,
                    PascalTypeDefinition.class
            );
        }

        return results;
    }

    /**
     * Create a search scope containing files referenced by .dpr files.
     * Returns null if no DPR files found or no files referenced.
     */
    private static GlobalSearchScope getDprReferencedFilesScope(@NotNull Project project) {
        DprProjectService dprService = DprProjectService.getInstance(project);
        Set<String> referencedFiles = dprService.getReferencedFiles();

        if (referencedFiles.isEmpty()) {
            return null;
        }

        List<VirtualFile> virtualFiles = new ArrayList<>();
        LocalFileSystem fs = LocalFileSystem.getInstance();

        for (String filePath : referencedFiles) {
            VirtualFile vf = fs.findFileByPath(filePath);
            if (vf != null && vf.isValid()) {
                virtualFiles.add(vf);
            }
        }

        if (virtualFiles.isEmpty()) {
            return null;
        }

        LOG.info("[DprScope] Created scope with " + virtualFiles.size() + " files from DPR references");
        return GlobalSearchScope.filesScope(project, virtualFiles);
    }
}
