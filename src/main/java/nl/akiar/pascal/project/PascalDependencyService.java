package nl.akiar.pascal.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import nl.akiar.pascal.dpr.DprProjectService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that tracks which Pascal files are "active" in the current project.
 * Only active files are stub-indexed, saving significant resources on large projects.
 * Files are active if they are in the project content, referenced by .dpr/.dproj,
 * or transitively used by an active file.
 */
@Service(Service.Level.PROJECT)
public final class PascalDependencyService implements Disposable {
    private static final Logger LOG = Logger.getInstance(PascalDependencyService.class);

    private final Project project;
    private final Set<VirtualFile> activeFiles = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<VirtualFile> processedFiles = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean initialized = false;

    // Pattern to find uses clause in .pas files
    private static final Pattern USES_PATTERN = Pattern.compile("\\buses\\b(.*?);", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public PascalDependencyService(@NotNull Project project) {
        this.project = project;
    }

    public static PascalDependencyService getInstance(@NotNull Project project) {
        return project.getService(PascalDependencyService.class);
    }

    /**
     * Checks if a file should be considered active in this project.
     */
    public boolean isActive(@NotNull VirtualFile file) {
        if (!initialized) {
            initializeAsync();
        }

        // 1. Files in project content roots are always active
        if (ProjectUtil.isProjectOrWorkspaceFile(file)) return true;
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir != null && com.intellij.openapi.vfs.VfsUtilCore.isAncestor(projectDir, file, false)) {
            return true;
        }

        // 2. Explicitly marked active files (dependencies or open files)
        return activeFiles.contains(file);
    }

    /**
     * Mark a file as active (e.g. because it was opened in the editor).
     */
    public void markActive(@NotNull VirtualFile file) {
        if (file.isValid() && !isActive(file)) {
            LOG.info("[PascalDependency] Marking file as active: " + file.getName());
            activeFiles.add(file);
            triggerScan();
        }
    }

    private void initializeAsync() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            initialized = true;
        }
        triggerScan();
    }

    private void triggerScan() {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            if (!project.isDisposed()) {
                scanDependencies();
            }
        }, 1, TimeUnit.SECONDS);
    }

    private synchronized void scanDependencies() {
        if (project.isDisposed()) return;
        com.intellij.openapi.project.DumbService.getInstance(project).runReadActionInSmartMode(() -> {
            if (project.isDisposed()) return;
            LOG.info("[PascalDependency] Starting dependency scan");

            Set<VirtualFile> toProcess = new HashSet<>();
            
            // 1. Add all project files
            VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
            if (projectDir != null) {
                collectProjectFiles(projectDir, toProcess);
            }

            // 2. Add files from DprProjectService
            DprProjectService dprService = DprProjectService.getInstance(project);
            for (String filePath : dprService.getReferencedFiles()) {
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
                if (file != null) toProcess.add(file);
            }

            // 3. Add files from PascalProjectService (.dproj)
            // PascalProjectService doesn't expose all files directly, but we can get them from discovered directories
            // actually PascalProjectService should be updated to expose referenced files, but for now we'll 
            // focus on transitively crawling from what we have.

            // 4. Also add currently open files
            com.intellij.openapi.fileEditor.FileEditorManager fem = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project);
            for (VirtualFile openFile : fem.getOpenFiles()) {
                if ("pas".equalsIgnoreCase(openFile.getExtension())) {
                    toProcess.add(openFile);
                }
            }

            // Add everything we've collected so far to active set
            boolean changed = activeFiles.addAll(toProcess);

            // 5. Transitive crawl
            Queue<VirtualFile> queue = new LinkedList<>(toProcess);
            Set<VirtualFile> newActive = new HashSet<>();

            while (!queue.isEmpty()) {
                ProgressManager.checkCanceled();
                VirtualFile file = queue.poll();
                if (processedFiles.contains(file)) continue;
                processedFiles.add(file);

                List<String> usedUnits = extractUsedUnits(file);
                for (String unitName : usedUnits) {
                    VirtualFile resolved = PascalProjectService.getInstance(project).resolveUnit(unitName, true);
                    if (resolved != null && !activeFiles.contains(resolved)) {
                        newActive.add(resolved);
                        queue.add(resolved);
                        changed = true;
                    }
                }
            }

            if (changed) {
                activeFiles.addAll(newActive);
                LOG.info("[PascalDependency] Scan complete. Total active files: " + activeFiles.size());
                // Trigger re-index of newly active files
                if (!newActive.isEmpty()) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                        if (!project.isDisposed()) {
                            com.intellij.util.FileContentUtilCore.reparseFiles(newActive);
                        }
                    }, com.intellij.openapi.application.ModalityState.nonModal());
                }
            }
        });
    }

    private void collectProjectFiles(VirtualFile dir, Set<VirtualFile> result) {
        if (!dir.isDirectory()) return;
        for (VirtualFile child : dir.getChildren()) {
            ProgressManager.checkCanceled();
            if (child.isDirectory()) {
                String name = child.getName();
                if (!name.startsWith(".") && !name.equals("node_modules") && !name.equals("build") && !name.equals("out")) {
                    collectProjectFiles(child, result);
                }
            } else if ("pas".equalsIgnoreCase(child.getExtension()) || "dpr".equalsIgnoreCase(child.getExtension())) {
                result.add(child);
            }
        }
    }

    private List<String> extractUsedUnits(VirtualFile file) {
        if (!file.isValid() || file.isDirectory()) return Collections.emptyList();
        
        try (java.io.InputStream is = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(file.getPath()))) {
            byte[] buffer = new byte[32768];
            int read = is.read(buffer);
            if (read <= 0) return Collections.emptyList();
            String text = new String(buffer, 0, read, StandardCharsets.UTF_8);
            
            // Strip comments to avoid false matches
            text = text.replaceAll("//.*", "");
            text = text.replaceAll("\\{.*?\\}", "");
            text = text.replaceAll("\\(\\*.*?\\*\\)", "");

            List<String> units = new ArrayList<>();
            Matcher usesMatcher = USES_PATTERN.matcher(text);
            while (usesMatcher.find()) {
                String usesContent = usesMatcher.group(1);
                // Simple comma separated units, handling "UnitName in 'path'"
                String[] parts = usesContent.split(",");
                for (String part : parts) {
                    String unit = part.trim();
                    // Handle "UnitName in '...'"
                    int inIdx = unit.toLowerCase().indexOf(" in ");
                    if (inIdx > 0) {
                        unit = unit.substring(0, inIdx).trim();
                    }
                    // Handle trailing comments that might have survived or semicolon
                    unit = unit.split("\\s+")[0]; // Take first word
                    
                    if (!unit.isEmpty() && !isReservedWord(unit)) {
                        units.add(unit);
                    }
                }
            }
            return units;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private boolean isReservedWord(String word) {
        String lower = word.toLowerCase();
        return "interface".equals(lower) || "implementation".equals(lower) || "program".equals(lower) || "library".equals(lower);
    }

    @Override
    public void dispose() {
        activeFiles.clear();
        processedFiles.clear();
    }
}
