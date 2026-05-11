package nl.akiar.pascal.settings;

import com.intellij.diagnostic.LoadingState;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Registers user-configured Pascal source paths (Settings → Pascal Source Paths)
 * as a synthetic library so IntelliJ indexes them.
 *
 * Intentionally separate from {@code DprLibraryRootsProvider}: settings-based
 * paths must reach the indexer regardless of whether any .dpr/.dproj is loaded.
 */
public class PascalSourcePathsLibraryRootsProvider extends AdditionalLibraryRootsProvider {
    private static final Logger LOG = Logger.getInstance(PascalSourcePathsLibraryRootsProvider.class);
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

        List<String> configured;
        try {
            configured = PascalSourcePathsSettings.getInstance(project).getSourcePaths();
        } catch (Exception e) {
            LOG.warn("[PascalUnits] Failed to read source paths settings: " + e.getMessage());
            return Collections.emptyList();
        }

        if (configured.isEmpty()) {
            if (DEBUG) LOG.info("[PascalUnits] SourcePathsProvider: no configured paths");
            return Collections.emptyList();
        }

        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        String projectPath = projectDir != null ? projectDir.getPath() : null;

        LocalFileSystem fs = LocalFileSystem.getInstance();
        List<VirtualFile> roots = new ArrayList<>();
        for (String path : configured) {
            if (path == null || path.isEmpty()) continue;
            VirtualFile dir = fs.findFileByPath(path);
            if (dir == null || !dir.isValid() || !dir.isDirectory()) {
                if (DEBUG) LOG.info("[PascalUnits] SourcePathsProvider: skipping invalid path " + path);
                continue;
            }
            VirtualFile canonical = dir.getCanonicalFile();
            if (canonical != null && canonical.isValid() && canonical.isDirectory()) {
                dir = canonical;
            }
            if (projectPath != null && dir.getPath().startsWith(projectPath)) {
                if (DEBUG) LOG.info("[PascalUnits] SourcePathsProvider: skipping in-project path " + dir.getPath());
                continue;
            }
            roots.add(dir);
        }

        if (roots.isEmpty()) {
            if (DEBUG) LOG.info("[PascalUnits] SourcePathsProvider: no usable roots after filtering");
            return Collections.emptyList();
        }

        if (DEBUG) {
            LOG.info("[PascalUnits] SourcePathsProvider: registering " + roots.size() + " roots");
            for (VirtualFile r : roots) {
                LOG.info("[PascalUnits]   root: " + r.getPath());
            }
        }

        return Collections.singletonList(new SettingsSyntheticLibrary(roots));
    }

    private static class SettingsSyntheticLibrary extends SyntheticLibrary implements ItemPresentation {
        private final List<VirtualFile> roots;

        SettingsSyntheticLibrary(List<VirtualFile> roots) {
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
            SettingsSyntheticLibrary that = (SettingsSyntheticLibrary) o;
            return Objects.equals(roots, that.roots);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roots);
        }

        @Override
        @Nullable
        public String getPresentableText() {
            return "Pascal Source Paths (Settings)";
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
