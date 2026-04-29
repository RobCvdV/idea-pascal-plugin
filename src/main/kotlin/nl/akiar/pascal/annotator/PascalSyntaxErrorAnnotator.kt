package nl.akiar.pascal.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import nl.akiar.pascal.PascalLanguage
import nl.akiar.pascal.psi.PascalElementTypes
import nl.akiar.pascal.psi.PsiUtil

/**
 * Annotator that detects structural syntax errors by examining the PSI tree.
 *
 * Only runs on the FILE element (once per file, not per token) to avoid
 * performance impact. Checks for missing structural elements that indicate
 * the file has syntax problems.
 */
class PascalSyntaxErrorAnnotator : Annotator {

    companion object {
        private val LOG = Logger.getInstance(PascalSyntaxErrorAnnotator::class.java)
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only run on the file element itself — one check per file
        if (element !is PsiFile) return
        if (element.language != PascalLanguage.INSTANCE) return

        try {
            checkFileStructure(element, holder)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.debug("Error checking file structure", e)
        }
    }

    private fun checkFileStructure(file: PsiFile, holder: AnnotationHolder) {
        val fileNode = file.node ?: return
        val text = file.text
        if (text.isNullOrBlank()) return

        val hasUnitDecl = PsiUtil.findFirstRecursive(fileNode, PascalElementTypes.UNIT_DECL_SECTION) != null
        val hasProgramDecl = PsiUtil.findFirstRecursive(fileNode, PascalElementTypes.PROGRAM_DECL_SECTION) != null
        val hasLibraryDecl = PsiUtil.findFirstRecursive(fileNode, PascalElementTypes.LIBRARY_DECL_SECTION) != null

        // Check for missing header
        if (!hasUnitDecl && !hasProgramDecl && !hasLibraryDecl) {
            // Only warn if the file has substantial content (not just whitespace/comments)
            val hasKeywords = text.contains("begin", ignoreCase = true) ||
                              text.contains("type", ignoreCase = true) ||
                              text.contains("var", ignoreCase = true) ||
                              text.contains("procedure", ignoreCase = true) ||
                              text.contains("function", ignoreCase = true)
            if (hasKeywords) {
                val firstChild = file.firstChild
                if (firstChild != null) {
                    holder.newAnnotation(
                        HighlightSeverity.WARNING,
                        "Missing unit, program, or library declaration"
                    )
                        .range(firstChild)
                        .create()
                }
            }
            return
        }

        // For unit files, check for missing interface/implementation sections
        if (hasUnitDecl) {
            val hasInterface = PsiUtil.findFirstRecursive(fileNode, PascalElementTypes.INTERFACE_SECTION) != null
            val hasImplementation = PsiUtil.findFirstRecursive(fileNode, PascalElementTypes.IMPLEMENTATION_SECTION) != null

            val unitDeclNode = PsiUtil.findFirstRecursive(fileNode, PascalElementTypes.UNIT_DECL_SECTION)
            val warningTarget = unitDeclNode?.psi ?: file.firstChild ?: return

            if (!hasInterface && !hasImplementation) {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Unit is missing both interface and implementation sections"
                )
                    .range(warningTarget)
                    .create()
            } else if (!hasInterface) {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Unit is missing interface section"
                )
                    .range(warningTarget)
                    .create()
            } else if (!hasImplementation) {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Unit is missing implementation section"
                )
                    .range(warningTarget)
                    .create()
            }
        }
    }
}
