package com.mendrix.pascal.dpr;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Provides additional library roots from DPR-referenced files.
 * This makes IntelliJ index files that are referenced by .dpr files
 * but are outside the current project folder.
 */
public class DprLibraryRootsProvider extends AdditionalLibraryRootsProvider {
    private static final Logger LOG = Logger.getInstance(DprLibraryRootsProvider.class);

    @Override
    @NotNull
    public Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
        DprProjectService dprService = DprProjectService.getInstance(project);
        Set<String> directories = dprService.getReferencedDirectories();

        if (directories.isEmpty()) {
            LOG.info("[DprLibraryRoots] No DPR-referenced directories found");
            return Collections.emptyList();
        }

        // Convert directory paths to VirtualFiles
        List<VirtualFile> roots = new ArrayList<>();
        LocalFileSystem fs = LocalFileSystem.getInstance();

        for (String dirPath : directories) {
            VirtualFile dir = fs.findFileByPath(dirPath);
            if (dir != null && dir.isValid() && dir.isDirectory()) {
                // Only add directories that are NOT already in the project
                if (!isInsideProject(project, dir)) {
                    roots.add(dir);
                }
            }
        }

        if (roots.isEmpty()) {
            LOG.info("[DprLibraryRoots] All DPR directories are already in project");
            return Collections.emptyList();
        }

        LOG.info("[DprLibraryRoots] Adding " + roots.size() + " directories as library roots");

        // Create a single synthetic library containing all DPR-referenced directories
        SyntheticLibrary library = new DprSyntheticLibrary(roots);
        return Collections.singletonList(library);
    }

    private boolean isInsideProject(@NotNull Project project, @NotNull VirtualFile file) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return false;

        String basePath = baseDir.getPath();
        String filePath = file.getPath();
        return filePath.startsWith(basePath);
    }

    /**
     * Synthetic library representing DPR-referenced source files.
     */
    private static class DprSyntheticLibrary extends SyntheticLibrary implements ItemPresentation {
        private final List<VirtualFile> roots;

        DprSyntheticLibrary(List<VirtualFile> roots) {
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
            DprSyntheticLibrary that = (DprSyntheticLibrary) o;
            return Objects.equals(roots, that.roots);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roots);
        }

        @Override
        @Nullable
        public String getPresentableText() {
            return "Delphi Project References";
        }

        @Override
        @Nullable
        public String getLocationString() {
            return roots.size() + " directories from .dpr files";
        }

        @Override
        @Nullable
        public Icon getIcon(boolean unused) {
            return null;
        }
    }
}
