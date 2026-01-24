package nl.akiar.pascal.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import nl.akiar.pascal.PascalLanguage
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.*
import nl.akiar.pascal.reference.PascalUnitReference
import nl.akiar.pascal.resolution.DelphiBuiltIns
import nl.akiar.pascal.resolution.PascalSymbolResolver
import nl.akiar.pascal.uses.PascalUsesClauseInfo

/**
 * Annotator that checks symbol references against uses clauses.
 *
 * This implements proper Delphi scoping semantics:
 * - Same-file declarations always have highest priority
 * - Among external units, the LAST one in the uses clause wins ("last wins" rule)
 * - Only symbols that are not in any used unit are errors
 * - Symbols in implementation uses are not available in interface section
 *
 * Note: This replaces PascalUsesClauseAnnotator with correct semantics.
 */
class PascalScopeAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only process Pascal files
        if (element.language != PascalLanguage.INSTANCE) {
            return
        }

        val elementType = element.node?.elementType ?: return

        // Skip elements inside unit declaration or uses section (except UNIT_REFERENCE)
        if ((PsiUtil.hasParent(element, PascalElementTypes.UNIT_DECL_SECTION) ||
             PsiUtil.hasParent(element, PascalElementTypes.USES_SECTION)) &&
            elementType != PascalElementTypes.UNIT_REFERENCE) {
            return
        }

        // Handle unit references in uses clause
        if (elementType == PascalElementTypes.UNIT_REFERENCE) {
            annotateUnitReference(element, holder)
            return
        }

        // Only process identifier tokens
        if (elementType != PascalTokenTypes.IDENTIFIER) {
            return
        }

        // Skip if this is a definition name (not a reference)
        if (isDefinitionName(element)) {
            return
        }

        val text = element.text
        if (text.isNullOrEmpty()) {
            return
        }

        // Skip built-in identifiers from System unit (always available)
        if (DelphiBuiltIns.isBuiltIn(text)) {
            return
        }

        val file = element.containingFile ?: return
        val offset = element.textOffset

        // Check type-like identifiers (T*, I*, E* convention)
        if (looksLikeType(text, element)) {
            val typeResult = PascalSymbolResolver.resolveType(text, file, offset)
            val error = typeResult.getErrorMessage()
            if (error != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, error)
                    .range(element)
                    .create()
                return
            }

            // If it resolved to a type, we're done
            if (typeResult.hasValidResolution) {
                return
            }
        }

        // Check for routines
        val routineResult = PascalSymbolResolver.resolveRoutine(text, file, offset)
        val routineError = routineResult.getErrorMessage()
        if (routineError != null) {
            holder.newAnnotation(HighlightSeverity.ERROR, routineError)
                .range(element)
                .create()
            return
        }
        if (routineResult.hasValidResolution) {
            return
        }

        // Check for variables (only global/constant/threadvar need uses clause)
        val varResult = PascalSymbolResolver.resolveVariable(text, file, offset)
        val varError = varResult.getErrorMessage()
        if (varError != null) {
            holder.newAnnotation(HighlightSeverity.ERROR, varError)
                .range(element)
                .create()
        }
    }

    private fun annotateUnitReference(element: PsiElement, holder: AnnotationHolder) {
        val ref = PascalUnitReference(element)
        val resolved = ref.resolve()

        when {
            resolved == null -> {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "Unknown unit `${element.text}`, please add unit and location to the project file"
                )
                    .range(element)
                    .create()
            }
            ref.isResolvedViaScopeNames -> {
                val targetFile = resolved as PsiFile
                var fullUnitName = targetFile.name
                if (fullUnitName.endsWith(".pas")) {
                    fullUnitName = fullUnitName.dropLast(4)
                }
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Unit '$fullUnitName' is resolved via unit scope names. Using short unit names in the uses clause is considered bad practice."
                )
                    .range(element)
                    .create()
            }
        }
    }

    private fun isDefinitionName(element: PsiElement): Boolean {
        val parent = element.parent
        return when (parent) {
            is PascalVariableDefinition -> parent.nameIdentifier == element
            is PascalTypeDefinition -> parent.nameIdentifier == element
            is PascalRoutine -> parent.nameIdentifier == element
            is PascalProperty -> parent.nameIdentifier == element
            else -> false
        }
    }

    private fun looksLikeType(text: String, element: PsiElement): Boolean {
        if (text.length < 2) return false
        val firstChar = text[0]
        if (firstChar != 'T' && firstChar != 'I' && firstChar != 'E') return false
        if (!text[1].isUpperCase()) return false

        // Heuristic: If followed by a colon, it's likely a field/variable name
        val next = PsiUtil.getNextNoneIgnorableSibling(element)
        return next == null || next.node?.elementType != PascalTokenTypes.COLON
    }
}
