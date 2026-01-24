package nl.akiar.pascal.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.reference.PascalMemberReference;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.stubs.PascalVariableIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;
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

    public PascalGotoDeclarationHandler() {
        System.out.println("[DEBUG_LOG] [PascalNav] PascalGotoDeclarationHandler instantiated");
    }

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

        PsiElement parent = sourceElement.getParent();

        // Handle unit references in uses clause
        if (parent != null && parent.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE) {
            nl.akiar.pascal.reference.PascalUnitReference ref = new nl.akiar.pascal.reference.PascalUnitReference(sourceElement);
            PsiElement resolved = ref.resolve();
            if (resolved != null) {
                LOG.info("[PascalNav]  -> Resolved to unit file: " + ((PsiFile)resolved).getName());
                return new PsiElement[]{resolved};
            }
        }

        // Try to resolve using references first (handles Member access, unit references, etc)
        com.intellij.psi.PsiReference[] refs = sourceElement.getReferences();
        if (refs.length > 0) {
            for (com.intellij.psi.PsiReference ref : refs) {
                PsiElement resolved = ref.resolve();
                if (resolved != null) {
                    LOG.info("[PascalNav]  -> Resolved via reference to: " + resolved);
                    return new PsiElement[]{resolved};
                }
            }
        }

        // Only handle identifiers
        if (sourceElement.getNode().getElementType() != PascalTokenTypes.IDENTIFIER) {
            return null;
        }

        // Fallback for member access if ReferenceContributor didn't kick in
        PsiElement leaf = PsiTreeUtil.prevLeaf(sourceElement);
        while (leaf != null && leaf instanceof com.intellij.psi.PsiWhiteSpace) {
            leaf = PsiTreeUtil.prevLeaf(leaf);
        }
        if (leaf != null && leaf.getNode().getElementType() == PascalTokenTypes.DOT) {
            LOG.info("[PascalNav] Detected dot before identifier, attempting manual member resolution");
            PascalMemberReference memberRef = new PascalMemberReference(sourceElement, new TextRange(0, sourceElement.getTextLength()));
            PsiElement resolved = memberRef.resolve();
            if (resolved != null) {
                return new PsiElement[]{resolved};
            }
        }
        
        // Handle routine declaration/implementation navigation
        if (parent instanceof PascalRoutine) {
            PascalRoutine routine = (PascalRoutine) parent;
            if (routine.getNameIdentifier() == sourceElement) {
                if (routine.isImplementation()) {
                    PascalRoutine decl = routine.getDeclaration();
                    if (decl != null) return new PsiElement[]{decl};
                } else {
                    PascalRoutine impl = routine.getImplementation();
                    if (impl != null) return new PsiElement[]{impl};
                }
            }
        }

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
                LOG.info("[PascalNav]  -> Skipping: this is the type definition name itself");
                return null;
            }
        }

        // Skip if this identifier IS a variable definition name
        if (parent instanceof PascalVariableDefinition) {
            if (((PascalVariableDefinition) parent).getNameIdentifier() == sourceElement) {
                LOG.info("[PascalNav]  -> Skipping: this is the variable definition name itself");
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

        // Also try looking up as a variable - use scoped lookup
        String varName = sourceElement.getText();
        PascalVariableDefinition var = PascalVariableIndex.findVariableAtPosition(varName, file, offset);
        if (var != null) {
            LOG.info("[PascalNav]  -> Found variable definition: " + var.getName() + " (" + var.getVariableKind() + ") in " + var.getContainingFile().getName());
            return new PsiElement[]{var};
        }

        LOG.info("[PascalNav]  -> Not found in index: " + typeName);
        return null;
    }

    @Override
    @Nullable
    public String getActionText(@NotNull DataContext context) {
        return null;
    }
}
