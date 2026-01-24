package nl.akiar.pascal.resolution

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.stubs.PascalTypeIndex
import nl.akiar.pascal.stubs.PascalVariableIndex

/**
 * Resolves complete member chains like `obj.Property.Method` in a single pass.
 *
 * The key insight is that each member in a chain needs the TYPE context from
 * the previous member. For example, in `meResult.Lines.Add`:
 * - `meResult` is a variable of type `TMandatoryResult`
 * - `Lines` is a property of `TMandatoryResult` with type `TStrings`
 * - `Add` is a method of `TStrings`
 *
 * This resolver:
 * 1. Collects all identifiers in the member chain
 * 2. Resolves the first identifier normally (variable, type, etc.)
 * 3. For each subsequent identifier, gets the type of the previous element
 *    and looks up the member in that type (including ancestors)
 * 4. Uses transitive dependency resolution to find types across unit boundaries
 */
object MemberChainResolver {
    private val LOG = Logger.getInstance(MemberChainResolver::class.java)

    /**
     * Result of resolving a member chain.
     */
    data class ChainResolutionResult(
        /** List of resolved elements for each part of the chain (null for unresolved) */
        val resolvedElements: List<PsiElement?>,
        /** The chain elements (identifiers) */
        val chainElements: List<PsiElement>,
        /** The origin file where resolution started */
        val originFile: PsiFile
    ) {
        val isFullyResolved: Boolean
            get() = resolvedElements.none { it == null }

        val lastResolved: PsiElement?
            get() = resolvedElements.lastOrNull { it != null }
    }

    /**
     * Resolve a member chain starting from an identifier.
     *
     * @param startElement The first identifier in the chain (or any identifier in the chain)
     * @return ChainResolutionResult containing resolved elements for each part
     */
    @JvmStatic
    fun resolveChain(startElement: PsiElement): ChainResolutionResult {
        val originFile = startElement.containingFile
        val chain = collectChain(startElement)

        LOG.debug("[MemberChain] Resolving chain of ${chain.size} elements starting from '${startElement.text}'")

        val resolved = resolveChainElements(chain, originFile)

        return ChainResolutionResult(
            resolvedElements = resolved,
            chainElements = chain,
            originFile = originFile
        )
    }

    /**
     * Find the index of an element within its member chain.
     *
     * @param element The element to find
     * @return The 0-based index in the chain, or -1 if not found
     */
    @JvmStatic
    fun findIndexInChain(element: PsiElement): Int {
        val chain = collectChain(element)
        return chain.indexOfFirst { it.textRange == element.textRange }
    }

    /**
     * Resolve a specific element within a member chain.
     *
     * @param element The element to resolve
     * @return The resolved PSI element, or null if not found
     */
    @JvmStatic
    fun resolveElement(element: PsiElement): PsiElement? {
        val result = resolveChain(element)
        val index = findIndexInChain(element)
        if (index >= 0 && index < result.resolvedElements.size) {
            return result.resolvedElements[index]
        }
        return null
    }

    /**
     * Collect all identifiers in a member access chain.
     * For "a.b.c", returns [a, b, c]
     */
    private fun collectChain(element: PsiElement): List<PsiElement> {
        val chain = mutableListOf<PsiElement>()

        // First, walk backwards to find the start of the chain
        var current = findChainStart(element)
        chain.add(current)

        // Then walk forward to collect all members
        while (true) {
            val next = findNextInChain(current) ?: break
            chain.add(next)
            current = next
        }

        return chain
    }

    /**
     * Find the start of a member chain by walking backwards through DOT tokens.
     */
    private fun findChainStart(element: PsiElement): PsiElement {
        var current = element

        while (true) {
            // Look for DOT before this element
            var prev = PsiTreeUtil.prevLeaf(current)
            while (prev != null && isWhitespaceOrComment(prev)) {
                prev = PsiTreeUtil.prevLeaf(prev)
            }

            if (prev == null || prev.node.elementType != PascalTokenTypes.DOT) {
                break
            }

            // Found a DOT, look for identifier before it
            var beforeDot = PsiTreeUtil.prevLeaf(prev)
            while (beforeDot != null && isWhitespaceOrComment(beforeDot)) {
                beforeDot = PsiTreeUtil.prevLeaf(beforeDot)
            }

            if (beforeDot == null || beforeDot.node.elementType != PascalTokenTypes.IDENTIFIER) {
                break
            }

            current = beforeDot
        }

        return current
    }

