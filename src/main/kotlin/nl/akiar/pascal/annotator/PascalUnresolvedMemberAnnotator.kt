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
import nl.akiar.pascal.resolution.DelphiBuiltIns
import nl.akiar.pascal.resolution.MemberChainResolver

/**
 * Annotator that highlights unresolved members in member access chains (a.b.c).
 *
 * Uses WEAK_WARNING severity because the resolution system has known gaps
 * (index properties, external types, generic type params, etc.) that can cause
 * false negatives. Only flags when we have high confidence the member truly doesn't exist.
 */
class PascalUnresolvedMemberAnnotator : Annotator {

    companion object {
        private val LOG = Logger.getInstance(PascalUnresolvedMemberAnnotator::class.java)
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.language != PascalLanguage.INSTANCE) return
        if (DumbService.isDumb(element.project)) return

        val elementType = element.node?.elementType ?: return
        if (elementType != PascalTokenTypes.IDENTIFIER) return

        // Only process identifiers that are part of a member chain (preceded by DOT)
        val prev = PsiUtil.getPrevNoneIgnorableSibling(element) ?: return
        if (prev.node?.elementType != PascalTokenTypes.DOT) return

        // Skip identifiers in uses/unit declaration sections
        if (PsiUtil.hasParent(element, PascalElementTypes.USES_SECTION) ||
            PsiUtil.hasParent(element, PascalElementTypes.UNIT_DECL_SECTION)) return

        val text = element.text
        if (text.isNullOrEmpty()) return

        // Skip built-in identifiers
        if (DelphiBuiltIns.isBuiltIn(text)) return

        try {
            val result = MemberChainResolver.resolveChain(element)
            val chainElements = result.chainElements
            val resolvedElements = result.resolvedElements

            // Find our element in the chain
            val index = chainElements.indexOfFirst { it.textRange == element.textRange }
            if (index < 0) return

            // Only flag if we have a valid predecessor
            if (index == 0) return // First element is handled by PascalScopeAnnotator

            val resolved = resolvedElements.getOrNull(index)
            val qualifierResolved = resolvedElements.getOrNull(index - 1)

            // Only flag when qualifier resolved but this member did not
            if (resolved != null) return
            if (qualifierResolved == null) return // cascading — don't pile on

            // Skip if qualifier resolved to a PsiFile (unit-qualified access like System.Default)
            if (qualifierResolved is PsiFile) return

            // Check if the qualifier's type is known. If the resolver couldn't determine
            // the type of the previous chain element (e.g., index properties, external types,
            // generic params), then this is a resolution gap, not a real error.
            // We detect this by checking if ALL remaining elements in the chain are unresolved —
            // that pattern indicates the type chain broke, not that individual members are missing.
            if (index > 1) {
                // For chain positions beyond the second, check if the resolution chain broke
                // at an earlier point (preceding member resolved but its type wasn't determined)
                val precedingResolved = resolvedElements.getOrNull(index - 2)
                if (precedingResolved != null && qualifierResolved != null) {
                    // qualifier resolved as a member but this element didn't — could be real or gap
                    // Only flag if this is the ONLY unresolved member (not a chain break)
                    val nextResolved = resolvedElements.getOrNull(index + 1)
                    if (nextResolved == null && index + 1 < resolvedElements.size) {
                        // Multiple consecutive unresolved after a resolved qualifier = type chain broke
                        return
                    }
                }
            }

            holder.newAnnotation(
                HighlightSeverity.WEAK_WARNING,
                "Cannot resolve member '$text'"
            )
                .range(element)
                .create()

        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            // Indices not ready, skip
        } catch (e: Exception) {
            LOG.debug("Error annotating member: $text", e)
        }
    }
}
