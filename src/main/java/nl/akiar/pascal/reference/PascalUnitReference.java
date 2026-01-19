package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import nl.akiar.pascal.project.PascalProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference to a Pascal unit file from a uses clause.
 */
public class PascalUnitReference extends PsiReferenceBase<PsiElement> {
    private static final Logger LOG = Logger.getInstance(PascalUnitReference.class);
    private final String unitName;

    public PascalUnitReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()));
        if (element.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE) {
            this.unitName = element.getText();
        } else if (element.getParent() != null && element.getParent().getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE) {
            this.unitName = element.getParent().getText();
        } else {
            this.unitName = element.getText();
        }
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        Project project = myElement.getProject();
//         LOG.info("[PascalNav] Resolving unit reference: " + unitName);

        // 1. Direct resolution
        VirtualFile unitFile = PascalProjectService.getInstance(project).resolveUnit(unitName, false);
        if (unitFile != null) {
            return PsiManager.getInstance(project).findFile(unitFile);
        }

        // 2. Resolution via scope names
        unitFile = PascalProjectService.getInstance(project).resolveUnit(unitName, true);
        if (unitFile != null) {
            return PsiManager.getInstance(project).findFile(unitFile);
        }

        return null;
    }

    /**
     * Check if the unit is resolved via unit scope names.
     */
    public boolean isResolvedViaScopeNames() {
        Project project = myElement.getProject();
        // If it resolves directly, it's not via scope names
        if (PascalProjectService.getInstance(project).resolveUnit(unitName, false) != null) {
            return false;
        }
        // If it doesn't resolve directly but resolves with scope names, then it is via scope names
        return PascalProjectService.getInstance(project).resolveUnit(unitName, true) != null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
