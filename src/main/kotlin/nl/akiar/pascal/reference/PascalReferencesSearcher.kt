package nl.akiar.pascal.reference

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import nl.akiar.pascal.PascalTokenTypes

/**
 * Custom references searcher for Pascal that explicitly checks
 * PsiReferenceContributor references. Required because leaf PSI elements
 * (IDENTIFIER tokens) are LeafPsiElement which doesn't include contributor
 * references in getReferences().
 */
class PascalReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val target = queryParameters.elementToSearch
        if (target !is PsiNamedElement) return

        val name = ReadAction.compute<String?, Throwable> { target.name } ?: return
        val project = ReadAction.compute<com.intellij.openapi.project.Project, Throwable> { target.project }
        val scope = queryParameters.effectiveSearchScope

        val searchScope = if (scope is GlobalSearchScope) scope
            else GlobalSearchScope.allScope(project)

        // Pascal is case-insensitive — search for the word case-insensitively
        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, offsetInElement ->
                ReadAction.compute<Boolean, Throwable> {
                    if (!element.isValid) return@compute true
                    processElement(element, offsetInElement, target, consumer)
                }
            },
            searchScope,
            name,
            UsageSearchContext.IN_CODE.toShort(),
            false // case INSENSITIVE for Pascal
        )
    }

    private fun processElement(
        element: PsiElement,
        offsetInElement: Int,
        target: PsiElement,
        consumer: Processor<in PsiReference>
    ): Boolean {
        // Find the leaf token at the word occurrence
        val file = element.containingFile ?: return true
        val absoluteOffset = element.textOffset + offsetInElement
        val leaf = file.findElementAt(absoluteOffset) ?: return true

        // Only process IDENTIFIER tokens
        val leafType = leaf.node?.elementType
        if (leafType != PascalTokenTypes.IDENTIFIER) return true

        // Check contributor references on the leaf element
        val refs = ReferenceProvidersRegistry.getReferencesFromProviders(leaf)
        for (ref in refs) {
            try {
                if (ref.isReferenceTo(target)) {
                    if (!consumer.process(ref)) return false
                }
            } catch (_: Exception) {
                // Skip references that fail to resolve
            }
        }

        return true
    }
}
