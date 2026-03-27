package nl.akiar.pascal.navigation;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Maps leaf identifier tokens to their owning Pascal PSI elements
 * (type definitions, routines, properties) so that IntelliJ's
 * TargetElementUtil can find the correct source element for
 * Go to Implementation (Cmd+Opt+B) and related actions.
 */
public class PascalTargetElementEvaluator extends TargetElementEvaluatorEx2 {
    private static final Logger LOG = Logger.getInstance(PascalTargetElementEvaluator.class);

    @Override
    @Nullable
    public PsiElement getNamedElement(@Nullable PsiElement element) {
        if (element == null) return null;

        LOG.debug("[TargetEval] getNamedElement: " + element.getClass().getSimpleName() +
                " text='" + element.getText() + "' type=" + element.getNode().getElementType());

        // 1. Check if the leaf is a declaration name identifier
        PsiElement declaration = findDeclarationForNameId(element);
        if (declaration != null) {
            LOG.debug("[TargetEval]   → found declaration: " + describeElement(declaration));
            return declaration;
        }

        // 2. If not a declaration site, resolve via reference.
        //    Try element's own references first, then ReferenceProvidersRegistry
        //    (which picks up PsiReferenceContributor refs). Always try both sources
        //    because element.getReferences() may return non-Pascal refs that don't
        //    resolve to our types (e.g. inside anonymous methods).
        PsiElement resolved = findPascalTarget(element.getReferences(), "own");
        if (resolved == null) {
            resolved = findPascalTarget(ReferenceProvidersRegistry.getReferencesFromProviders(element), "contributed");
        }
        if (resolved != null) {
            LOG.debug("[TargetEval]   → resolved to: " + describeElement(resolved));
            return resolved;
        }

        LOG.debug("[TargetEval]   → returning null");
        return null;
    }

    @Nullable
    private PsiElement findPascalTarget(PsiReference[] refs, String source) {
        for (PsiReference ref : refs) {
            PsiElement resolved = ref.resolve();
            LOG.debug("[TargetEval]   " + source + " ref " + ref.getClass().getSimpleName() + " → " +
                    (resolved != null ? describeElement(resolved) : "null"));
            if (resolved instanceof PascalRoutine || resolved instanceof PascalTypeDefinition || resolved instanceof PascalProperty) {
                return resolved;
            }
        }
        return null;
    }

    private PsiElement findDeclarationForNameId(PsiElement element) {
        PascalRoutine routine = PsiTreeUtil.getParentOfType(element, PascalRoutine.class, false);
        if (routine != null) {
            PsiElement nameId = routine.getNameIdentifier();
            if (nameId != null && nameId.getTextRange().contains(element.getTextRange())) {
                return routine;
            }
        }

        PascalTypeDefinition typeDef = PsiTreeUtil.getParentOfType(element, PascalTypeDefinition.class, false);
        if (typeDef != null) {
            PsiElement nameId = typeDef.getNameIdentifier();
            if (nameId != null && nameId.getTextRange().contains(element.getTextRange())) {
                return typeDef;
            }
        }

        PascalProperty property = PsiTreeUtil.getParentOfType(element, PascalProperty.class, false);
        if (property != null) {
            PsiElement nameId = property.getNameIdentifier();
            if (nameId != null && nameId.getTextRange().contains(element.getTextRange())) {
                return property;
            }
        }

        return null;
    }

    private String describeElement(PsiElement el) {
        if (el instanceof PascalRoutine r) {
            PascalTypeDefinition owner = r.getContainingClass();
            return "PascalRoutine '" + r.getName() + "' in " +
                    (owner != null ? owner.getName() + "(" + owner.getTypeKind() + ")" : "no class");
        }
        if (el instanceof PascalTypeDefinition td) {
            return "PascalTypeDef '" + td.getName() + "' kind=" + td.getTypeKind();
        }
        if (el instanceof PascalProperty p) {
            return "PascalProperty '" + p.getName() + "'";
        }
        String text = el.getText();
        return el.getClass().getSimpleName() + " '" + text.substring(0, Math.min(text.length(), 30)) + "'";
    }

    @Override
    public boolean isAcceptableNamedParent(@Nullable PsiElement parent) {
        return parent instanceof PascalTypeDefinition
                || parent instanceof PascalRoutine
                || parent instanceof PascalProperty;
    }
}
