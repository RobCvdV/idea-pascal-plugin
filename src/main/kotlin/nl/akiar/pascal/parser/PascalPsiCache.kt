package nl.akiar.pascal.parser

import com.intellij.psi.tree.IElementType
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the PSI structure from the last successful parse so it can be replayed
 * when both original and sanitized text fail to parse.
 *
 * The cache stores a flat list of top-level structural elements (type definitions,
 * routines, sections, etc.) with their offsets. On replay, offsets are adjusted
 * based on the edit delta.
 */
object PascalPsiCache {

    data class CachedElement(
        val startOffset: Int,
        val endOffset: Int,
        val elementType: IElementType
    )

    data class CachedPsiStructure(
        val text: String,
        val textLength: Int,
        val elements: List<CachedElement>
    )

    private val cache = ConcurrentHashMap<String, CachedPsiStructure>()

    /**
     * Element types safe to cache and replay.
     * IMPORTANT: Only non-stub element types are safe here. Stub-based types
     * (TYPE_DEFINITION, VARIABLE_DEFINITION, ROUTINE_DECLARATION, PROPERTY_DEFINITION,
     * ATTRIBUTE_DEFINITION) must NOT be replayed because the stub builder expects specific
     * child structure that raw token replay cannot provide, causing "Failed to build stub tree" errors.
     */
    private val CACHEABLE_TYPES: Set<IElementType> by lazy {
        setOf(
            nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION,
            nl.akiar.pascal.psi.PascalElementTypes.USES_SECTION,
            nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_SECTION,
            nl.akiar.pascal.psi.PascalElementTypes.IMPLEMENTATION_SECTION,
            nl.akiar.pascal.psi.PascalElementTypes.TYPE_SECTION,
            nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION,
            nl.akiar.pascal.psi.PascalElementTypes.CONST_SECTION,
            nl.akiar.pascal.psi.PascalElementTypes.CLASS_TYPE,
            nl.akiar.pascal.psi.PascalElementTypes.RECORD_TYPE,
            nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_TYPE,
            nl.akiar.pascal.psi.PascalElementTypes.ENUM_TYPE
        )
    }

    /**
     * Record the PSI structure after a successful parse.
     * Called with a list of (startOffset, endOffset, elementType) triples collected during mapNode.
     */
    fun record(filePath: String, text: String, elements: List<CachedElement>) {
        val filtered = elements.filter { it.elementType in CACHEABLE_TYPES }
        if (filtered.isNotEmpty()) {
            cache[filePath] = CachedPsiStructure(text, text.length, filtered)
        }
    }

    /**
     * Try to retrieve and adjust a cached structure for the current text.
     * Returns null if no cache exists for this file.
     */
    fun replay(filePath: String, currentText: String): List<CachedElement>? {
        val cached = cache[filePath] ?: return null
        if (cached.elements.isEmpty()) return null

        val delta = currentText.length - cached.textLength
        val editPoint = findFirstDivergence(cached.text, currentText)

        val adjusted = mutableListOf<CachedElement>()
        for (elem in cached.elements) {
            val s = if (elem.startOffset > editPoint) elem.startOffset + delta else elem.startOffset
            val e = if (elem.endOffset > editPoint) elem.endOffset + delta else elem.endOffset
            if (s >= 0 && e <= currentText.length && s < e) {
                adjusted.add(CachedElement(s, e, elem.elementType))
            }
        }

        return adjusted.ifEmpty { null }
    }

    /**
     * Invalidate cache for a specific file.
     */
    fun invalidate(filePath: String) {
        cache.remove(filePath)
    }

    /**
     * Clear the entire cache (e.g., on project close).
     */
    fun clearAll() {
        cache.clear()
    }

    /**
     * Find the byte offset where two strings first differ.
     */
    private fun findFirstDivergence(a: String, b: String): Int {
        val minLen = minOf(a.length, b.length)
        for (i in 0 until minLen) {
            if (a[i] != b[i]) return i
        }
        return minLen
    }
}
