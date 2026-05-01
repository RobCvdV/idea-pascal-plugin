package nl.akiar.pascal.reference

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalElementTypes
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.VariableKind

/**
 * Custom references searcher for Pascal that explicitly checks
 * PsiReferenceContributor references. Required because leaf PSI elements
 * (IDENTIFIER tokens) are LeafPsiElement which doesn't include contributor
 * references in getReferences().
 *
 * For class methods (e.g., TMyClass.Create), pre-filters occurrences to avoid
 * resolving every "Create" across 10k+ files — only checks member access
 * contexts (after DOT) and implicit self calls within the same class.
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
        val isClassMember = ReadAction.compute<Boolean, Throwable> {
            target is PascalRoutine && (target as PascalRoutine).isMethod ||
                target is PascalVariableDefinition && (target as PascalVariableDefinition).variableKind == VariableKind.FIELD
        }

        // Always search all indexed files — Pascal source paths are often outside
        // IntelliJ content roots (monorepo with configured source paths).
        val searchScope = GlobalSearchScope.allScope(project)

        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, offsetInElement ->
                ReadAction.compute<Boolean, Throwable> {
                    if (!element.isValid) return@compute true
                    processElement(element, offsetInElement, target, isClassMember, consumer)
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
        isClassMember: Boolean,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val file = element.containingFile ?: return true
        val absoluteOffset = element.textOffset + offsetInElement
        val leaf = file.findElementAt(absoluteOffset) ?: return true

        // Only process IDENTIFIER tokens
        if (leaf.node?.elementType != PascalTokenTypes.IDENTIFIER) return true

        // Pre-filter for class members: skip bare identifiers that can't be
        // references to the target method/field. Only check:
        // 1. Member access (preceded by DOT): obj.Create, Self.Create
        // 2. Implicit self within the same class (no DOT, but inside the class body)
        // 3. Inherited calls: inherited Create
        if (isClassMember && !isMemberAccessContext(leaf)) {
            return true
        }

        // Check contributor references
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

    /**
     * Check if this identifier is in a context where it could be a member access:
     * - Preceded by DOT (obj.Method, Self.Method)
     * - Preceded by 'inherited' keyword
     * - Inside a routine that belongs to the same class (implicit self)
     */
    private fun isMemberAccessContext(leaf: PsiElement): Boolean {
        // Check if preceded by DOT
        var prev = PsiTreeUtil.prevLeaf(leaf)
        while (prev is PsiWhiteSpace || prev is PsiComment) {
            prev = PsiTreeUtil.prevLeaf(prev)
        }
        if (prev != null) {
            val prevType = prev.node?.elementType
            if (prevType == PascalTokenTypes.DOT) return true
            if (prevType == PascalTokenTypes.KW_INHERITED) return true
        }

        // Check if inside a class method body (implicit Self.Method call)
        val routine = PsiTreeUtil.getParentOfType(leaf, PascalRoutine::class.java)
        if (routine != null && routine.isMethod) return true

        return false
    }
}
