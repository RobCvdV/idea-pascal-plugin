package nl.akiar.pascal.dpr;

import com.intellij.diagnostic.LoadingState;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import nl.akiar.pascal.project.PascalProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Registers .dpr-referenced and .dproj/.optset-discovered directories as
 * library roots so IntelliJ indexes them.
 *
 * User-configured paths from Settings → Pascal Source Paths are handled by
 * {@link nl.akiar.pascal.settings.PascalSourcePathsLibraryRootsProvider}.
 */
public class DprLibraryRootsProvider extends AdditionalLibraryRootsProvider {
    private static final Logger LOG = Logger.getInstance(DprLibraryRootsProvider.class);
    private static final boolean DEBUG = Boolean.getBoolean("pascal.units.debug");

    @Override
    @NotNull
    public Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
        if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
            return Collections.emptyList();
        }

        if (project.isDefault() || !project.isInitialized() || project.isDisposed()) {
            return Collections.emptyList();
        }

        Set<String> allDirectories = new HashSet<>();

        try {
            DprProjectService dprService = DprProjectService.getInstance(project);
            allDirectories.addAll(dprService.getReferencedDirectories());

            PascalProjectService pascalProjectService = PascalProjectService.getInstance(project);
            allDirectories.addAll(pascalProjectService.getDiscoveredDirectories());
        } catch (Exception e) {
            LOG.warn("[PascalUnits] DprLibraryRoots: error collecting directories: " + e.getMessage());
            return Collections.emptyList();
        }

        if (allDirectories.isEmpty()) {
            return Collections.emptyList();
        }

        // Convert directory paths to VirtualFiles
        List<VirtualFile> roots = new ArrayList<>();
        LocalFileSystem fs = LocalFileSystem.getInstance();

        for (String dirPath : allDirectories) {
            VirtualFile dir = fs.findFileByPath(dirPath);
            if (dir != null && dir.isValid() && dir.isDirectory()) {
                // Only add directories that are NOT already in the project
                if (!isInsideProject(project, dir)) {
                    roots.add(dir);
                }
            }
        }

        if (roots.isEmpty()) {
            if (DEBUG) LOG.info("[PascalUnits] DprLibraryRoots: no roots after filtering (in-project or invalid)");
            return Collections.emptyList();
        }

        if (DEBUG) {
            LOG.info("[PascalUnits] DprLibraryRoots: registering " + roots.size() + " roots");
            for (VirtualFile r : roots) {
                LOG.info("[PascalUnits]   root: " + r.getPath());
            }
        }

        SyntheticLibrary library = new PascalSyntheticLibrary(roots);
        return Collections.singletonList(library);
    }

    private boolean isInsideProject(@NotNull Project project, @NotNull VirtualFile file) {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return false;

        String basePath = baseDir.getPath();
        String filePath = file.getPath();
        return filePath.startsWith(basePath);
    }

    /**
     * Synthetic library representing Pascal source directories.
     */
    private static class PascalSyntheticLibrary extends SyntheticLibrary implements ItemPresentation {
        private final List<VirtualFile> roots;

        PascalSyntheticLibrary(List<VirtualFile> roots) {
            this.roots = roots;
        }

        @Override
        @NotNull
        public Collection<VirtualFile> getSourceRoots() {
            return roots;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PascalSyntheticLibrary that = (PascalSyntheticLibrary) o;
            return Objects.equals(roots, that.roots);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roots);
        }

        @Override
        @Nullable
        public String getPresentableText() {
            return "Pascal Source Paths";
        }

        @Override
        @Nullable
        public String getLocationString() {
            return roots.size() + " directories";
        }

        @Override
        @Nullable
        public Icon getIcon(boolean unused) {
            return null;
        }
    }
}
