package nl.akiar.pascal.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
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
public class PascalProjectService implements Disposable {
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
        String path = unitToFileMap.get(lowerUnit);
        if (path != null) {
            return LocalFileSystem.getInstance().findFileByPath(path);
        }

        // 2. Try scope names if requested
        if (useScopeNames) {
            List<String> scopes = PascalSourcePathsSettings.getInstance(project).getUnitScopeNames();
            for (String scope : scopes) {
                String scopedName = (scope + "." + unitName).toLowerCase();
                path = unitToFileMap.get(scopedName);
                if (path != null) {
                    return LocalFileSystem.getInstance().findFileByPath(path);
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
                    LOG.info("[PascalProject] Adding DCCReference: " + normalizedRef + " (resolved to: " + refFile.getPath() + ")");
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
                    LOG.info("[PascalProject] Parsing optset file: " + optsetFile.getPath());
                    List<String> searchPaths = OptsetParser.parseSearchPaths(optsetFile);
                    LOG.info("[PascalProject] Found " + searchPaths.size() + " search paths in optset " + optsetFile.getName());
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
                            LOG.info("[PascalProject] Indexing search path from optset: " + normalizedPath + " (resolved to: " + searchDir.getPath() + ")");
                            collectPasFiles(searchDir, sourceFiles);
                        } else {
                            LOG.warn("[PascalProject] Could not find search path: " + sp + " (resolved to: " + normalizedPath + ") from " + optsetFile.getPath());
                        }
                    }
                }
            }
        }

        // 2. Settings source paths
        List<String> settingsPaths = PascalSourcePathsSettings.getInstance(project).getSourcePaths();
        for (String sp : settingsPaths) {
            VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(sp);
            if (dir != null && dir.isDirectory()) {
                LOG.info("[PascalProject] Indexing search path from settings: " + sp);
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
                if (child.getName().equalsIgnoreCase("UEoKeyBase.pas") || child.getName().equalsIgnoreCase("UEoBase.pas")) {
                    LOG.info("[PascalProject] DEBUG: collectPasFiles found " + child.getName() + " in " + dir.getPath());
                }
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
        if (file.getName().equalsIgnoreCase("UEoKeyBase.pas")) {
            LOG.info("[PascalProject] DEBUG: indexFile called for UEoKeyBase.pas at " + file.getPath());
        }
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(file.getPath()), StandardCharsets.UTF_8)) {
            String line;
            // Usually the unit name is on the first non-comment line
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("{") || line.startsWith("(*")) {
                    continue;
                }
                Matcher matcher = UNIT_NAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    String unitName = matcher.group(1).toLowerCase();
                    // LOG.info("[PascalProject] Indexed unit: " + unitName + " from " + file.getPath());
                    if (unitName.equalsIgnoreCase("ueobase") || unitName.equalsIgnoreCase("ueokeybase")) {
                        LOG.info("[PascalProject] DEBUG: Found unit " + unitName + " in file " + file.getPath());
                    }
                    unitToFileMap.put(unitName, file.getPath());
                    break;
                }
                // If we hit implementation or interface without unit name, it might not have one (program)
                if (line.toLowerCase().startsWith("interface") || line.toLowerCase().startsWith("implementation") || line.toLowerCase().startsWith("program")) {
                    break;
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to index unit name from " + file.getPath(), e);
        }
        
        // Fallback: use filename if no unit name found
        String fileName = file.getNameWithoutExtension().toLowerCase();
        if (!unitToFileMap.containsKey(fileName)) {
            // LOG.info("[PascalProject] Indexed unit (fallback to filename): " + fileName + " from " + file.getPath());
            if (fileName.equalsIgnoreCase("ueobase") || fileName.equalsIgnoreCase("ueokeybase")) {
                LOG.info("[PascalProject] DEBUG: Found unit (fallback) " + fileName + " in file " + file.getPath());
            }
            unitToFileMap.put(fileName, file.getPath());
        }
    }

    @Override
    public void dispose() {
        unitToFileMap.clear();
    }
}
