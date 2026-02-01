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
import nl.akiar.pascal.psi.PsiUtil;
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

        // Skip if inside a unit reference or unit declaration or uses section (except for the reference itself)
        if ((PsiUtil.hasParent(element, PascalElementTypes.UNIT_DECL_SECTION) ||
             PsiUtil.hasParent(element, PascalElementTypes.USES_SECTION)) &&
            element.getNode().getElementType() != PascalElementTypes.UNIT_REFERENCE) {
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

        // Skip if this is a definition name (not a reference)
        if (isDefinitionName(element)) {
            return;
        }

        // Skip property getter/setter identifiers (class members, not global refs)
        if (isPropertySpecifierIdentifier(element)) {
            return;
        }

        String text = element.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }
        int offset = element.getTextOffset();

        // 1. Check if it looks like a type reference (starts with T, I, or E convention)
        if (looksLikeType(text, element)) {
            PascalTypeIndex.TypeLookupResult typeResult = PascalTypeIndex.findTypesWithUsesValidation(text, file, offset);
            String typeError = typeResult.getErrorMessage();
            if (typeError != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, typeError).range(element).create();
                return;
            }

            // Check for scope name warnings
            if (!typeResult.getInScopeViaScopeNames().isEmpty()) {
                PascalTypeDefinition first = typeResult.getInScopeViaScopeNames().get(0);
                holder.newAnnotation(HighlightSeverity.WARNING,
                                "Unit '" + first.getUnitName() + "' is included via unit scope names. " +
                                "Using short unit names is considered bad practice.")
                        .range(element)
                        .create();
            }
            
            // If it matched a type, we're done
            if (!typeResult.getInScopeTypes().isEmpty()) {
                return;
            }
        }

        // 2. Check for routines
        nl.akiar.pascal.stubs.PascalRoutineIndex.RoutineLookupResult routineResult =
                nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutinesWithUsesValidation(text, file, offset);
        String routineError = routineResult.getErrorMessage();
        if (routineError != null) {
            // Only report if it actually exists somewhere (out of scope) or is ambiguous in scope
            if (!routineResult.getInScopeRoutines().isEmpty() || !routineResult.getOutOfScopeRoutines().isEmpty()) {
                holder.newAnnotation(HighlightSeverity.ERROR, routineError).range(element).create();
                return;
            }
        }
        if (!routineResult.getInScopeRoutines().isEmpty()) {
            return;
        }

        // 3. Check for variables
        nl.akiar.pascal.stubs.PascalVariableIndex.VariableLookupResult varResult =
                nl.akiar.pascal.stubs.PascalVariableIndex.findVariablesWithUsesValidation(text, file, offset);
        String varError = varResult.getErrorMessage();
        if (varError != null) {
            if (!varResult.getInScopeVariables().isEmpty() || !varResult.getOutOfScopeVariables().isEmpty()) {
                holder.newAnnotation(HighlightSeverity.ERROR, varError).range(element).create();
                return;
            }
        }
    }

    private boolean isDefinitionName(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent instanceof nl.akiar.pascal.psi.PascalVariableDefinition) {
            return ((nl.akiar.pascal.psi.PascalVariableDefinition) parent).getNameIdentifier() == element;
        }
        if (parent instanceof nl.akiar.pascal.psi.PascalTypeDefinition) {
            return ((nl.akiar.pascal.psi.PascalTypeDefinition) parent).getNameIdentifier() == element;
        }
        if (parent instanceof nl.akiar.pascal.psi.PascalRoutine) {
            return ((nl.akiar.pascal.psi.PascalRoutine) parent).getNameIdentifier() == element;
        }
        if (parent instanceof nl.akiar.pascal.psi.PascalProperty) {
            return ((nl.akiar.pascal.psi.PascalProperty) parent).getNameIdentifier() == element;
        }
        return false;
    }

    private boolean looksLikeType(String text, PsiElement element) {
        if (text.length() < 2) return false;
        char firstChar = text.charAt(0);
        if (firstChar != 'T' && firstChar != 'I' && firstChar != 'E') return false;
        if (!Character.isUpperCase(text.charAt(1))) return false;

        // Heuristic: If followed by a colon, it's likely a field/variable name
        PsiElement next = PsiUtil.getNextNoneIgnorableSibling(element);
        return next == null || next.getNode().getElementType() != PascalTokenTypes.COLON;
    }

    private boolean isPropertySpecifierIdentifier(PsiElement element) {
        // Check if inside PROPERTY_DEFINITION
        if (!PsiUtil.hasParent(element, PascalElementTypes.PROPERTY_DEFINITION)) {
            return false;
        }

        // Check if preceded by KW_READ, KW_WRITE, KW_STORED, or KW_DEFAULT
        PsiElement prev = PsiUtil.getPrevNoneIgnorableSibling(element);
        if (prev == null) return false;

        com.intellij.psi.tree.IElementType prevType = prev.getNode().getElementType();
        return prevType == PascalTokenTypes.KW_READ ||
               prevType == PascalTokenTypes.KW_WRITE ||
               prevType == PascalTokenTypes.KW_STORED ||
               prevType == PascalTokenTypes.KW_DEFAULT;
    }
}