    /**
     * Find the next identifier in the chain (after a DOT).
     */
    private fun findNextInChain(element: PsiElement): PsiElement? {
        var next = PsiTreeUtil.nextLeaf(element)
        while (next != null && isWhitespaceOrComment(next)) {
            next = PsiTreeUtil.nextLeaf(next)
        }

        if (next == null || next.node.elementType != PascalTokenTypes.DOT) {
            return null
        }

        // Found DOT, look for identifier after it
        var afterDot = PsiTreeUtil.nextLeaf(next)
        while (afterDot != null && isWhitespaceOrComment(afterDot)) {
            afterDot = PsiTreeUtil.nextLeaf(afterDot)
        }

        if (afterDot != null && afterDot.node.elementType == PascalTokenTypes.IDENTIFIER) {
            return afterDot
        }

        return null
    }

    /**
     * Resolve each element in the chain, passing type context forward.
     */
    private fun resolveChainElements(chain: List<PsiElement>, originFile: PsiFile): List<PsiElement?> {
        if (chain.isEmpty()) return emptyList()

        val resolved = mutableListOf<PsiElement?>()
        var currentType: PascalTypeDefinition? = null

        for ((index, element) in chain.withIndex()) {
            val name = element.text

            if (index == 0) {
                // First element: resolve as variable or type
                val firstResolved = resolveFirstElement(element, originFile)
                resolved.add(firstResolved)

                if (firstResolved != null) {
                    currentType = getTypeOf(firstResolved, originFile)
                    LOG.debug("[MemberChain] First element '$name' resolved to $firstResolved, type: ${currentType?.name}")
                } else {
                    LOG.debug("[MemberChain] First element '$name' could not be resolved")
                }
            } else {
                // Subsequent elements: look up as member of current type
                if (currentType == null) {
                    LOG.debug("[MemberChain] Cannot resolve '$name' - no type context")
                    resolved.add(null)
                    continue
                }

                val memberResolved = findMemberInType(currentType, name, true)
                resolved.add(memberResolved)

                if (memberResolved != null) {
                    currentType = getTypeOf(memberResolved, originFile)
                    LOG.debug("[MemberChain] Member '$name' in ${currentType?.name} resolved to $memberResolved")
                } else {
                    LOG.debug("[MemberChain] Member '$name' not found in ${currentType.name}")
                    currentType = null
                }
            }
        }

        return resolved
    }

    /**
     * Resolve the first element in a chain (variable, parameter, type, etc.)
     */
    private fun resolveFirstElement(element: PsiElement, originFile: PsiFile): PsiElement? {
        val name = element.text

        // 1. Try existing references
        val refs = element.references
        for (ref in refs) {
            val resolved = ref.resolve()
            if (resolved != null) {
                return resolved
            }
        }

        // 2. Try variable resolution
        val variable = PascalVariableIndex.findVariableAtPosition(name, originFile, element.textOffset)
        if (variable != null) {
            return variable
        }

        // 3. Try type resolution (for static member access like TMyClass.Create)
        val typeResult = PascalTypeIndex.findTypeWithTransitiveDeps(name, originFile, element.textOffset)
        if (typeResult.inScopeTypes.isNotEmpty()) {
            return typeResult.inScopeTypes[0]
        }

        return null
    }

    /**
     * Get the type definition for a resolved element.
     */
    private fun getTypeOf(element: PsiElement, originFile: PsiFile): PascalTypeDefinition? {
        val typeName = when (element) {
            is PascalVariableDefinition -> element.typeName
            is PascalProperty -> element.typeName
            is PascalRoutine -> {
                // For routines (functions), we could look at return type
                // but that's complex - for now, return null
                null
            }
            is PascalTypeDefinition -> {
                // If it's a type itself (like TMyClass.StaticMethod), return it
                return element
            }
            else -> null
        }

        if (typeName == null) {
            return null
        }

        // Look up the type using transitive deps from the ORIGIN file
        val result = PascalTypeIndex.findTypeWithTransitiveDeps(typeName, originFile, element.textOffset)
        return result.inScopeTypes.firstOrNull()
    }

    /**
     * Find a member in a type (including ancestors).
     */
    private fun findMemberInType(typeDef: PascalTypeDefinition, name: String, includeAncestors: Boolean): PsiElement? {
        val members = typeDef.getMembers(includeAncestors)
        for (member in members) {
            if (member is PsiNameIdentifierOwner) {
                val memberName = member.name
                if (name.equals(memberName, ignoreCase = true)) {
                    return member
                }
            }
        }
        return null
    }

    private fun isWhitespaceOrComment(element: PsiElement): Boolean {
        val type = element.node.elementType
        return type == PascalTokenTypes.WHITE_SPACE ||
               type == PascalTokenTypes.LINE_COMMENT ||
               type == PascalTokenTypes.BLOCK_COMMENT
    }
}
