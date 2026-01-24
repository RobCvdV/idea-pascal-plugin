package nl.akiar.pascal.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.dpr.DprProjectService;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import nl.akiar.pascal.uses.PascalUsesClauseUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Stub index for Pascal type definitions.
 * Allows looking up type definitions by name across the project.
 */
public class PascalTypeIndex extends StringStubIndexExtension<PascalTypeDefinition> {
    private static final Logger LOG = Logger.getInstance(PascalTypeIndex.class);

    public static final StubIndexKey<String, PascalTypeDefinition> KEY =
            StubIndexKey.createIndexKey("pascal.type.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalTypeDefinition> getKey() {
        return KEY;
    }

    /**
     * Find all type definitions with the given name (case-insensitive).
     * Searches both the project scope and files referenced by .dpr files.
     */
    public static Collection<PascalTypeDefinition> findTypes(@NotNull String name, @NotNull Project project) {
        // First, search in project scope
        Collection<PascalTypeDefinition> results = StubIndex.getElements(
                KEY,
                name.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalTypeDefinition.class
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
                    PascalTypeDefinition.class
            );
        }

        return results;
    }

    /**
     * Find type definitions, prioritizing types from units in the uses clause.
     * Returns types that are in scope first, followed by types that are out of scope.
     *
     * @param name The type name to search for
     * @param fromFile The file containing the reference (for uses clause analysis)
     * @param offset The offset of the reference in fromFile
     * @return TypeLookupResult with in-scope and out-of-scope types
     */
    @NotNull
    public static TypeLookupResult findTypesWithUsesValidation(
            @NotNull String name,
            @NotNull PsiFile fromFile,
            int offset) {

        Collection<PascalTypeDefinition> allTypes = findTypes(name, fromFile.getProject());
        PascalUsesClauseUtil.UsesClauseInfo usesInfo = PascalUsesClauseUtil.parseUsesClause(fromFile);

        List<PascalTypeDefinition> inScope = new ArrayList<>();
        List<PascalTypeDefinition> outOfScope = new ArrayList<>();
        List<PascalTypeDefinition> viaScopeNames = new ArrayList<>();

        List<String> scopes = PascalSourcePathsSettings.getInstance(fromFile.getProject()).getUnitScopeNames();

        for (PascalTypeDefinition typeDef : allTypes) {
            String targetUnit = typeDef.getUnitName();
            PsiFile targetFile = typeDef.getContainingFile();
            if (targetFile == null) continue;

            // Same file is always in scope
            if (targetFile.equals(fromFile)) {
                inScope.add(typeDef);
                continue;
            }

            String unitInUses = usesInfo.findUnitInUses(targetUnit, offset, scopes);
            if (unitInUses != null) {
                inScope.add(typeDef);
                if (!unitInUses.equalsIgnoreCase(targetUnit)) {
                    viaScopeNames.add(typeDef);
                }
            } else {
                outOfScope.add(typeDef);
            }
        }

        return new TypeLookupResult(inScope, outOfScope, viaScopeNames, usesInfo, offset, scopes);
    }

    /**
     * Result of type lookup with uses clause validation.
     */
    public static class TypeLookupResult {
        private final List<PascalTypeDefinition> inScopeTypes;
        private final List<PascalTypeDefinition> outOfScopeTypes;
        private final List<PascalTypeDefinition> inScopeViaScopeNames; // New field
        private final PascalUsesClauseUtil.UsesClauseInfo usesInfo;
        private final int referenceOffset;
        private final List<String> scopes;

        public TypeLookupResult(List<PascalTypeDefinition> inScope, List<PascalTypeDefinition> outOfScope,
                               PascalUsesClauseUtil.UsesClauseInfo usesInfo, int referenceOffset) {
            this(inScope, outOfScope, new ArrayList<>(), usesInfo, referenceOffset, new ArrayList<>());
        }

        public TypeLookupResult(List<PascalTypeDefinition> inScope, List<PascalTypeDefinition> outOfScope,
                                List<PascalTypeDefinition> viaScopeNames,
                                PascalUsesClauseUtil.UsesClauseInfo usesInfo, int referenceOffset,
                                List<String> scopes) {
            this.inScopeTypes = inScope;
            this.outOfScopeTypes = outOfScope;
            this.inScopeViaScopeNames = viaScopeNames;
            this.usesInfo = usesInfo;
            this.referenceOffset = referenceOffset;
            this.scopes = scopes;
        }

        /** Types that are properly in scope (unit is in uses clause) */
        public List<PascalTypeDefinition> getInScopeTypes() {
            return inScopeTypes;
        }

        /** Types found in scope but using a short unit name resolved via unit scope names */
        public List<PascalTypeDefinition> getInScopeViaScopeNames() {
            return inScopeViaScopeNames;
        }

        /** Types that exist but their unit is not in the uses clause */
        public List<PascalTypeDefinition> getOutOfScopeTypes() {
            return outOfScopeTypes;
        }

        /** All found types (in-scope first, then out-of-scope) */
        public List<PascalTypeDefinition> getAllTypes() {
            List<PascalTypeDefinition> all = new ArrayList<>(inScopeTypes);
            all.addAll(outOfScopeTypes);
            return all;
        }

        /** True if the type exists but is not in scope */
        public boolean hasOutOfScopeTypes() {
            return !outOfScopeTypes.isEmpty();
        }

        /** True if no types were found at all */
        public boolean isEmpty() {
            return inScopeTypes.isEmpty() && outOfScopeTypes.isEmpty();
        }

        /** Get error message for out-of-scope reference, or null if OK */
        @Nullable
        public String getErrorMessage() {
            java.util.Set<String> inScopeUnits = new java.util.LinkedHashSet<>();
            for (PascalTypeDefinition type : inScopeTypes) {
                inScopeUnits.add(type.getUnitName());
            }

            if (inScopeUnits.size() <= 1 && inScopeTypes.size() >= 1) {
                return null; // Unique or unique across units
            }

            if (inScopeUnits.size() > 1) {
                // Ambiguous reference
                return "Ambiguous reference. Found in multiple units: " + String.join(", ", inScopeUnits);
            }

            if (outOfScopeTypes.isEmpty()) {
                return null; // Not found at all (handled by reference resolver usually)
            }

            java.util.Set<String> outOfScopeUnits = new java.util.LinkedHashSet<>();
            for (PascalTypeDefinition type : outOfScopeTypes) {
                outOfScopeUnits.add(type.getUnitName());
            }

            // Type exists but not in scope
            if (outOfScopeUnits.size() > 1) {
                return "Ambiguous reference found in multiple units (none in uses clause): " + String.join(", ", outOfScopeUnits);
            }

            String unitName = outOfScopeUnits.iterator().next();

            if (usesInfo.isInInterfaceSection(referenceOffset)) {
                if (usesInfo.getImplementationUses().contains(unitName.toLowerCase())) {
                    return "Unit '" + unitName + "' is in implementation uses, but type is referenced in interface section. Add it to interface uses.";
                }
                return "Unit '" + unitName + "' is not in uses clause. Add it to interface uses.";
            } else {
                return "Unit '" + unitName + "' is not in uses clause. Add it to uses clause.";
            }
        }

        /** Get the unit name that needs to be added to uses clause */
        @Nullable
        public String getMissingUnit() {
            if (outOfScopeTypes.isEmpty() || outOfScopeTypes.size() > 1) return null;
            return outOfScopeTypes.get(0).getUnitName();
        }
    }

    /**
     * Create a search scope containing files referenced by .dpr files.
     * Returns null if no DPR files found or no files referenced.
     */
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
