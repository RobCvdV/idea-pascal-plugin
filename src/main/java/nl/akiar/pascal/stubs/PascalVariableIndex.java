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
     * Find variables prioritizing types from units in the uses clause.
     */
    @NotNull
    public static VariableLookupResult findVariablesWithUsesValidation(
            @NotNull String name,
            @NotNull PsiFile fromFile,
            int offset) {

        Collection<PascalVariableDefinition> candidates = findVariablesWithScope(name, fromFile);
        nl.akiar.pascal.uses.PascalUsesClauseUtil.UsesClauseInfo usesInfo =
                nl.akiar.pascal.uses.PascalUsesClauseUtil.parseUsesClause(fromFile);
        List<String> scopes = nl.akiar.pascal.settings.PascalSourcePathsSettings.getInstance(fromFile.getProject()).getUnitScopeNames();

        List<PascalVariableDefinition> inScope = new ArrayList<>();
        List<PascalVariableDefinition> outOfScope = new ArrayList<>();

        for (PascalVariableDefinition var : candidates) {
            VariableKind kind = var.getVariableKind();
            if (kind == VariableKind.GLOBAL || kind == VariableKind.CONSTANT || kind == VariableKind.THREADVAR) {
                if (fromFile.equals(var.getContainingFile())) {
                    inScope.add(var);
                } else {
                    String targetUnit = var.getUnitName();
                    if (usesInfo.findUnitInUses(targetUnit, offset, scopes) != null) {
                        inScope.add(var);
                    } else {
                        outOfScope.add(var);
                    }
                }
            } else {
                // Local/Field are always "in scope" if they are candidates
                inScope.add(var);
            }
        }

        return new VariableLookupResult(inScope, outOfScope, usesInfo, offset);
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

        VariableLookupResult result = findVariablesWithUsesValidation(name, file, offset);
        List<PascalVariableDefinition> inScope = result.getInScopeVariables();
        
        if (inScope.isEmpty()) {
            return null;
        }

        // 1. Try to find the one that actually contains this offset (declaration)
        for (PascalVariableDefinition var : inScope) {
            if (var.getTextRange().contains(offset)) {
                return var;
            }
        }

        // 2. Find the most specific one based on scope
        PsiElement elementAtOffset = file.findElementAt(offset);
        nl.akiar.pascal.psi.PascalRoutine containingRoutine =
                com.intellij.psi.util.PsiTreeUtil.getParentOfType(elementAtOffset, nl.akiar.pascal.psi.PascalRoutine.class);
        nl.akiar.pascal.psi.PascalTypeDefinition containingClass =
                com.intellij.psi.util.PsiTreeUtil.getParentOfType(elementAtOffset, nl.akiar.pascal.psi.PascalTypeDefinition.class);

        PascalVariableDefinition bestLocalMatch = null;
        int bestLocalDistance = Integer.MAX_VALUE;
        PascalVariableDefinition bestFieldMatch = null;
        int bestFieldDistance = Integer.MAX_VALUE;
        PascalVariableDefinition bestGlobalMatch = null;
        int bestGlobalDistance = Integer.MAX_VALUE;

        for (PascalVariableDefinition var : inScope) {
            int varOffset = var.getTextOffset();
            // Defined after the offset - only allowed for fields or if in different file
            if (varOffset > offset && file.equals(var.getContainingFile())) {
                VariableKind kind = var.getVariableKind();
                if (kind != VariableKind.FIELD) continue;
            }

            int distance = Math.abs(offset - varOffset);
            VariableKind kind = var.getVariableKind();

            if (kind == VariableKind.LOCAL || kind == VariableKind.PARAMETER || kind == VariableKind.LOOP_VAR) {
                nl.akiar.pascal.psi.PascalRoutine varRoutine =
                        com.intellij.psi.util.PsiTreeUtil.getParentOfType(var, nl.akiar.pascal.psi.PascalRoutine.class);
                if (varRoutine != null && varRoutine.equals(containingRoutine)) {
                    if (distance < bestLocalDistance) {
                        bestLocalDistance = distance;
                        bestLocalMatch = var;
                    }
                }
            } else if (kind == VariableKind.FIELD) {
                nl.akiar.pascal.psi.PascalTypeDefinition varClass =
                        com.intellij.psi.util.PsiTreeUtil.getParentOfType(var, nl.akiar.pascal.psi.PascalTypeDefinition.class);
                if (varClass != null && varClass.equals(containingClass)) {
                    if (distance < bestFieldDistance) {
                        bestFieldDistance = distance;
                        bestFieldMatch = var;
                    }
                }
            } else {
                if (distance < bestGlobalDistance) {
                     bestGlobalDistance = distance;
                     bestGlobalMatch = var;
                }
            }
        }

        if (bestLocalMatch != null) return bestLocalMatch;
        if (bestFieldMatch != null) return bestFieldMatch;
        if (bestGlobalMatch != null) return bestGlobalMatch;

        return inScope.get(0);
    }

    /**
     * Result of variable lookup with uses clause validation.
     */
    public static class VariableLookupResult {
        private final List<PascalVariableDefinition> inScopeVariables;
        private final List<PascalVariableDefinition> outOfScopeVariables;
        private final nl.akiar.pascal.uses.PascalUsesClauseUtil.UsesClauseInfo usesInfo;
        private final int referenceOffset;

        public VariableLookupResult(List<PascalVariableDefinition> inScope, List<PascalVariableDefinition> outOfScope,
                                   nl.akiar.pascal.uses.PascalUsesClauseUtil.UsesClauseInfo usesInfo, int referenceOffset) {
            this.inScopeVariables = inScope;
            this.outOfScopeVariables = outOfScope;
            this.usesInfo = usesInfo;
            this.referenceOffset = referenceOffset;
        }

        public List<PascalVariableDefinition> getInScopeVariables() {
            return inScopeVariables;
        }

        public List<PascalVariableDefinition> getOutOfScopeVariables() {
            return outOfScopeVariables;
        }

        @Nullable
        public String getErrorMessage() {
            Set<String> inScopeUnits = new java.util.LinkedHashSet<>();
            for (PascalVariableDefinition var : inScopeVariables) {
                inScopeUnits.add(var.getUnitName());
            }

            if (inScopeUnits.size() <= 1 && !inScopeVariables.isEmpty()) {
                return null;
            }

            if (inScopeUnits.size() > 1) {
                return "Ambiguous reference. Found in multiple units: " + String.join(", ", inScopeUnits);
            }

            if (outOfScopeVariables.isEmpty()) {
                return null;
            }

            Set<String> outOfScopeUnits = new java.util.LinkedHashSet<>();
            for (PascalVariableDefinition var : outOfScopeVariables) {
                outOfScopeUnits.add(var.getUnitName());
            }

            if (outOfScopeUnits.size() > 1) {
                return "Ambiguous reference found in multiple units (none in uses clause): " + String.join(", ", outOfScopeUnits);
            }

            String unitName = outOfScopeUnits.iterator().next();
            if (usesInfo.isInInterfaceSection(referenceOffset)) {
                if (usesInfo.getImplementationUses().contains(unitName.toLowerCase())) {
                    return "Unit '" + unitName + "' is in implementation uses, but variable is referenced in interface section. Add it to interface uses.";
                }
                return "Unit '" + unitName + "' is not in uses clause. Add it to interface uses.";
            } else {
                return "Unit '" + unitName + "' is not in uses clause. Add it to uses clause.";
            }
        }
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
