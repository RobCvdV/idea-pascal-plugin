package nl.akiar.pascal.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import nl.akiar.pascal.uses.PascalUsesClauseUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PascalPropertyIndex extends StringStubIndexExtension<PascalProperty> {
    public static final StubIndexKey<String, PascalProperty> KEY =
            StubIndexKey.createIndexKey("pascal.property.index");

    @Override
    @NotNull
    public StubIndexKey<String, PascalProperty> getKey() {
        return KEY;
    }

    public static Collection<PascalProperty> findProperties(@NotNull String name, @NotNull Project project) {
        return StubIndex.getElements(
                KEY,
                name.toLowerCase(),
                project,
                GlobalSearchScope.allScope(project),
                PascalProperty.class
        );
    }

    @NotNull
    public static PropertyLookupResult findPropertiesWithUsesValidation(
            @NotNull String name,
            @NotNull PsiFile fromFile,
            int offset) {

        Collection<PascalProperty> allProperties = findProperties(name, fromFile.getProject());
        PascalUsesClauseUtil.UsesClauseInfo usesInfo = PascalUsesClauseUtil.parseUsesClause(fromFile);

        List<PascalProperty> inScope = new ArrayList<>();
        List<PascalProperty> outOfScope = new ArrayList<>();

        List<String> scopes = PascalSourcePathsSettings.getInstance(fromFile.getProject()).getUnitScopeNames();

        for (PascalProperty prop : allProperties) {
            String targetUnit = prop.getUnitName();
            PsiFile targetFile = prop.getContainingFile();
            if (targetFile == null) continue;

            if (targetFile.equals(fromFile)) {
                inScope.add(prop);
                continue;
            }

            if (usesInfo.findUnitInUses(targetUnit, offset, scopes) != null) {
                inScope.add(prop);
            } else {
                outOfScope.add(prop);
            }
        }

        return new PropertyLookupResult(inScope, outOfScope, usesInfo, offset);
    }

    public static class PropertyLookupResult {
        private final List<PascalProperty> inScopeProperties;
        private final List<PascalProperty> outOfScopeProperties;
        private final PascalUsesClauseUtil.UsesClauseInfo usesInfo;
        private final int referenceOffset;

        public PropertyLookupResult(List<PascalProperty> inScope, List<PascalProperty> outOfScope,
                                    PascalUsesClauseUtil.UsesClauseInfo usesInfo, int referenceOffset) {
            this.inScopeProperties = inScope;
            this.outOfScopeProperties = outOfScope;
            this.usesInfo = usesInfo;
            this.referenceOffset = referenceOffset;
        }

        public List<PascalProperty> getInScopeProperties() {
            return inScopeProperties;
        }

        public List<PascalProperty> getOutOfScopeProperties() {
            return outOfScopeProperties;
        }

        public boolean isEmpty() {
            return inScopeProperties.isEmpty() && outOfScopeProperties.isEmpty();
        }

        @Nullable
        public String getErrorMessage() {
            java.util.Set<String> inScopeUnits = new java.util.LinkedHashSet<>();
            for (PascalProperty prop : inScopeProperties) {
                inScopeUnits.add(prop.getUnitName());
            }

            if (inScopeUnits.size() <= 1 && !inScopeProperties.isEmpty()) {
                return null;
            }

            if (inScopeUnits.size() > 1) {
                return "Ambiguous reference. Found in multiple units: " + String.join(", ", inScopeUnits);
            }

            if (outOfScopeProperties.isEmpty()) {
                return null;
            }

            java.util.Set<String> outOfScopeUnits = new java.util.LinkedHashSet<>();
            for (PascalProperty prop : outOfScopeProperties) {
                outOfScopeUnits.add(prop.getUnitName());
            }

            if (outOfScopeUnits.size() > 1) {
                return "Ambiguous reference found in multiple units (none in uses clause): " + String.join(", ", outOfScopeUnits);
            }

            String unitName = outOfScopeUnits.iterator().next();
            if (usesInfo.isInInterfaceSection(referenceOffset)) {
                if (usesInfo.getImplementationUses().contains(unitName.toLowerCase())) {
                    return "Unit '" + unitName + "' is in implementation uses, but property is referenced in interface section. Add it to interface uses.";
                }
                return "Unit '" + unitName + "' is not in uses clause. Add it to interface uses.";
            } else {
                return "Unit '" + unitName + "' is not in uses clause. Add it to uses clause.";
            }
        }
    }
}
