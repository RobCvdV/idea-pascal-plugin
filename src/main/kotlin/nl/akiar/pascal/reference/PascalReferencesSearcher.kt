package nl.akiar.pascal.reference

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
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

        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, offsetInElement ->
                ReadAction.compute<Boolean, Throwable> {
                    if (!element.isValid) return@compute true

                    // Get the leaf element at this offset
                    val leaf = element.containingFile?.findElementAt(element.textOffset + offsetInElement)
                        ?: return@compute true

                    // Check contributor references on the leaf and its parents
                    var current: PsiElement? = leaf
                    while (current != null && current !is com.intellij.psi.PsiFile) {
                        val refs = ReferenceProvidersRegistry.getReferencesFromProviders(current)
                        for (ref in refs) {
                            if (ref.isReferenceTo(target)) {
                                if (!consumer.process(ref)) return@compute false
                            }
                        }
                        current = current.parent
                    }
                    true
                }
            },
            searchScope,
            name,
            UsageSearchContext.IN_CODE.toShort(),
            true // case sensitive
        )
    }
}
