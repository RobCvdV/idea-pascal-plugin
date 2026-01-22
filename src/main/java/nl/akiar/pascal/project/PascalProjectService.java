package nl.akiar.pascal.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import nl.akiar.pascal.index.PascalUnitIndex;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-level service that manages Pascal project structure.
 * Handles .dproj and .optset discovery and unit resolution.
 */
@Service(Service.Level.PROJECT)
public final class PascalProjectService implements Disposable {
    private static final Logger LOG = Logger.getInstance(PascalProjectService.class);
    private final Project project;

    // Discovered source directories
    private final Set<String> discoveredDirectories = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean initialized = false;

    public PascalProjectService(@NotNull Project project) {
        this.project = project;
    }

    public static PascalProjectService getInstance(@NotNull Project project) {
        return project.getService(PascalProjectService.class);
    }

    /**
     * Resolve a unit name to a VirtualFile.
     */
    @Nullable
    public VirtualFile resolveUnit(@NotNull String unitName) {
        return resolveUnit(unitName, false);
    }

    /**
     * Resolve a unit name to a VirtualFile, optionally using unit scope names.
     */
    @Nullable
    public VirtualFile resolveUnit(@NotNull String unitName, boolean useScopeNames) {
        String lowerUnit = unitName.toLowerCase();
        
        // 1. Direct match
        VirtualFile file = findFileByUnitName(lowerUnit);
        if (file != null) return file;

        // 2. Try scope names if requested
        if (useScopeNames) {
            List<String> scopes = PascalSourcePathsSettings.getInstance(project).getUnitScopeNames();
            for (String scope : scopes) {
                String scopedName = (scope + "." + unitName).toLowerCase();
                file = findFileByUnitName(scopedName);
                if (file != null) return file;
            }
        }
        
        return null;
    }

    @Nullable
    private VirtualFile findFileByUnitName(@NotNull String unitName) {
        return com.intellij.openapi.application.ReadAction.compute(() -> {
            Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(
                    PascalUnitIndex.INDEX_ID,
                    unitName,
                    GlobalSearchScope.allScope(project)
            );
            return files.isEmpty() ? null : files.iterator().next();
        });
    }

    /**
     * Get all source directories discovered in the project.
     */
    @NotNull
    public Set<String> getDiscoveredDirectories() {
        if (!initialized) {
            rescan();
        }
        return Collections.unmodifiableSet(discoveredDirectories);
    }

    public void rescan() {
        if (project.isDisposed()) return;
        LOG.info("[PascalProject] Starting project rescan");
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            synchronized (this) {
                discoveredDirectories.clear();
                discoverSourceFiles();
                initialized = true;
            }
            LOG.info("[PascalProject] Rescan complete. Discovered " + discoveredDirectories.size() + " directories.");
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

    private void discoverSourceFiles() {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return;

        // 1. Find .dproj files
        List<VirtualFile> dprojFiles = new ArrayList<>();
        findFilesRecursively(baseDir, ".dproj", dprojFiles, 10);
        LOG.info("[PascalProject] Found " + dprojFiles.size() + " .dproj files.");

        for (VirtualFile dprojFile : dprojFiles) {
            LOG.info("[PascalProject] Parsing dproj: " + dprojFile.getPath());
            DprojParser.DprojInfo info = DprojParser.parse(dprojFile);
            VirtualFile dprojDir = dprojFile.getParent();

            // DCCReference
            for (String ref : info.references) {
                com.intellij.openapi.progress.ProgressManager.checkCanceled();
                // Handle both \ and /
                String normalizedRef = ref.replace('\\', '/');
                VirtualFile refFile = dprojDir.findFileByRelativePath(normalizedRef);
                if (refFile != null && refFile.isValid()) {
                    if (refFile.isDirectory()) {
                        discoveredDirectories.add(refFile.getPath());
                    } else {
                        if (refFile.getParent() != null) {
                            discoveredDirectories.add(refFile.getParent().getPath());
                        }
                    }
                } else {
                    LOG.warn("[PascalProject] Could not find DCCReference: " + ref + " relative to " + dprojFile.getPath());
                }
            }

            // Optsets
            LOG.info("[PascalProject] Processing " + info.optsets.size() + " optset references from dproj.");
            for (String optset : info.optsets) {
                com.intellij.openapi.progress.ProgressManager.checkCanceled();
                // Handle both \ and /
                String normalizedOptset = optset.replace('\\', '/');
                VirtualFile optsetFile = dprojDir.findFileByRelativePath(normalizedOptset);
                if (optsetFile != null && optsetFile.isValid()) {
                    List<String> searchPaths = OptsetParser.parseSearchPaths(optsetFile);
                    for (String sp : searchPaths) {
                        // sp can contain macros like $(PROJECTDIR)
                        String path = sp.replace("$(PROJECTDIR)", ".");
                        // Handle both \ and /
                        String normalizedPath = path.replace('\\', '/');
                        
                        VirtualFile searchDir;
                        if (normalizedPath.startsWith("/") || (normalizedPath.length() > 1 && normalizedPath.charAt(1) == ':')) {
                            // Absolute path (Unix-style or Windows-style)
                            searchDir = LocalFileSystem.getInstance().findFileByPath(normalizedPath);
                        } else {
                            // Relative path - relative to dproj location as clarified by user
                            searchDir = dprojDir.findFileByRelativePath(normalizedPath);
                        }

                        if (searchDir != null && searchDir.isDirectory()) {
                            discoveredDirectories.add(searchDir.getPath());
                        }
                    }
                }
            }
        }
    }

    private void findFilesRecursively(VirtualFile dir, String extension, List<VirtualFile> result, int depth) {
        if (depth <= 0 || dir == null || !dir.isValid() || !dir.isDirectory()) return;
        com.intellij.openapi.progress.ProgressManager.checkCanceled();
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                findFilesRecursively(child, extension, result, depth - 1);
            } else if (child.getName().toLowerCase().endsWith(extension)) {
                result.add(child);
            }
        }
    }

    @Override
    public void dispose() {
        discoveredDirectories.clear();
    }
}
