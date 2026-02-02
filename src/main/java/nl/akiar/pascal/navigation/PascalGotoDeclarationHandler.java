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
import com.intellij.openapi.util.Key;
import nl.akiar.pascal.resolution.MemberChainResolver;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import nl.akiar.pascal.psi.PsiUtil;

/**
 * Handles Cmd+Click (Go to Declaration) for Pascal type references.
 * Navigates from type usages to their definitions, prioritizing types
 * from units that are in the uses clause.
 */
public class PascalGotoDeclarationHandler implements GotoDeclarationHandler {
    private static final Logger LOG = Logger.getInstance(PascalGotoDeclarationHandler.class);
    private static final Key<Boolean> GOTO_MEMBER_RESOLVING = Key.create("pascal.goto.member.resolving");

    public PascalGotoDeclarationHandler() {
        System.out.println("[DEBUG_LOG] [PascalNav] PascalGotoDeclarationHandler instantiated");
    }

    @Override
    @Nullable
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        LOG.info("[MemberTraversal] Goto: source='" + (sourceElement != null ? sourceElement.getText() : "<null>") + "' file='" + (sourceElement != null ? sourceElement.getContainingFile().getName() : "<null>") + "' offset=" + offset);
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
                    // Check visibility - only return if accessible
                    if (!isAccessible(resolved, sourceElement)) {
                        LOG.info("[PascalNav]  -> Resolved element not accessible due to visibility");
                        continue; // Try next reference
                    }
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
            LOG.info("[MemberTraversal] Goto: manual member resolution fallback");
            // Prevent recursive re-entry
            Boolean inProgress = sourceElement.getUserData(GOTO_MEMBER_RESOLVING);
            if (Boolean.TRUE.equals(inProgress)) {
                LOG.info("[MemberTraversal] Goto: reentrancy guard active, skipping");
                return null;
            }
            sourceElement.putUserData(GOTO_MEMBER_RESOLVING, true);
            try {
                PsiElement resolved = MemberChainResolver.resolveElement(sourceElement);
                LOG.info("[MemberTraversal] Goto: chain resolved -> " + (resolved != null ? resolved.getClass().getSimpleName() : "<unresolved>"));
                if (resolved != null) {
                    // Check visibility - only return if accessible
                    if (!isAccessible(resolved, sourceElement)) {
                        LOG.info("[MemberTraversal] Goto: resolved element not accessible due to visibility");
                        return null;
                    }
                    return new PsiElement[]{resolved};
                }
            } finally {
                sourceElement.putUserData(GOTO_MEMBER_RESOLVING, null);
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

    /**
     * Check if a resolved element is accessible from the call site based on visibility rules.
     *
     * @param resolved The resolved element (routine, property, or variable)
     * @param callSite The element at the call site
     * @return true if the element is accessible, false if visibility rules block access
     */
    private boolean isAccessible(@NotNull PsiElement resolved, @NotNull PsiElement callSite) {
        String visibility = PsiUtil.getVisibility(resolved);
        if (visibility == null) {
            return true; // No visibility info, allow access
        }

        String resolvedUnit = PsiUtil.getUnitName(resolved);
        String callSiteUnit = PsiUtil.getUnitName(callSite);

        LOG.info("[PascalNav] Visibility check: " + visibility + ", resolvedUnit=" + resolvedUnit + ", callSiteUnit=" + callSiteUnit);

        // Public and published are always accessible
        if ("public".equals(visibility) || "published".equals(visibility)) {
            return true;
        }

        // Private and strict private: must be in same unit
        if ("private".equals(visibility) || "strict private".equals(visibility)) {
            boolean sameUnit = resolvedUnit != null && resolvedUnit.equalsIgnoreCase(callSiteUnit);
            if (!sameUnit) {
                LOG.info("[PascalNav]  -> Private member not accessible from different unit");
            }
            return sameUnit;
        }

        // Protected and strict protected: same unit OR descendant class
        // For now, we allow protected access from the same unit only
        // Full inheritance-based protected access would require checking if callSite is in a descendant class
        if ("protected".equals(visibility) || "strict protected".equals(visibility)) {
            boolean sameUnit = resolvedUnit != null && resolvedUnit.equalsIgnoreCase(callSiteUnit);
            if (!sameUnit) {
                // TODO: Check if callSite is within a class that descends from resolved's containing class
                LOG.info("[PascalNav]  -> Protected member not accessible from different unit (inheritance check not implemented)");
            }
            return sameUnit;
        }

        return true;
    }
}
