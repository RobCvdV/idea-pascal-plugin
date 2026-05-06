package nl.akiar.pascal.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import nl.akiar.pascal.PascalLanguage
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.*
import nl.akiar.pascal.reference.PascalUnitReference
import nl.akiar.pascal.resolution.DelphiBuiltIns
import nl.akiar.pascal.resolution.PascalSymbolResolver

/**
 * Annotator that checks symbol references against uses clauses.
 *
 * Uses a conservative "hybrid" strategy to avoid false positives:
 * - Unit references in uses clauses: always checked
 * - Interface section: all type-like identifiers checked (declarations are well-structured)
 * - Implementation section: only type references in declarations (var/param types) checked
 * - Routine bodies: skipped entirely (too many local-scope variables: fields, params,
 *   lambda params, exception vars, with-block members, etc.)
 *
 * Implements Delphi "last wins" scoping semantics via PascalSymbolResolver.
 */
class PascalScopeAnnotator : Annotator {

    companion object {
        private val LOG = Logger.getInstance(PascalScopeAnnotator::class.java)

        private val RESERVED_IDENTIFIERS = setOf(
            "Self", "Result", "inherited", "True", "False", "nil",
            "self", "result", "true", "false"
        )
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.language != PascalLanguage.INSTANCE) return
        if (DumbService.isDumb(element.project)) return

        val elementType = element.node?.elementType ?: return

        // Skip elements inside unit declaration or uses section (except UNIT_REFERENCE)
        if ((PsiUtil.hasParent(element, PascalElementTypes.UNIT_DECL_SECTION) ||
             PsiUtil.hasParent(element, PascalElementTypes.USES_SECTION)) &&
            elementType != PascalElementTypes.UNIT_REFERENCE) {
            return
        }

        // Handle unit references in uses clause — always checked
        if (elementType == PascalElementTypes.UNIT_REFERENCE) {
            safeAnnotateUnitReference(element, holder)
            return
        }

        // Only process identifier tokens
        if (elementType != PascalTokenTypes.IDENTIFIER) return

        // --- Cheap guards that apply everywhere ---
        val text = element.text
        if (text.isNullOrEmpty()) return
        if (text in RESERVED_IDENTIFIERS) return
        if (DelphiBuiltIns.isBuiltIn(text)) return
        if (isDefinitionName(element)) return
        if (isMemberAccess(element)) return

        // --- Section-based strategy ---
        // Skip everything inside routine bodies (method implementations, begin..end blocks)
        if (isInsideRoutineBody(element)) return

        // In interface section: check type-like identifiers in declarations
        // In implementation section (outside routine bodies): check type references only
        if (!looksLikeTypeReference(text, element)) return

        // Additional guards for declaration contexts
        if (isPropertySpecifierIdentifier(element)) return
        if (isEnumElement(element)) return
        if (isGenericParameter(element)) return

        safeAnnotateType(element, text, holder)
    }

    // --- Safe wrappers ---

    private fun safeAnnotateUnitReference(element: PsiElement, holder: AnnotationHolder) {
        try {
            annotateUnitReference(element, holder)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            // Indices not ready yet
        } catch (e: Exception) {
            LOG.debug("Error annotating unit reference: ${element.text}", e)
        }
    }

    private fun safeAnnotateType(element: PsiElement, text: String, holder: AnnotationHolder) {
        try {
            val file = element.containingFile ?: return
            val offset = element.textOffset
            val typeResult = PascalSymbolResolver.resolveType(text, file, offset)

            // If type resolved successfully, nothing to report
            if (typeResult.hasValidResolution) return

            // Skip if the type only exists in the implicit System unit (always available in Delphi)
            if (typeResult.outOfScopeTypes.all { it.unitName.equals("system", ignoreCase = true) }) return

            val error = typeResult.getErrorMessage() ?: return
            holder.newAnnotation(HighlightSeverity.ERROR, error)
                .range(element)
                .create()
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            // Indices not ready yet
        } catch (e: Exception) {
            LOG.debug("Error annotating type: $text", e)
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

    // --- Guard helpers ---

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

    private fun isMemberAccess(element: PsiElement): Boolean {
        val prev = PsiUtil.getPrevNoneIgnorableSibling(element) ?: return false
        return prev.node?.elementType == PascalTokenTypes.DOT
    }

    private fun isInsideRoutineBody(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null && parent !is PsiFile) {
            val parentType = parent.node?.elementType
            if (parentType == PascalElementTypes.ROUTINE_BODY) return true
            parent = parent.parent
        }
        return false
    }

    private fun looksLikeTypeReference(text: String, element: PsiElement): Boolean {
        // Must follow Delphi type naming convention: T*, I*, E* + uppercase second char
        if (text.length < 2) return false
        val firstChar = text[0]
        if (firstChar != 'T' && firstChar != 'I' && firstChar != 'E') return false
        if (!text[1].isUpperCase()) return false

        // Skip if followed by colon (likely a field/variable name like TValue: Integer)
        val next = PsiUtil.getNextNoneIgnorableSibling(element)
        if (next != null && next.node?.elementType == PascalTokenTypes.COLON) return false

        return true
    }

    private fun isPropertySpecifierIdentifier(element: PsiElement): Boolean {
        if (!PsiUtil.hasParent(element, PascalElementTypes.PROPERTY_DEFINITION)) return false
        val prev = PsiUtil.getPrevNoneIgnorableSibling(element) ?: return false
        val prevType = prev.node?.elementType
        return prevType == PascalTokenTypes.KW_READ ||
               prevType == PascalTokenTypes.KW_WRITE ||
               prevType == PascalTokenTypes.KW_STORED ||
               prevType == PascalTokenTypes.KW_DEFAULT
    }

    private fun isEnumElement(element: PsiElement): Boolean {
        return PsiUtil.hasParent(element, PascalElementTypes.ENUM_ELEMENT)
    }

    private fun isGenericParameter(element: PsiElement): Boolean {
        return PsiUtil.hasParent(element, PascalElementTypes.GENERIC_PARAMETER)
    }
}
