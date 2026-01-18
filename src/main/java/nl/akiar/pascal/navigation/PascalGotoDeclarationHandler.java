package nl.akiar.pascal.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handles Cmd+Click (Go to Declaration) for Pascal type references.
 * Navigates from type usages to their definitions, prioritizing types
 * from units that are in the uses clause.
 */
public class PascalGotoDeclarationHandler implements GotoDeclarationHandler {
    private static final Logger LOG = Logger.getInstance(PascalGotoDeclarationHandler.class);

    @Override
    @Nullable
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }

        // Only handle Pascal files
        if (sourceElement.getLanguage() != PascalLanguage.INSTANCE) {
            return null;
        }

        // Only handle identifiers
        if (sourceElement.getNode().getElementType() != PascalTokenTypes.IDENTIFIER) {
            return null;
        }

        // Handle unit references in uses clause
        PsiElement parent = sourceElement.getParent();
        if (parent != null && parent.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE) {
            nl.akiar.pascal.reference.PascalUnitReference ref = new nl.akiar.pascal.reference.PascalUnitReference(sourceElement);
            PsiElement resolved = ref.resolve();
            if (resolved != null) {
                LOG.info("[PascalNav]  -> Resolved to unit file: " + ((PsiFile)resolved).getName());
                return new PsiElement[]{resolved};
            }
        }

        String typeName = sourceElement.getText();
        LOG.info("[PascalNav] GotoDeclaration for: " + typeName);

        // Skip if this identifier IS a type definition name
        if (parent instanceof PascalTypeDefinition) {
            if (((PascalTypeDefinition) parent).getNameIdentifier() == sourceElement) {
                LOG.info("[PascalNav]  -> Skipping: this is the definition name itself");
                return null;
            }
        }

        // Look up the type with uses clause validation
        PsiFile file = sourceElement.getContainingFile();
        int elementOffset = sourceElement.getTextOffset();

        PascalTypeIndex.TypeLookupResult result =
                PascalTypeIndex.findTypesWithUsesValidation(typeName, file, elementOffset);

        // Prioritize in-scope types, but still allow navigation to out-of-scope types
        List<PascalTypeDefinition> inScope = result.getInScopeTypes();
        if (!inScope.isEmpty()) {
            LOG.info("[PascalNav]  -> Found " + inScope.size() + " in-scope type definition(s)");
            return inScope.toArray(new PsiElement[0]);
        }

        // Fall back to out-of-scope types (still navigable, just shows error)
        List<PascalTypeDefinition> outOfScope = result.getOutOfScopeTypes();
        if (!outOfScope.isEmpty()) {
            LOG.info("[PascalNav]  -> Found " + outOfScope.size() + " out-of-scope type definition(s) - unit not in uses");
            return outOfScope.toArray(new PsiElement[0]);
        }

        LOG.info("[PascalNav]  -> Type not found in index: " + typeName);
        return null;
    }

    @Override
    @Nullable
    public String getActionText(@NotNull DataContext context) {
        return null;
    }
}
