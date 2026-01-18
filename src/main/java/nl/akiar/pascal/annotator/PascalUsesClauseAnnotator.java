package nl.akiar.pascal.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.reference.PascalUnitReference;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.uses.PascalUsesClauseUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator that checks type references against uses clauses.
 * Shows errors when a type is referenced but its unit is not in the
 * correct uses clause (interface uses for interface section references,
 * any uses clause for implementation section references).
 */
public class PascalUsesClauseAnnotator implements Annotator {
    private static final Logger LOG = Logger.getInstance(PascalUsesClauseAnnotator.class);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only process Pascal files
        if (element.getLanguage() != PascalLanguage.INSTANCE) {
            return;
        }

        // Handle unit references in uses clause
        if (element.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
            PascalUnitReference ref = new PascalUnitReference(element);
            PsiElement resolved = ref.resolve();
            if (resolved == null) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Unknown unit `" + element.getText() + "`, please add unit and location to the projectfile: `Projectfile.dpr`")
                        .range(element)
                        .create();
            } else if (ref.isResolvedViaScopeNames()) {
                PsiFile targetFile = (PsiFile) resolved;
                String fullUnitName = targetFile.getName();
                if (fullUnitName.endsWith(".pas")) {
                    fullUnitName = fullUnitName.substring(0, fullUnitName.length() - 4);
                }
                holder.newAnnotation(HighlightSeverity.WARNING, "Unit '" + fullUnitName + "' is resolved via unit scope names. Using short unit names in the uses clause is considered bad practice.")
                        .range(element)
                        .create();
            }
            return;
        }

        // Only process identifier tokens
        if (element.getNode().getElementType() != PascalTokenTypes.IDENTIFIER) {
            return;
        }

        // Skip if this is a type definition name (not a reference)
        PsiElement parent = element.getParent();
        if (parent instanceof PascalTypeDefinition) {
            if (((PascalTypeDefinition) parent).getNameIdentifier() == element) {
                return; // This is the definition, not a reference
            }
        }

        // Check if this identifier looks like a type reference (starts with T or I convention)
        String text = element.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        // Simple heuristic: only check identifiers that look like type names
        // Pascal convention: TClassName, IInterfaceName, EExceptionName
        char firstChar = text.charAt(0);
        if (firstChar != 'T' && firstChar != 'I' && firstChar != 'E') {
            return; // Probably not a type reference
        }

        // Must be at least 2 characters (T + something)
        if (text.length() < 2) {
            return;
        }

        // Second character should be uppercase (TGood, not This)
        char secondChar = text.charAt(1);
        if (!Character.isUpperCase(secondChar)) {
            return;
        }

        // Heuristic: If followed by a colon, it's likely a field/variable name, not a type reference.
        // TInterfaceEntry = record IID: TGUID; end;
        // Here IID is followed by colon, TGUID is not.
        PsiElement nextSibling = element.getNextSibling();
        while (nextSibling != null && (nextSibling.getNode().getElementType() == PascalTokenTypes.WHITE_SPACE ||
                nextSibling.getNode().getElementType() == PascalTokenTypes.LINE_COMMENT ||
                nextSibling.getNode().getElementType() == PascalTokenTypes.BLOCK_COMMENT ||
                nextSibling.getNode().getElementType() == PascalTokenTypes.COMPILER_DIRECTIVE)) {
            nextSibling = nextSibling.getNextSibling();
        }
        if (nextSibling != null && nextSibling.getNode().getElementType() == PascalTokenTypes.COLON) {
            return;
        }

        // Look up the type with uses clause validation
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }

        int offset = element.getTextOffset();
        PascalTypeIndex.TypeLookupResult result =
                PascalTypeIndex.findTypesWithUsesValidation(text, file, offset);

        // If we found in-scope types, everything is OK (but check for scope name warnings)
        if (!result.getInScopeTypes().isEmpty()) {
            if (!result.getInScopeViaScopeNames().isEmpty()) {
                // If ANY of the in-scope types were found via scope names, show a warning
                PascalTypeDefinition first = result.getInScopeViaScopeNames().get(0);
                String fullUnitName = PascalUsesClauseUtil.getUnitName(first.getContainingFile());
                holder.newAnnotation(HighlightSeverity.WARNING,
                                "Unit '" + fullUnitName + "' is included via unit scope names. " +
                                "Using short unit names is considered bad practice.")
                        .range(element)
                        .create();
            }
            return;
        }

        // If we found out-of-scope types, show an error
        String errorMessage = result.getErrorMessage();
        if (errorMessage != null) {
            LOG.info("[PascalUses] Error for '" + text + "': " + errorMessage);
            holder.newAnnotation(HighlightSeverity.ERROR, errorMessage)
                    .range(element)
                    .create();
        }
        // If no types found at all, we don't show an error (might be a variable, not a type)
    }
}
