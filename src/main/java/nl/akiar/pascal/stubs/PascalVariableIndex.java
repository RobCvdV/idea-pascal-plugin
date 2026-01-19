package nl.akiar.pascal.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.dpr.DprProjectService;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Stub index for Pascal variable definitions.
 * Allows looking up variable definitions by name across the project.
 */
public class PascalVariableIndex extends StringStubIndexExtension<PascalVariableDefinition> {
    private static final Logger LOG = Logger.getInstance(PascalVariableIndex.class);

    public static final StubIndexKey<String, PascalVariableDefinition> KEY =
            StubIndexKey.createIndexKey("pascal.variable.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalVariableDefinition> getKey() {
        return KEY;
    }

    /**
     * Find all variable definitions with the given name (case-insensitive).
     */
    public static Collection<PascalVariableDefinition> findVariables(@NotNull String name, @NotNull Project project) {
        // First, search in project scope
        Collection<PascalVariableDefinition> results = StubIndex.getElements(
                KEY,
                name.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalVariableDefinition.class
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
                    PascalVariableDefinition.class
            );
        }

        return results;
    }

    /**
     * Find variables by name, filtered by kind.
     */
    public static Collection<PascalVariableDefinition> findVariablesByKind(
            @NotNull String name,
            @NotNull Project project,
            @NotNull VariableKind kind) {
        Collection<PascalVariableDefinition> all = findVariables(name, project);
        List<PascalVariableDefinition> filtered = new ArrayList<>();
        for (PascalVariableDefinition var : all) {
            if (var.getVariableKind() == kind) {
                filtered.add(var);
            }
        }
        return filtered;
    }

    /**
     * Find variables in a specific file.
     */
    public static Collection<PascalVariableDefinition> findVariablesInFile(
            @NotNull String name,
            @NotNull PsiFile file) {
        Collection<PascalVariableDefinition> all = findVariables(name, file.getProject());
        List<PascalVariableDefinition> filtered = new ArrayList<>();
        for (PascalVariableDefinition var : all) {
            if (file.equals(var.getContainingFile())) {
                filtered.add(var);
            }
        }
        return filtered;
    }

    /**
     * Find all global variables with the given name.
     */
    public static Collection<PascalVariableDefinition> findGlobalVariables(
            @NotNull String name,
            @NotNull Project project) {
        return findVariablesByKind(name, project, VariableKind.GLOBAL);
    }

    /**
     * Find variables with priority: same file first, then by scope.
     * This is the main lookup method that should be used for documentation and navigation.
     */
    public static Collection<PascalVariableDefinition> findVariablesWithScope(
            @NotNull String name,
            @NotNull PsiFile currentFile) {

        Collection<PascalVariableDefinition> allVariables = findVariables(name, currentFile.getProject());

        if (allVariables.isEmpty()) {
            return allVariables;
        }

        // Separate into same-file and other-file variables
        List<PascalVariableDefinition> sameFileVars = new ArrayList<>();
        List<PascalVariableDefinition> otherFileVars = new ArrayList<>();

        for (PascalVariableDefinition var : allVariables) {
            if (currentFile.equals(var.getContainingFile())) {
                sameFileVars.add(var);
            } else {
                otherFileVars.add(var);
            }
        }

        // If we found variables in the same file, return those first
        if (!sameFileVars.isEmpty()) {
            return sameFileVars;
        }

        // Otherwise, filter to only return variables from units in the uses clause
        // (TODO: implement uses clause checking for variables)
        return otherFileVars;
    }

    /**
     * Find variables in the same file only.
     */
    public static Collection<PascalVariableDefinition> findVariablesInSameFile(
            @NotNull String name,
            @NotNull PsiFile file) {

        Collection<PascalVariableDefinition> allVariables = findVariables(name, file.getProject());
        List<PascalVariableDefinition> result = new ArrayList<>();

        for (PascalVariableDefinition var : allVariables) {
            if (file.equals(var.getContainingFile())) {
                result.add(var);
            }
        }

        return result;
    }

    /**
     * Find a variable at a specific position, considering scope.
     * Prioritizes: local scope > class scope > unit scope > imported scope.
     */
    @Nullable
    public static PascalVariableDefinition findVariableAtPosition(
            @NotNull String name,
            @NotNull PsiFile file,
            int offset) {

        Collection<PascalVariableDefinition> candidates = findVariablesWithScope(name, file);

        if (candidates.isEmpty()) {
            return null;
        }

        // If only one match, return it
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // Multiple matches - try to find the most specific one based on position
        // Variables defined before the offset are candidates
        PascalVariableDefinition bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (PascalVariableDefinition var : candidates) {
            int varOffset = var.getTextOffset();
            // Variable must be defined before the reference (or in same scope)
            if (varOffset < offset) {
                int distance = offset - varOffset;
                // Prefer closer definitions (more local scope)
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = var;
                }
            }
        }

        // If no match before offset, return the first one
        return bestMatch != null ? bestMatch : candidates.iterator().next();
    }

    /**
     * Create a search scope containing files referenced by .dpr files.
     */
    @Nullable
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

        return GlobalSearchScope.filesScope(project, virtualFiles);
    }
}
