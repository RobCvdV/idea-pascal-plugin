package nl.akiar.pascal.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Project-level service that manages Pascal project structure.
 * Handles .dproj and .optset discovery and unit resolution.
 */
@Service(Service.Level.PROJECT)
public final class PascalProjectService implements Disposable {
    private static final Logger LOG = Logger.getInstance(PascalProjectService.class);
    private final Project project;

    // Unit Name -> File Path mapping
    private final Map<String, String> unitToFileMap = new ConcurrentHashMap<>();
    // Discovered source directories
    private final Set<String> discoveredDirectories = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean initialized = false;

    // Regex for 'unit UnitName;'
    private static final Pattern UNIT_NAME_PATTERN = Pattern.compile("^\\s*unit\\s+([\\w.]+)\\s*;", Pattern.CASE_INSENSITIVE);

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
        ensureInitialized();
        String lowerUnit = unitName.toLowerCase();
        
        // 1. Direct match
        String url = unitToFileMap.get(lowerUnit);
        if (url != null) {
            return com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(url);
        }

        // 2. Try scope names if requested
        if (useScopeNames) {
            List<String> scopes = PascalSourcePathsSettings.getInstance(project).getUnitScopeNames();
            for (String scope : scopes) {
                String scopedName = (scope + "." + unitName).toLowerCase();
                url = unitToFileMap.get(scopedName);
                if (url != null) {
                    return com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(url);
                }
            }
        }
        
        return null;
    }

    /**
     * Get all source directories discovered in the project.
     */
    @NotNull
    public Set<String> getDiscoveredDirectories() {
        ensureInitialized();
        return Collections.unmodifiableSet(discoveredDirectories);
    }

    public void rescan() {
        LOG.info("[PascalProject] Starting project rescan");
        synchronized (this) {
            unitToFileMap.clear();
            discoveredDirectories.clear();
            Set<VirtualFile> sourceFiles = discoverSourceFiles();
            for (VirtualFile file : sourceFiles) {
                indexFile(file);
            }
            initialized = true;
        }
        LOG.info("[PascalProject] Rescan complete. Indexed " + unitToFileMap.size() + " units. Discovered " + discoveredDirectories.size() + " directories.");
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    rescan();
                }
            }
        }
    }

    private Set<VirtualFile> discoverSourceFiles() {
        Set<VirtualFile> sourceFiles = new HashSet<>();
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return sourceFiles;

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
                // Handle both \ and /
                String normalizedRef = ref.replace('\\', '/');
                VirtualFile refFile = dprojDir.findFileByRelativePath(normalizedRef);
                if (refFile != null && refFile.isValid()) {
//                     LOG.info("[PascalProject] Adding DCCReference: " + normalizedRef + " (resolved to: " + refFile.getPath() + ")");
                    if (refFile.isDirectory()) {
                        collectPasFiles(refFile, sourceFiles);
                    } else {
                        sourceFiles.add(refFile);
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
                // Handle both \ and /
                String normalizedOptset = optset.replace('\\', '/');
                VirtualFile optsetFile = dprojDir.findFileByRelativePath(normalizedOptset);
                if (optsetFile != null && optsetFile.isValid()) {
//                     LOG.info("[PascalProject] Parsing optset file: " + optsetFile.getPath());
                    List<String> searchPaths = OptsetParser.parseSearchPaths(optsetFile);
//                     LOG.info("[PascalProject] Found " + searchPaths.size() + " search paths in optset " + optsetFile.getName());
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
//                             LOG.info("[PascalProject] Indexing search path from optset: " + normalizedPath + " (resolved to: " + searchDir.getPath() + ")");
                            collectPasFiles(searchDir, sourceFiles);
                        } else {
//                             LOG.warn("[PascalProject] Could not find search path: " + sp + " (resolved to: " + normalizedPath + ") from " + optsetFile.getPath());
                        }
                    }
                }
            }
        }

        // 2. Settings source paths
        List<String> settingsPaths = PascalSourcePathsSettings.getInstance(project).getSourcePaths();
        for (String sp : settingsPaths) {
            VirtualFile dir = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(sp);
            if (dir == null) {
                dir = LocalFileSystem.getInstance().findFileByPath(sp);
            }
            
            if (dir != null && dir.isDirectory()) {
                collectPasFiles(dir, sourceFiles);
            }
        }

        return sourceFiles;
    }

    private void collectPasFiles(VirtualFile dir, Set<VirtualFile> result) {
        if (dir == null || !dir.isValid() || !dir.isDirectory()) return;
        discoveredDirectories.add(dir.getPath());
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                collectPasFiles(child, result);
            } else if ("pas".equalsIgnoreCase(child.getExtension())) {
                result.add(child);
            }
        }
    }

    private void findFilesRecursively(VirtualFile dir, String extension, List<VirtualFile> result, int depth) {
        if (depth <= 0) return;
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                findFilesRecursively(child, extension, result, depth - 1);
            } else if (child.getName().toLowerCase().endsWith(extension)) {
                result.add(child);
            }
        }
    }

    private void indexFile(VirtualFile file) {
        if (!"pas".equalsIgnoreCase(file.getExtension())) {
            return;
        }
        try {
            // Using VirtualFile.contentsToByteArray() for better integration with test file systems
            byte[] bytes = file.contentsToByteArray();
            // Try UTF-8 first
            String content;
            try {
                content = new String(bytes, StandardCharsets.UTF_8);
                // If it contains replacement chars, check if it's really UTF-8
                if (content.contains("\uFFFD")) {
                     // Potential decoding error, try Latin-1
                     content = new String(bytes, StandardCharsets.ISO_8859_1);
                }
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                // Fallback to ISO-8859-1 (Latin1) which accepts all byte sequences
                content = new String(bytes, StandardCharsets.ISO_8859_1);
            }

            String[] lines = content.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("{") || line.startsWith("(*")) {
                    continue;
                }
                Matcher matcher = UNIT_NAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    String unitName = matcher.group(1).toLowerCase();
                    unitToFileMap.put(unitName, file.getUrl());
                    break;
                }
                // If we hit implementation or interface without unit name, it might not have one (program)
                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith("interface") || lowerLine.startsWith("implementation") || lowerLine.startsWith("program")) {
                    break;
                }
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Failed to index unit name from " + file.getPath(), e);
        }
        
        // Fallback: use filename if no unit name found
        String fileName = file.getNameWithoutExtension().toLowerCase();
        if (!unitToFileMap.containsKey(fileName)) {
            // LOG.info("[PascalProject] Indexed unit (fallback to filename): " + fileName + " from " + file.getPath());
            unitToFileMap.put(fileName, file.getUrl());
        }
    }

    @Override
    public void dispose() {
        unitToFileMap.clear();
    }
}
