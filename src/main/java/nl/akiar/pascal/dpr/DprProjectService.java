package nl.akiar.pascal.dpr;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-level service that manages Delphi project (.dpr) file references.
 * Scans for .dpr files and maintains a list of all referenced Pascal source files.
 */
@Service(Service.Level.PROJECT)
public final class DprProjectService implements Disposable {
    private static final Logger LOG = Logger.getInstance(DprProjectService.class);

    private final Project project;

    // Cache: .dpr file path -> list of referenced file paths
    private final Map<String, List<String>> dprFileCache = new ConcurrentHashMap<>();

    // All referenced file paths (union of all .dpr files)
    private volatile Set<String> allReferencedFiles = Collections.emptySet();

    // Flag to track if we've done initial scan
    private volatile boolean initialized = false;

    public DprProjectService(@NotNull Project project) {
        this.project = project;
    }

    public static DprProjectService getInstance(@NotNull Project project) {
        return project.getService(DprProjectService.class);
    }

    /**
     * Get all file paths referenced by .dpr files in the project.
     * Performs lazy initialization on first call.
     */
    @NotNull
    public Set<String> getReferencedFiles() {
        if (!initialized) {
            rescan();
        }
        return allReferencedFiles;
    }

    /**
     * Force rescan of all .dpr files.
     * Call this after project structure changes.
     */
    public void rescan() {
        if (project.isDisposed()) return;
        LOG.info("[DprProjectService] Starting .dpr rescan");
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            synchronized (this) {
                scanDprFiles();
                initialized = true;
            }
            // Notify platform that library roots might have changed
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    com.intellij.openapi.application.WriteAction.run(() -> {
                        com.intellij.openapi.roots.ex.ProjectRootManagerEx.getInstanceEx(project)
                                .makeRootsChange(com.intellij.openapi.util.EmptyRunnable.INSTANCE, false, true);
                    });
                }
            }, com.intellij.openapi.application.ModalityState.nonModal());
        });
    }

    /**
     * Check if a file path is referenced by any .dpr file.
     */
    public boolean isReferencedFile(@NotNull String filePath) {
        return getReferencedFiles().contains(filePath);
    }

    private void scanDprFiles() {
        Map<String, List<String>> newCache = new HashMap<>();
        Set<String> newAllFiles = new HashSet<>();

        // Find all .dpr files by traversing VFS (avoid FilenameIndex to prevent circular dependency)
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir != null) {
            List<VirtualFile> dprFiles = new ArrayList<>();
            findDprFilesRecursively(baseDir, dprFiles, 10); // max depth 10

            LOG.info("[DprProjectService] Found " + dprFiles.size() + " .dpr files in project");

            for (VirtualFile dprFile : dprFiles) {
                LOG.info("[DprProjectService] Parsing: " + dprFile.getPath());

                List<String> referencedFiles = DprParser.parseReferencedFiles(dprFile);
                newCache.put(dprFile.getPath(), referencedFiles);
                newAllFiles.addAll(referencedFiles);

                LOG.info("[DprProjectService] Found " + referencedFiles.size() + " referenced files");
            }
        }

        // Also scan parent directories for .dpr files (for monorepo support)
        scanParentDirectoriesForDpr(newCache, newAllFiles);

        dprFileCache.clear();
        dprFileCache.putAll(newCache);
        allReferencedFiles = Collections.unmodifiableSet(newAllFiles);

        LOG.info("[DprProjectService] Total referenced files: " + allReferencedFiles.size());
    }

    /**
     * Recursively find .dpr files using VFS traversal.
     * This avoids using FilenameIndex which can cause circular dependencies.
     */
    private void findDprFilesRecursively(VirtualFile dir, List<VirtualFile> result, int maxDepth) {
        if (maxDepth <= 0 || dir == null || !dir.isValid() || !dir.isDirectory()) {
            return;
        }
        com.intellij.openapi.progress.ProgressManager.checkCanceled();

        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                // Skip common non-source directories
                String name = child.getName();
                if (!name.startsWith(".") && !name.equals("node_modules") && !name.equals("build") && !name.equals("out")) {
                    findDprFilesRecursively(child, result, maxDepth - 1);
                }
            } else if (DprParser.isDprFile(child)) {
                result.add(child);
            }
        }
    }

    /**
     * Scan parent directories (up to 5 levels) for .dpr files.
     * This helps with monorepo structures where .dpr might be outside the opened folder.
     */
    private void scanParentDirectoriesForDpr(Map<String, List<String>> cache, Set<String> allFiles) {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return;

        VirtualFile parent = baseDir.getParent();
        int levels = 0;

        while (parent != null && levels < 5) {
            // Look for .dpr files in this directory
            for (VirtualFile child : parent.getChildren()) {
                if (DprParser.isDprFile(child) && !cache.containsKey(child.getPath())) {
                    LOG.info("[DprProjectService] Found .dpr in parent: " + child.getPath());

                    List<String> referencedFiles = DprParser.parseReferencedFiles(child);
                    cache.put(child.getPath(), referencedFiles);
                    allFiles.addAll(referencedFiles);
                }
            }

            parent = parent.getParent();
            levels++;
        }
    }

    /**
     * Get the directories containing referenced files.
     * Useful for expanding the search scope.
     */
    @NotNull
    public Set<String> getReferencedDirectories() {
        Set<String> directories = new HashSet<>();
        for (String filePath : getReferencedFiles()) {
            int lastSlash = filePath.lastIndexOf('/');
            if (lastSlash == -1) {
                lastSlash = filePath.lastIndexOf('\\');
            }
            if (lastSlash > 0) {
                directories.add(filePath.substring(0, lastSlash));
            }
        }
        return directories;
    }

    @Override
    public void dispose() {
        dprFileCache.clear();
        allReferencedFiles = Collections.emptySet();
    }
}
