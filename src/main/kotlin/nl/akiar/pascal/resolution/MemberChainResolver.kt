package nl.akiar.pascal.resolution

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import nl.akiar.pascal.PascalTokenTypes
import com.intellij.psi.PsiManager
import nl.akiar.pascal.psi.PascalForStatement
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.TypeKind
import nl.akiar.pascal.psi.impl.PascalTypeDefinitionImpl
import nl.akiar.pascal.stubs.PascalPropertyIndex
import nl.akiar.pascal.stubs.PascalRoutineIndex
import nl.akiar.pascal.stubs.PascalTypeIndex
import nl.akiar.pascal.stubs.PascalVariableIndex

/**
 * Resolves complete member chains like `obj.Property.Method` in a single pass.
 *
 * The key insight is that each member in a chain needs the TYPE context from
 * the previous member.
 */
object MemberChainResolver {
    private val LOG = Logger.getInstance(MemberChainResolver::class.java)
    private val LOG_ENABLED = java.lang.Boolean.getBoolean("pascal.memberTraversal.logging")

    private class LruCache<K, V>(val maxSize: Int) : java.util.LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    private val FILE_RESOLUTION_CACHE: Key<LruCache<String, Any?>> = Key.create("pascal.file.resolution.cache")

    // Short-lived memo for chains to avoid repeated resolves during daemon passes
    private data class ChainKey(val filePath: String?, val startOffset: Int, val chainText: String)
    private val chainMemo = java.util.concurrent.ConcurrentHashMap<ChainKey, PsiElement?>()
    @Volatile private var lastMemoClearTs: Long = System.currentTimeMillis()
    private const val MEMO_TTL_MS = 2000L

    private object ResolverConfig {
        @JvmField val enablePerformanceMetrics: Boolean = java.lang.Boolean.getBoolean("pascal.resolver.metrics")
        @JvmField val enableDebugLogs: Boolean = java.lang.Boolean.getBoolean("pascal.resolver.debug")
    }

    object PerformanceMetrics {
        private val timings = mutableListOf<Pair<String, Long>>()
        @Synchronized fun record(label: String, nanos: Long) {
            if (ResolverConfig.enablePerformanceMetrics) {
                if (timings.size >= 10000) timings.removeFirst()
                timings.add(label to nanos)
            }
        }
        @Synchronized fun snapshot(): List<Pair<String, Long>> = timings.toList()
        @Synchronized fun clear() { timings.clear() }
    }

    private fun maybeLog(msg: String, file: PsiFile? = null) {
        if (LOG_ENABLED && (file == null || nl.akiar.pascal.log.UnitLogFilter.shouldLog(file))) LOG.info(msg)
    }

    private fun clearExpiredMemo() {
        val now = System.currentTimeMillis()
        if (now - lastMemoClearTs > MEMO_TTL_MS || chainMemo.size > 500) {
            chainMemo.clear()
            lastMemoClearTs = now
        }
    }

    /**
     * Clear in-memory caches for chain resolution and member type memoization.
     * Optionally restarts the daemon analyzer for immediate refresh.
     */
    @JvmStatic
    fun clearCaches(project: Project? = null) {
        chainMemo.clear()
        lastMemoClearTs = System.currentTimeMillis()
        try {
            MemberResolutionCache.clearAll(project)
        } catch (_: Exception) {
            // best effort
        }
        try {
            InheritanceChainCache.clearAll(project)
        } catch (_: Exception) {
            // best effort
        }
        if (project != null) {
            try {
                DaemonCodeAnalyzer.getInstance(project).restart()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    // Reentrancy guard to avoid infinite resolution loops across handlers
    private val RESOLVE_IN_PROGRESS: Key<Boolean> = Key.create("pascal.member.resolve.in.progress")

    // Reentrancy guard for inline var type inference (prevents cycles when var A := B and B := A)
    private val inferenceInProgress: ThreadLocal<MutableSet<Int>> = ThreadLocal.withInitial { mutableSetOf() }

    /**
     * Result of resolving a member chain.
     */
    data class ChainResolutionResult(
        /** List of resolved elements for each part of the chain (null for unresolved) */
        val resolvedElements: List<PsiElement?>,
        /** The chain elements (identifiers) */
        val chainElements: List<PsiElement>,
        /** The origin file where resolution started */
        val originFile: PsiFile,
        /** Generic type argument substitution map for the last resolved type context.
         *  Maps formal type parameter names to actual type argument names.
         *  e.g., {T → TRide} when the qualifier has type TEntityList<TRide>. */
        val typeArgMap: Map<String, String> = emptyMap(),
        /** Per-element type argument maps. typeArgMaps[i] is the substitution map
         *  that was active when resolving chain element i (i.e., the owner's map).
         *  For example, for chain [LResult, ItemsById, RideId]:
         *  - typeArgMaps[0] = {} (no owner)
         *  - typeArgMaps[1] = {T → TRide} (LResult's type TEntityList<TRide>)
         *  - typeArgMaps[2] = {} (ItemsById resolved to TRide, which has no generics) */
        val typeArgMaps: List<Map<String, String>> = emptyList()
    ) {
        val isFullyResolved: Boolean
            get() = resolvedElements.none { it == null }

        val lastResolved: PsiElement?
            get() = resolvedElements.lastOrNull { it != null }

        /** Get the type arg map that was active when resolving the element at the given index.
         *  This is the owner's substitution context for that element. */
        fun getTypeArgMapForIndex(index: Int): Map<String, String> {
            return if (index in typeArgMaps.indices) typeArgMaps[index] else emptyMap()
        }
    }

    /**
     * Internal result from resolveChainElements, carrying both resolved elements and the
     * final generic type argument substitution map.
     */
    private data class ChainResolutionInternal(
        val resolvedElements: List<PsiElement?>,
        val finalTypeArgMap: Map<String, String>,
        /** Per-element type argument maps */
        val perElementTypeArgMaps: List<Map<String, String>> = emptyList()
    )

    /**
     * Resolve a member chain starting from an identifier.
     *
     * @param startElement The first identifier in the chain (or any identifier in the chain)
     * @return ChainResolutionResult containing resolved elements for each part
     */
    @JvmStatic
    fun resolveChain(startElement: PsiElement): ChainResolutionResult {
        val originFile = startElement.containingFile
        maybeLog("[MemberTraversal] resolveChain start element='${startElement.text}' file='${originFile?.name}'", originFile)
        val chain = collectChain(startElement)
        maybeLog("[MemberTraversal] collected chain size=${chain.size} parts=${chain.map { it.text }}", originFile)
        val internal = resolveChainElements(chain, originFile)
        maybeLog("[MemberTraversal] resolved chain parts=${internal.resolvedElements.map { it?.javaClass?.simpleName ?: "<unresolved>" }}", originFile)
        return ChainResolutionResult(
            resolvedElements = internal.resolvedElements,
            chainElements = chain,
            originFile = originFile,
            typeArgMap = internal.finalTypeArgMap,
            typeArgMaps = internal.perElementTypeArgMaps
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
        val start = System.nanoTime()
        try {
            if (element.getUserData(RESOLVE_IN_PROGRESS) == true) {
                maybeLog("[MemberTraversal] resolveElement: reentrancy guard active; skipping for '${element.text}'", element.containingFile)
                return null
            }
            element.putUserData(RESOLVE_IN_PROGRESS, true)
            try {
                val chain = collectChain(element)
                if (chain.isEmpty()) return null
                val filePath = element.containingFile?.virtualFile?.path
                val chainText = chain.joinToString(".") { it.text }
                val key = ChainKey(filePath, chain.first().textOffset, chainText)
                clearExpiredMemo()
                chainMemo[key]?.let { return it }

                maybeLog("[MemberTraversal] resolveChain start element='${element.text}' file='${element.containingFile.name}'", element.containingFile)
                maybeLog("[MemberTraversal] collected chain size=${chain.size} parts=${chain.map { it.text }}", element.containingFile)
                val internal = resolveChainElements(chain, element.containingFile)
                val myIndex = chain.indexOf(element)
                val targetIndex = if (myIndex >= 0) myIndex else chain.lastIndex
                val target = internal.resolvedElements.getOrNull(targetIndex)
                if (target != null) {
                    maybeLog("[MemberTraversal] resolved chain parts=${internal.resolvedElements.filterNotNull().map { it.javaClass.simpleName }}", element.containingFile)
                    // Only cache non-null targets to avoid NPE in ConcurrentHashMap
                    // Avoid caching partial results in dumb mode to prevent sticky under-resolution
                    if (!DumbService.isDumb(element.project)) {
                        chainMemo[key] = target
                    }
                }
                return target
            } finally {
                element.putUserData(RESOLVE_IN_PROGRESS, null)
            }
        } finally {
            PerformanceMetrics.record("MemberChainResolver.resolveElement", System.nanoTime() - start)
        }
    }

    /** Strip the Delphi escape prefix `&` from an identifier name. */
    private fun stripEscape(name: String): String =
        if (name.startsWith("&") && name.length > 1) name.substring(1) else name

    /**
     * PSI tree-walking fallback for finding variables/parameters.
     * Walks up from the element to find the enclosing routine(s), then searches
     * their formal parameters and local variable declarations by name.
     * This doesn't depend on stub indices, so it works even when stubs are stale.
     */
    private fun findVariableByPsiWalk(element: PsiElement, name: String): PascalVariableDefinition? {
        var parent = element.parent
        while (parent != null && parent !is com.intellij.psi.PsiFile) {
            if (parent is PascalRoutine) {
                // Search formal parameters
                val params = nl.akiar.pascal.psi.PsiUtil.findAllRecursive(
                    parent.node, nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER
                )
                for (paramNode in params) {
                    val varDefs = nl.akiar.pascal.psi.PsiUtil.findAllRecursive(
                        paramNode, nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_DEFINITION
                    )
                    for (varNode in varDefs) {
                        val varDef = varNode.psi as? PascalVariableDefinition ?: continue
                        if (varDef.name.equals(name, ignoreCase = true)) return varDef
                    }
                }
                // Search local variables in routine body
                val body = nl.akiar.pascal.psi.PsiUtil.findFirstRecursive(
                    parent.node, nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_BODY
                )
                if (body != null) {
                    val localVars = nl.akiar.pascal.psi.PsiUtil.findAllRecursive(
                        body, nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_DEFINITION
                    )
                    for (varNode in localVars) {
                        val varDef = varNode.psi as? PascalVariableDefinition ?: continue
                        if (varDef.name.equals(name, ignoreCase = true)) return varDef
                    }
                }
            }
            parent = parent.parent
        }
        return null
    }

    /**
     * Collect all identifiers in a member access chain.
     * For "a.b.c", returns [a, b, c]
     */
    private fun isChainIdentifier(element: PsiElement): Boolean {
        val elemType = element.node.elementType
        if (elemType == PascalTokenTypes.KW_SELF) return true
        if (elemType == PascalTokenTypes.KW_RESULT) return true
        for (idType in nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES) {
            if (elemType == idType) return true
        }
        return false
    }

    private fun collectChain(element: PsiElement): List<PsiElement> {
        // Collect identifiers following DOTs: qualifier.Member.Next
        // Also handles parenthesized calls: qualifier.Method(args).Next
        // And bracket access: qualifier.Prop[idx].Next
        // And angle bracket generics: qualifier.Method<T>.Next
        val parts = mutableListOf<PsiElement>()
        val diagLog = nl.akiar.pascal.log.UnitLogFilter.shouldLog(element.containingFile)
        // Walk backwards to find the first identifier of the chain
        var start: PsiElement = element
        var prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(element))
        while (prev != null) {
            if (diagLog) LOG.debug("[GenericChain] backward walk: prev='${prev.text}' type=${prev.node.elementType}")
            // Skip past parenthesized/bracketed expressions: Method(...) or Prop[...]
            if (prev.node.elementType == PascalTokenTypes.RPAREN) {
                prev = skipMatchedBackward(prev, PascalTokenTypes.LPAREN, PascalTokenTypes.RPAREN)
                if (prev != null) prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(prev))
                continue
            }
            if (prev.node.elementType == PascalTokenTypes.RBRACKET) {
                prev = skipMatchedBackward(prev, PascalTokenTypes.LBRACKET, PascalTokenTypes.RBRACKET)
                if (prev != null) prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(prev))
                continue
            }
            if (prev.node.elementType == PascalTokenTypes.GT && isLikelyGenericBracket(prev)) {
                if (diagLog) LOG.debug("[GenericChain] backward walk: attempting GT skip from '${prev.text}' at offset=${prev.textOffset}")
                val matched = skipMatchedBackward(prev, PascalTokenTypes.LT, PascalTokenTypes.GT)
                if (matched != null) {
                    if (diagLog) LOG.debug("[GenericChain] backward walk: GT matched LT at '${matched.text}' offset=${matched.textOffset}")
                    prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(matched))
                    continue
                } else {
                    if (diagLog) LOG.debug("[GenericChain] backward walk: GT has no matching LT, treating as chain boundary")
                    break
                }
            }
            if (prev.node.elementType != PascalTokenTypes.DOT) break
            // After finding a DOT, skip past any parens/brackets/angles before the identifier
            var beforeDot = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(prev))
            while (beforeDot != null) {
                if (beforeDot.node.elementType == PascalTokenTypes.RPAREN) {
                    beforeDot = skipMatchedBackward(beforeDot, PascalTokenTypes.LPAREN, PascalTokenTypes.RPAREN)
                    if (beforeDot != null) beforeDot = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(beforeDot))
                } else if (beforeDot.node.elementType == PascalTokenTypes.RBRACKET) {
                    beforeDot = skipMatchedBackward(beforeDot, PascalTokenTypes.LBRACKET, PascalTokenTypes.RBRACKET)
                    if (beforeDot != null) beforeDot = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(beforeDot))
                } else if (beforeDot.node.elementType == PascalTokenTypes.GT && isLikelyGenericBracket(beforeDot)) {
                    if (diagLog) LOG.debug("[GenericChain] after-dot backward: attempting GT skip at offset=${beforeDot.textOffset}")
                    val matched = skipMatchedBackward(beforeDot, PascalTokenTypes.LT, PascalTokenTypes.GT)
                    if (matched != null) {
                        if (diagLog) LOG.debug("[GenericChain] after-dot backward: GT matched LT at offset=${matched.textOffset}")
                        beforeDot = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(matched))
                    } else {
                        if (diagLog) LOG.debug("[GenericChain] after-dot backward: GT has no matching LT, stopping")
                        break
                    }
                } else {
                    break
                }
            }
            if (beforeDot == null || !isChainIdentifier(beforeDot)) break
            start = beforeDot
            prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(beforeDot))
        }
        // Walk forward collecting identifiers separated by DOT
        // (skipping parenthesized/bracketed/angle bracket expressions between identifiers)
        var current: PsiElement? = start
        while (current != null) {
            if (isChainIdentifier(current)) {
                parts.add(current)
            }
            var next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(current))
            if (diagLog && next != null) LOG.debug("[GenericChain] forward walk: after '${current.text}', next='${next.text}' type=${next.node.elementType}")
            // Skip past parenthesized/bracketed expressions
            if (next != null && next.node.elementType == PascalTokenTypes.LPAREN) {
                next = skipMatchedForward(next, PascalTokenTypes.LPAREN, PascalTokenTypes.RPAREN)
                if (next != null) next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(next))
            }
            if (next != null && next.node.elementType == PascalTokenTypes.LBRACKET) {
                next = skipMatchedForward(next, PascalTokenTypes.LBRACKET, PascalTokenTypes.RBRACKET)
                if (next != null) next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(next))
            }
            if (next != null && next.node.elementType == PascalTokenTypes.LT && isLikelyGenericBracket(next)) {
                if (diagLog) LOG.debug("[GenericChain] forward walk: attempting LT skip after '${current.text}'")
                val matched = skipMatchedForward(next, PascalTokenTypes.LT, PascalTokenTypes.GT)
                if (matched != null) {
                    if (diagLog) LOG.debug("[GenericChain] forward walk: LT matched GT at offset=${matched.textOffset}")
                    next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(matched))
                } else {
                    if (diagLog) LOG.debug("[GenericChain] forward walk: LT has no matching GT, treating as chain boundary")
                }
            }
            if (next == null || next.node.elementType != PascalTokenTypes.DOT) break
            current = skipForwardWhitespace(PsiTreeUtil.nextLeaf(next))
        }
        if (diagLog) LOG.debug("[GenericChain] collectChain result: [${parts.joinToString(", ") { "'${it.text}'" }}] from element='${element.text}'")
        return parts
    }

    private fun skipMatchedBackward(close: PsiElement,
                                     openType: com.intellij.psi.tree.IElementType,
                                     closeType: com.intellij.psi.tree.IElementType): PsiElement? {
        var depth = 1
        var cur = PsiTreeUtil.prevLeaf(close)
        while (cur != null && depth > 0) {
            if (cur.node.elementType == closeType) depth++
            else if (cur.node.elementType == openType) depth--
            if (depth == 0) return cur
            cur = PsiTreeUtil.prevLeaf(cur)
        }
        return null
    }

    private fun skipMatchedForward(open: PsiElement,
                                    openType: com.intellij.psi.tree.IElementType,
                                    closeType: com.intellij.psi.tree.IElementType): PsiElement? {
        var depth = 1
        var cur = PsiTreeUtil.nextLeaf(open)
        while (cur != null && depth > 0) {
            if (cur.node.elementType == openType) depth++
            else if (cur.node.elementType == closeType) depth--
            if (depth == 0) return cur
            cur = PsiTreeUtil.nextLeaf(cur)
        }
        return null
    }

    private fun skipBackwardWhitespace(element: PsiElement?): PsiElement? {
        var cur = element
        while (cur != null && cur.node.elementType == PascalTokenTypes.WHITE_SPACE) {
            cur = PsiTreeUtil.prevLeaf(cur)
        }
        return cur
    }

    private fun skipForwardWhitespace(element: PsiElement?): PsiElement? {
        var cur = element
        while (cur != null && cur.node.elementType == PascalTokenTypes.WHITE_SPACE) {
            cur = PsiTreeUtil.nextLeaf(cur)
        }
        return cur
    }

    /**
     * Determine whether a GT or LT token is likely a generic bracket rather than a comparison operator.
     * Uses surrounding token context to disambiguate.
     */
    private fun isLikelyGenericBracket(gtOrLt: PsiElement): Boolean {
        if (gtOrLt.node.elementType == PascalTokenTypes.GT) {
            // GT followed by DOT is a generic chain end: Method<T>.Next
            val next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(gtOrLt))
            if (next != null && next.node.elementType == PascalTokenTypes.DOT) return true
            // GT followed by expression context tokens → comparison, not generic
            if (next != null && isExpressionContextToken(next)) return false
            // Default for GT: not generic (safe default to avoid false positives)
            return false
        }

        if (gtOrLt.node.elementType == PascalTokenTypes.LT) {
            // LT preceded by identifier → likely generic
            val prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(gtOrLt))
            if (prev == null) return false
            if (isChainIdentifier(prev)) return true
            // LT preceded by operator/keyword → comparison
            return false
        }
        return false
    }

    /**
     * Check if a token is in an expression context where `>` or `<` would be comparison operators.
     */
    private fun isExpressionContextToken(element: PsiElement): Boolean {
        val t = element.node.elementType
        return t == PascalTokenTypes.KW_THEN || t == PascalTokenTypes.KW_DO ||
               t == PascalTokenTypes.KW_AND || t == PascalTokenTypes.KW_OR ||
               t == PascalTokenTypes.KW_NOT || t == PascalTokenTypes.KW_XOR ||
               t == PascalTokenTypes.SEMI || t == PascalTokenTypes.RPAREN ||
               t == PascalTokenTypes.RBRACKET
    }

    /**
     * Resolve a member chain starting from an identifier.
     *
     * @param startElement The first identifier in the chain (or any identifier in the chain)
     * @return ChainResolutionResult containing resolved elements for each part
     */
    private fun resolveChainElements(chain: List<PsiElement>, originFile: PsiFile): ChainResolutionInternal {
        val start = System.nanoTime()
        try {
            val results = MutableList<PsiElement?>(chain.size) { null }
            if (chain.isEmpty()) return ChainResolutionInternal(results, emptyMap())

            val cache = originFile.getUserData(FILE_RESOLUTION_CACHE) ?: LruCache<String, Any?>(100).also {
                originFile.putUserData(FILE_RESOLUTION_CACHE, it)
            }
            val chainText = chain.joinToString(".") { it.text }
            val modCount = originFile.manager.modificationTracker.modificationCount
            val cacheKey = "$chainText@$modCount"

            val cached = cache[cacheKey] as? ChainResolutionInternal
            if (cached != null && cached.resolvedElements.size == chain.size) {
                LOG.debug("[GenericChain] CACHE HIT for key='$cacheKey' resolved=[${cached.resolvedElements.joinToString(", ") { it?.javaClass?.simpleName ?: "<null>" }}]")
                return cached
            }

            val first = chain.first()
            maybeLog("[MemberTraversal] resolving first '${first.text}' at offset=${first.textOffset}", originFile)

            val usesInfo = nl.akiar.pascal.uses.PascalUsesClauseInfo.parse(originFile)
            var availableUnitsAtOffset = usesInfo.getAvailableUnits(first.textOffset)
            // System is implicitly available in Delphi — ensure it's in the available units list
            // so that tryResolveUnitPrefix can match "System.Default(...)" etc.
            if (availableUnitsAtOffset.none { it.equals("system", ignoreCase = true) }) {
                availableUnitsAtOffset = availableUnitsAtOffset + "system"
            }
            maybeLog("[MemberTraversal] uses at offset=${first.textOffset} size=${availableUnitsAtOffset.size} sample=${availableUnitsAtOffset.take(10)}", originFile)

            var startIndex = 1
            var currentType: PascalTypeDefinition? = null
            var currentOwnerName: String? = null
            // Generic type argument substitution map: maps formal type params to actual args
            // e.g., {T → TRide} when variable is TEntityList<TRide>
            var currentTypeArgMap: Map<String, String> = emptyMap()
            // Track per-element typeArgMaps: perElementMaps[i] = the map active when resolving element i
            val perElementMaps = MutableList<Map<String, String>>(chain.size) { emptyMap() }

            // Check for Self keyword as first element
            if (first.node.elementType == PascalTokenTypes.KW_SELF) {
                val containingClass = findContainingClass(first)
                results[0] = containingClass
                currentType = containingClass
                currentOwnerName = containingClass?.name
                maybeLog("[MemberTraversal] first resolved as Self -> class '${containingClass?.name}'", originFile)
            }
            // Check for Result keyword as first element
            else if (first.node.elementType == PascalTokenTypes.KW_RESULT) {
                val routine = findEnclosingFunction(first)
                results[0] = routine
                currentType = getTypeOf(routine, originFile)
                currentOwnerName = routine?.returnTypeName
                maybeLog("[MemberTraversal] first resolved as Result -> function '${routine?.name}' returnType='${routine?.returnTypeName}'", originFile)
            }
            // Try unit-qualified prefix (e.g. Spring.Collections.Lists.TList)
            else if (chain.size >= 2) {
                val unitPrefixResult = tryResolveUnitPrefix(chain, availableUnitsAtOffset, originFile.project)
                if (unitPrefixResult != null) {
                    val (prefixLen, unitPsiFile) = unitPrefixResult
                    for (idx in 0 until prefixLen) {
                        results[idx] = unitPsiFile
                    }
                    val memberName = stripEscape(chain[prefixLen].text)
                    val unitName = nl.akiar.pascal.psi.PsiUtil.getUnitName(unitPsiFile)
                    val globalMember = findGlobalMemberInUnit(memberName, unitName, originFile.project)
                    results[prefixLen] = globalMember
                    maybeLog("[MemberTraversal] unit-qualified: prefix=$prefixLen unitName=$unitName member=$memberName -> ${globalMember?.javaClass?.simpleName}", originFile)
                    currentType = when (globalMember) {
                        is PascalTypeDefinition -> globalMember
                        else -> getTypeOf(globalMember, originFile)
                    }
                    currentOwnerName = when (globalMember) {
                        is PascalVariableDefinition -> globalMember.typeName
                        is PascalProperty -> globalMember.typeName
                        is PascalRoutine -> globalMember.returnTypeName
                        is PascalTypeDefinition -> globalMember.name
                        else -> null
                    }
                    if (currentType != null) {
                        // Build generic substitution map from the owner's type name
                        val rawOwnerTypeName = when (globalMember) {
                            is PascalVariableDefinition -> globalMember.typeName
                            is PascalProperty -> globalMember.typeName
                            else -> null
                        }
                        if (rawOwnerTypeName != null) {
                            val (_, typeArgs) = parseTypeArguments(rawOwnerTypeName)
                            currentTypeArgMap = buildTypeArgMap(currentType, typeArgs)
                        }
                        currentOwnerName = currentType.name
                    }
                    startIndex = prefixLen + 1
                }
            }

            // Check for "as" type cast prefix: (expr as TType).Member
            // In this case the chain is [Member, ...] and we need to extract the cast type
            if (startIndex == 1 && results[0] == null) {
                val asCastType = tryResolveAsCastPrefix(first, originFile)
                if (asCastType != null) {
                    currentType = asCastType
                    currentOwnerName = asCastType.name
                    startIndex = 0
                    maybeLog("[MemberTraversal] as-cast prefix resolved to type '${asCastType.name}'", originFile)
                }
            }

            // Normal first-element resolution (if not handled by Self, unit prefix, or as-cast)
            if (startIndex == 1 && results[0] == null) {
                // Use scope-aware variable lookup (local > field > global) for proper anonymous routine support
                val firstName = stripEscape(first.text)
                val resolvedVar = nl.akiar.pascal.stubs.PascalVariableIndex.findVariableAtPosition(firstName, originFile, first.textOffset)
                val resolvedFirst: PsiElement? = if (resolvedVar != null) {
                    maybeLog("[MemberTraversal] first resolved as variable '${firstName}' (scope-aware)", originFile)
                    resolvedVar
                } else {
                    // PSI tree fallback: walk up to find matching formal parameter or local var
                    // in the enclosing routine. This handles cases where the stub index misses
                    // parameters (e.g., during incremental re-indexing or for implementation methods).
                    val psiWalkVar = findVariableByPsiWalk(first, firstName)
                    if (psiWalkVar != null) {
                        maybeLog("[MemberTraversal] first resolved as variable '${firstName}' (PSI walk fallback)", originFile)
                        psiWalkVar
                    } else {
                        val typeResult = nl.akiar.pascal.stubs.PascalTypeIndex.findTypesWithUsesValidation(firstName, originFile, first.textOffset)
                        typeResult.inScopeTypes.firstOrNull()?.also { maybeLog("[MemberTraversal] first resolved as type '${firstName}' -> ${it.name}", originFile) }
                            ?: run {
                                // Try routine index (function as first element in chain, e.g. CoStatus.HandleStatus)
                                val routineResult = PascalRoutineIndex.findRoutinesWithUsesValidation(firstName, originFile, first.textOffset)
                                routineResult.inScopeRoutines.firstOrNull()?.also {
                                    maybeLog("[MemberTraversal] first resolved as routine '${first.text}' -> returnType=${it.returnTypeName}", originFile)
                                }
                            }
                    }
                }
                results[0] = resolvedFirst

                currentType = when (resolvedFirst) {
                    is PascalTypeDefinition -> resolvedFirst  // Type used directly (e.g. TMyClass.Create)
                    else -> getTypeOf(resolvedFirst, originFile)
                }
                currentOwnerName = when (resolvedFirst) {
                    is PascalVariableDefinition -> resolvedFirst.typeName
                        ?: inferTypeFromInitializer(resolvedFirst, originFile)
                    is PascalProperty -> resolvedFirst.typeName
                    is PascalRoutine -> resolvedFirst.returnTypeName
                    is PascalTypeDefinition -> resolvedFirst.name
                    else -> null
                }
                if (resolvedFirst != null) {
                    maybeLog("[MemberTraversal] getTypeOf element='${resolvedFirst.javaClass.simpleName}' typeName='${(resolvedFirst as? PascalVariableDefinition)?.typeName ?: (resolvedFirst as? PascalProperty)?.typeName ?: "<null>"}'", originFile)
                }
                if (currentType != null) {
                    maybeLog("[MemberTraversal] typeOf lookup '${currentType.name}' in-scope=[${currentType.name}] out-of-scope=[]", originFile)
                    maybeLog("[MemberTraversal] first type -> ${currentType.name}", originFile)
                    maybeLog("[MemberTraversal] confirm type index for '${currentType.name}': in-scope=[${currentType.name}] out-of-scope=[]", originFile)
                    // Build generic substitution map from the first element's type name
                    val rawFirstTypeName = when (resolvedFirst) {
                        is PascalVariableDefinition -> resolvedFirst.typeName
                            ?: inferTypeFromInitializer(resolvedFirst, originFile)
                        is PascalProperty -> resolvedFirst.typeName
                        is PascalRoutine -> resolvedFirst.returnTypeName
                        else -> null
                    }
                    if (rawFirstTypeName != null) {
                        val (_, typeArgs) = parseTypeArguments(rawFirstTypeName)
                        currentTypeArgMap = buildTypeArgMap(currentType, typeArgs)
                        if (currentTypeArgMap.isNotEmpty()) {
                            maybeLog("[MemberTraversal] generic substitution map: $currentTypeArgMap", originFile)
                        }
                    }
                    currentOwnerName = currentType.name
                }
                LOG.debug("[GenericChain] first element resolved: '${first.text}' -> ${resolvedFirst?.javaClass?.simpleName ?: "<null>"} type=${currentType?.name ?: "<null>"} ownerName=$currentOwnerName typeArgMap=$currentTypeArgMap")

                // Fallback: check if first element is a generic type parameter with a constraint
                if (resolvedFirst == null && chain.size >= 2) {
                    val constraintType = resolveTypeParameterConstraint(firstName, first, originFile)
                    if (constraintType != null) {
                        currentType = constraintType
                        currentOwnerName = constraintType.name
                        results[0] = constraintType
                        maybeLog("[MemberTraversal] first resolved as type parameter '$firstName' -> constraint '${constraintType.name}'", originFile)
                    }
                }

                // If first element has brackets [i], resolve through the indexer to get element type
                if (chain.isNotEmpty() && hasBracketsAfter(chain[0]) && currentType != null) {
                    val indexerResult = resolveIndexerElementType(currentType, currentTypeArgMap, originFile)
                    if (indexerResult != null) {
                        val (newType, newOwner, newMap) = indexerResult
                        if (newType != null) {
                            LOG.debug("[GenericChain] bracket indexer on first element: ${currentType.name}[i] -> ${newType.name} typeArgMap=$newMap")
                            currentType = newType
                            currentOwnerName = newOwner ?: newType.name
                            currentTypeArgMap = newMap
                        }
                    }
                }
            }

            // If the first element's type couldn't be resolved (e.g., Result with return type "T"),
            // check if the owner name is a generic type parameter and substitute with the constraint type.
            // This runs after ALL first-element resolution paths (Self, Result, normal, etc.)
            if (currentType == null && !currentOwnerName.isNullOrBlank() && chain.size >= 2) {
                val constraintType = resolveTypeParameterConstraint(currentOwnerName!!, first, originFile)
                if (constraintType != null) {
                    currentType = constraintType
                    currentOwnerName = constraintType.name
                    maybeLog("[MemberTraversal] owner type parameter substitution: '${currentOwnerName}' -> '${constraintType.name}'", originFile)
                }
            }

            // Resolve subsequent chain parts using member lookup on currentType
            for (i in startIndex until chain.size) {
                // Record the typeArgMap that is active for this element (the owner's context)
                perElementMaps[i] = currentTypeArgMap
                val name = stripEscape(chain[i].text)
                LOG.debug("[GenericChain] resolving step[$i] member='$name' currentType=${currentType?.name ?: "<null>"} currentOwnerName=$currentOwnerName typeArgMap=$currentTypeArgMap")
                var member: PsiElement? = null
                if (currentType != null) {
                    member = findMemberInType(currentType, name, originFile, includeAncestors = true)
                    LOG.debug("[GenericChain] findMemberInType(owner=${currentType.name}, member=$name) -> ${member?.javaClass?.simpleName ?: "<none>"}")
                } else if (!currentOwnerName.isNullOrBlank()) {
                    val propResult = PascalPropertyIndex.findPropertiesWithUsesValidation(name, originFile, chain[i].textOffset)
                    member = propResult.inScopeProperties.firstOrNull { p -> p.containingClass?.name.equals(currentOwnerName, ignoreCase = true) }
                    if (member == null) {
                        val routineResult = PascalRoutineIndex.findRoutinesWithUsesValidation(name, originFile, chain[i].textOffset)
                        member = routineResult.inScopeRoutines.firstOrNull { r -> r.containingClassName != null && r.containingClassName.equals(currentOwnerName, ignoreCase = true) }
                    }
                    if (member == null) {
                        member = resolveMemberByOwnerScan(currentOwnerName!!, name, originFile)
                        if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                            LOG.info("[MemberTraversal][Diag] resolveMemberByOwnerScan(owner=${currentOwnerName}, name=${name}) -> ${member?.javaClass?.simpleName ?: "<none>"}")
                        }
                    }
                    if (member != null) {
                        maybeLog("[MemberTraversal] member '$name' by owner='$currentOwnerName' -> ${member.javaClass.simpleName}", originFile)
                    }
                }
                if (member != null) {
                    maybeLog("[MemberTraversal] member '$name' -> ${member.javaClass.simpleName}", originFile)
                }
                results[i] = member

                // Get the member's raw type name and apply generic substitution if applicable
                val memberRawTypeName = when (member) {
                    is PascalVariableDefinition -> member.typeName
                    is PascalProperty -> member.typeName
                    is PascalRoutine -> member.returnTypeName
                        ?: if (isConstructor(member)) currentType?.name else null
                    else -> null
                }
                LOG.debug("[GenericChain] step[$i] '$name' rawTypeName='$memberRawTypeName' currentTypeArgMap=$currentTypeArgMap")

                // Check for call-site generic type arguments on this chain element
                // e.g., Resolve<IMutationsRepository> → extract ["IMutationsRepository"]
                // and build a method-level typeArgMap if the member is a generic routine
                var effectiveTypeArgMap = currentTypeArgMap
                if (member is PascalRoutine) {
                    val callSiteArgs = extractCallSiteTypeArgs(chain[i])
                    if (callSiteArgs.isNotEmpty()) {
                        val routineTypeParams = getRoutineTypeParameters(member)
                        LOG.debug("[GenericChain] step[$i] '$name' generic method: callSiteArgs=$callSiteArgs routineTypeParams=$routineTypeParams")
                        if (routineTypeParams.isNotEmpty()) {
                            // Build method-level type arg map and merge with class-level map
                            val methodArgMap = mutableMapOf<String, String>()
                            methodArgMap.putAll(currentTypeArgMap)
                            for (j in callSiteArgs.indices) {
                                if (j < routineTypeParams.size) {
                                    methodArgMap[routineTypeParams[j]] = callSiteArgs[j]
                                }
                            }
                            effectiveTypeArgMap = methodArgMap
                            LOG.debug("[GenericChain] step[$i] '$name' merged typeArgMap=$effectiveTypeArgMap")
                            // Update the per-element map so doc provider sees the call-site substitution
                            perElementMaps[i] = effectiveTypeArgMap
                        }
                    }
                }

                val substitutedTypeName = if (memberRawTypeName != null && effectiveTypeArgMap.containsKey(memberRawTypeName)) {
                    val sub = effectiveTypeArgMap[memberRawTypeName]!!
                    LOG.debug("[GenericChain] step[$i] '$name' SUBSTITUTION: '$memberRawTypeName' -> '$sub'")
                    sub
                } else {
                    memberRawTypeName
                }

                currentType = if (substitutedTypeName != null) {
                    // Use the (possibly substituted) type name for lookup — also needed for
                    // constructors where returnTypeName is null but memberRawTypeName was set
                    getTypeOf(member, originFile, substitutedTypeName)
                } else {
                    getTypeOf(member, originFile)
                }

                // If type lookup failed and the type name is a generic type parameter, substitute with constraint
                if (currentType == null && substitutedTypeName != null) {
                    val constraintType = resolveTypeParameterConstraint(substitutedTypeName, chain[i], originFile)
                    if (constraintType != null) {
                        currentType = constraintType
                        maybeLog("[MemberTraversal] step[$i] type parameter substitution: '$substitutedTypeName' -> '${constraintType.name}'", originFile)
                    }
                }

                // Rebuild the type arg map for the new current type
                currentOwnerName = when (member) {
                    is PascalVariableDefinition -> member.typeName
                    is PascalProperty -> member.typeName
                    else -> currentType?.name
                }
                // If the resolved type itself has generic args (from substituted or raw name), rebuild the map
                val effectiveTypeName = substitutedTypeName ?: currentOwnerName
                if (effectiveTypeName != null && currentType != null) {
                    val (_, newTypeArgs) = parseTypeArguments(effectiveTypeName)
                    currentTypeArgMap = if (newTypeArgs.isNotEmpty()) {
                        buildTypeArgMap(currentType, newTypeArgs)
                    } else {
                        emptyMap()
                    }
                } else {
                    currentTypeArgMap = emptyMap()
                }

                LOG.debug("[GenericChain] step[$i] '$name' RESULT: member=${member?.javaClass?.simpleName ?: "<null>"} nextType=${currentType?.name ?: "<null>"} nextOwner=$currentOwnerName effectiveType=$effectiveTypeName nextTypeArgMap=$currentTypeArgMap")

                // If this chain element has brackets [i] after it, resolve through the indexer
                if (hasBracketsAfter(chain[i]) && currentType != null) {
                    val indexerResult = resolveIndexerElementType(currentType, currentTypeArgMap, originFile)
                    if (indexerResult != null) {
                        val (newType, newOwner, newMap) = indexerResult
                        if (newType != null) {
                            LOG.debug("[GenericChain] bracket indexer on step[$i]: ${currentType.name}[i] -> ${newType.name} typeArgMap=$newMap")
                            currentType = newType
                            currentOwnerName = newOwner ?: newType.name
                            currentTypeArgMap = newMap
                        }
                    }
                }
            }
            val internalResult = ChainResolutionInternal(results, currentTypeArgMap, perElementMaps)
            // Cache deterministic results, but skip caching during dumb mode to avoid
            // persisting under-resolved nulls from empty stub index returns.
            if (!DumbService.isDumb(originFile.project)) {
                cache[cacheKey] = internalResult
            }
            return internalResult
        } finally {
            PerformanceMetrics.record("MemberChainResolver.resolveChainElements", System.nanoTime() - start)
        }
    }

    private fun resolveTypeByUnitScan(typeName: String, originFile: PsiFile): PascalTypeDefinition? {
        // Skip expensive scanning during dumb mode
        if (DumbService.isDumb(originFile.project)) return null
        val project = originFile.project
        val unitCandidates = when (typeName.lowercase()) {
            "tstrings", "tstringlist" -> listOf("System.Classes", "Classes")
            "tobject" -> listOf("System")
            else -> emptyList()
        }
        // ...existing code...
        for (unit in unitCandidates) {
            val svc = nl.akiar.pascal.project.PascalProjectService.getInstance(project)
            val vf = svc.resolveUnit(unit, true)
            if (vf == null) {
                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                    LOG.info("[MemberTraversal][Diag] resolveTypeByUnitScan: unit '${unit}' not resolved")
                }
                continue
            }
            val psi = com.intellij.psi.PsiManager.getInstance(project).findFile(vf)
            if (psi == null) {
                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                    LOG.info("[MemberTraversal][Diag] resolveTypeByUnitScan: PSI null for unit '${unit}' path=${vf.path}")
                }
                continue
            }
            val types = PsiTreeUtil.findChildrenOfType(psi, PascalTypeDefinition::class.java)
            if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                LOG.info("[MemberTraversal][Diag] resolveTypeByUnitScan: scanning unit='${unit}' typesFound=${types.size}")
            }
            val hit = types.firstOrNull { it.name.equals(typeName, true) }
            if (hit != null) return hit
        }
        return null
    }

    private fun resolveMemberByOwnerScan(ownerName: String, memberName: String, originFile: PsiFile): PsiElement? {
        // Skip expensive scanning during dumb mode
        if (DumbService.isDumb(originFile.project)) return null
        val ownerLower = ownerName.lowercase()
        // ...existing code...
        val candidateUnits = when (ownerLower) {
            "tstrings", "tstringlist" -> listOf("System.Classes", "Classes")
            "tobject" -> listOf("System")
            else -> emptyList()
        }
        val project = originFile.project
        for (unit in candidateUnits) {
            val svc = nl.akiar.pascal.project.PascalProjectService.getInstance(project)
            val vf = svc.resolveUnit(unit, true)
            if (vf == null) {
                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                    LOG.info("[MemberTraversal][Diag] resolveMemberByOwnerScan: unit '${unit}' not resolved for owner='${ownerName}'")
                }
                continue
            }
            val psi = com.intellij.psi.PsiManager.getInstance(project).findFile(vf)
            if (psi == null) {
                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                    LOG.info("[MemberTraversal][Diag] resolveMemberByOwnerScan: PSI null for unit='${unit}'")
                }
                continue
            }
            val routines = PsiTreeUtil.findChildrenOfType(psi, PascalRoutine::class.java)
            if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                LOG.info("[MemberTraversal][Diag] resolveMemberByOwnerScan: unit='${unit}' routinesFound=${routines.size}")
            }
            val rHit = routines.firstOrNull { r -> r.name.equals(memberName, true) && (r.containingClassName?.equals(ownerName, true) == true || r.containingClass?.name?.equals(ownerName, true) == true) }
            if (rHit != null) return rHit
            val props = PsiTreeUtil.findChildrenOfType(psi, PascalProperty::class.java)
            if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                LOG.info("[MemberTraversal][Diag] resolveMemberByOwnerScan: unit='${unit}' propertiesFound=${props.size}")
            }
            val pHit = props.firstOrNull { p -> p.name.equals(memberName, true) && p.containingClass?.name?.equals(ownerName, true) == true }
            if (pHit != null) return pHit
        }
        return null
    }

    /**
     * Get the type definition for a resolved element.
     * @param typeNameOverride If provided, use this type name instead of the element's own type name.
     *                         Used for generic type parameter substitution.
     */
    private fun getTypeOf(element: PsiElement?, originFile: PsiFile, typeNameOverride: String? = null): PascalTypeDefinition? {
        if (element == null) return null
        val rawTypeName = typeNameOverride ?: when (element) {
            is PascalVariableDefinition -> element.typeName ?: inferTypeFromInitializer(element, originFile)
            is PascalProperty -> element.typeName
            is PascalRoutine -> element.returnTypeName  // Now uses stub-based return type
            else -> null
        }
        if (rawTypeName.isNullOrBlank()) return null

        // Strip generic arguments for index lookup: "TEntityList<TRide>" -> "TEntityList"
        val (typeName, _) = parseTypeArguments(rawTypeName)

        val contextFile = originFile
        if (typeName.equals("TStrings", ignoreCase = true)) {
            if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(contextFile)) {
                LOG.info("[MemberTraversal][Diag] getTypeOf(TStrings) enter [ctx=${contextFile.name}] element=${element.javaClass.simpleName}")
            }
        }

        val disableBuiltins = false

        // When typeNameOverride is provided, bypass the cache because the cache key
        // is based on the element's own type name, not the override.
        val computeType: () -> PascalTypeDefinition? = {
            if (typeName.equals("TStrings", ignoreCase = true)) {
                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(contextFile)) {
                    LOG.info("[MemberTraversal][Diag] getTypeOf(TStrings) compute start")
                }
            }
            val typeResult = PascalTypeIndex.findTypeWithTransitiveDeps(typeName, contextFile, element.textOffset)
            val inScope = typeResult.inScopeTypes.firstOrNull()
            if (inScope != null) {
                maybeLog("[MemberTraversal] typeOf(in-scope) '$typeName' -> ${inScope.name} (unit=${inScope.unitName}) [ctx=${contextFile.name}]", contextFile)
                inScope
            } else {
                val viaScope = typeResult.inScopeViaScopeNames.firstOrNull()
                if (viaScope != null) {
                    maybeLog("[MemberTraversal] typeOf(via-scope-names) '$typeName' -> ${viaScope.name} (unit=${viaScope.unitName}) [ctx=${contextFile.name}]", contextFile)
                    viaScope
                } else {
                    val direct = PascalTypeIndex.findTypesWithUsesValidation(typeName, contextFile, element.textOffset)
                    val directHit = direct.inScopeTypes.firstOrNull()
                    if (directHit != null) {
                        maybeLog("[MemberTraversal] typeOf(direct) '$typeName' -> ${directHit.name} (unit=${directHit.unitName}) [ctx=${contextFile.name}]", contextFile)
                        directHit
                    } else {
                        val resolved = PascalSymbolResolver.resolveType(typeName, contextFile, element.textOffset)
                        val td = resolved.resolvedType
                        if (td != null) {
                            maybeLog("[MemberTraversal] typeOf(resolver) '$typeName' -> ${td.name} (unit=${td.unitName}) [ctx=${contextFile.name}] uses=${resolved.usesInfo.getAvailableUnits(element.textOffset).take(5)} scopes=${resolved.scopes}", contextFile)
                            td
                        } else {
                            if (!disableBuiltins) {
                                val globalCandidates = PascalTypeIndex.findTypes(typeName, originFile.project)
                                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(contextFile)) {
                                    LOG.info("[MemberTraversal][Diag] getTypeOf('$typeName') global candidates=${globalCandidates.size}")
                                }
                                val builtin = globalCandidates.firstOrNull { it.unitName.equals("System.Classes", true) || it.unitName.equals("System", true) || it.unitName.equals("Classes", true) }
                                if (builtin != null) {
                                    maybeLog("[MemberTraversal] typeOf(global) '$typeName' -> ${builtin.name} (unit=${builtin.unitName}) [ctx=${contextFile.name}]", contextFile)
                                    builtin
                                } else {
                                    val scanned = resolveTypeByUnitScan(typeName, originFile)
                                    if (scanned != null) {
                                        maybeLog("[MemberTraversal] typeOf(scan) '$typeName' -> ${scanned.name} (unit=${scanned.unitName}) [ctx=${contextFile.name}]", contextFile)
                                        scanned
                                    } else {
                                        if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(contextFile)) {
                                            // when disableBuiltins is false and no candidates were found
                                        }
                                        maybeLog("[MemberTraversal] typeOf '$typeName' -> <none> [ctx=${contextFile.name}]", contextFile)
                                        null
                                    }
                                }
                            } else {
                                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(contextFile)) {
                                    LOG.info("[MemberTraversal][Diag] built-in/global/scan fallback disabled for '$typeName'")
                                }
                                maybeLog("[MemberTraversal] typeOf '$typeName' -> <none> [ctx=${contextFile.name}]", contextFile)
                                null
                            }
                        }
                    }
                }
            }
        }

        val resolved = if (typeNameOverride != null) {
            // Bypass cache: type name override changes the meaning of the lookup
            computeType()
        } else {
            MemberResolutionCache.getOrComputeTypeOf(element, originFile, contextFile, computeType)
        }

        // Unwrap PROCEDURAL types (function references) to their return type.
        // When code says `FuncRefVar.Member`, Delphi implicitly calls the function
        // and accesses Member on the result.
        if (resolved != null && resolved.typeKind == TypeKind.PROCEDURAL) {
            val returnTypeName = (resolved as? PascalTypeDefinitionImpl)?.proceduralReturnTypeName
            if (returnTypeName != null) {
                val (unwrappedName, _) = parseTypeArguments(returnTypeName)
                val unwrapped = PascalTypeIndex.findTypeWithTransitiveDeps(unwrappedName, contextFile, element.textOffset)
                val unwrappedType = unwrapped.inScopeTypes.firstOrNull()
                    ?: PascalTypeIndex.findTypesWithUsesValidation(unwrappedName, contextFile, element.textOffset).inScopeTypes.firstOrNull()
                if (unwrappedType != null) {
                    maybeLog("[MemberTraversal] unwrapped PROCEDURAL '${resolved.name}' -> return type '${unwrappedType.name}'", contextFile)
                    return unwrappedType
                }
            }
        }

        return resolved
    }

    /**
     * Public entry point for getting the inferred type of an inline variable.
     * Used by documentation provider to show inferred types.
     */
    @JvmStatic
    fun getInferredTypeOf(varDef: PascalVariableDefinition, originFile: PsiFile): PascalTypeDefinition? {
        if (!varDef.typeName.isNullOrBlank()) return null // has explicit type
        val inferredTypeName = inferTypeFromInitializer(varDef, originFile) ?: return null
        val typeResult = PascalTypeIndex.findTypeWithTransitiveDeps(inferredTypeName, originFile, varDef.textOffset)
        return typeResult.inScopeTypes.firstOrNull()
            ?: PascalTypeIndex.findTypesWithUsesValidation(inferredTypeName, originFile, varDef.textOffset).inScopeTypes.firstOrNull()
    }

    /**
     * Public entry point for getting the inferred type NAME of an inline variable.
     * Unlike [getInferredTypeOf], this returns the raw type name string, which works
     * for primitive types like "string" and "Integer" that have no PascalTypeDefinition in the index.
     */
    @JvmStatic
    fun getInferredTypeName(varDef: PascalVariableDefinition, originFile: PsiFile): String? {
        if (!varDef.typeName.isNullOrBlank()) return null // has explicit type
        return inferTypeFromInitializer(varDef, originFile)
    }

    private const val NIL_SENTINEL = "\u0000NIL"

    /**
     * Map a literal token element type to its Delphi type name.
     * Returns [NIL_SENTINEL] for nil (caller must handle).
     */
    private fun literalTypeForElementType(elementType: com.intellij.psi.tree.IElementType): String? {
        return when (elementType) {
            PascalTokenTypes.STRING_LITERAL, PascalTokenTypes.CHAR_LITERAL -> "string"
            PascalTokenTypes.INTEGER_LITERAL, PascalTokenTypes.HEX_LITERAL -> "Integer"
            PascalTokenTypes.FLOAT_LITERAL -> "Extended"
            PascalTokenTypes.KW_TRUE, PascalTokenTypes.KW_FALSE -> "Boolean"
            PascalTokenTypes.KW_NIL -> NIL_SENTINEL
            else -> null
        }
    }

    /**
     * Check if an identifier text is a known boolean literal (sonar-delphi may parse True/False as identifiers).
     */
    private fun isBooleanLiteralText(text: String): Boolean {
        return text.equals("True", ignoreCase = true) || text.equals("False", ignoreCase = true)
    }

    /**
     * Check if an AST node (or its descendants) is a literal and return the type name.
     * Returns [NIL_SENTINEL] for nil, null if not a literal.
     * Sonar-delphi wraps literals in expression nodes: PRIMARY_EXPRESSION → NAME_REFERENCE → TRUE/NIL etc.
     */
    private fun inferTypeFromLiteral(node: com.intellij.lang.ASTNode): String? {
        // Direct literal token
        literalTypeForElementType(node.elementType)?.let { return it }
        if (node.elementType == PascalTokenTypes.IDENTIFIER && isBooleanLiteralText(node.text)) return "Boolean"

        // Check if this is a simple expression wrapping a single literal
        // Only descend if the node text is short (a literal won't be a complex expression)
        val text = node.text
        if (text.length <= 20) {
            // Walk down through firstChild chain (PRIMARY_EXPRESSION → NAME_REFERENCE → TRUE)
            var cur = node.firstChildNode
            while (cur != null) {
                literalTypeForElementType(cur.elementType)?.let { return it }
                if (cur.elementType == PascalTokenTypes.IDENTIFIER && isBooleanLiteralText(cur.text)) return "Boolean"
                // Only descend into single-child nodes (a real literal expression has one child per level)
                cur = if (cur.treeNext == null) cur.firstChildNode else null
            }
        }
        return null // not a literal
    }

    /**
     * Infer the type of an inline variable from its initializer expression.
     * For `var X := SomeFunc()`, resolves SomeFunc and returns its return type name.
     * For `var X := SomeVar`, resolves SomeVar and returns its type name.
     * For `var X := SomeObj.Method.Prop`, resolves the full member chain.
     * For `var X := 'hello'`, returns "string".
     */
    private fun inferTypeFromInitializer(varDef: PascalVariableDefinition, originFile: PsiFile): String? {
        // Reentrancy guard: prevent cycles (var A := B; var B := A)
        val guard = inferenceInProgress.get()
        val offset = varDef.textOffset
        if (!guard.add(offset)) return null
        try {
            return inferTypeFromInitializerImpl(varDef, originFile)
        } finally {
            guard.remove(offset)
        }
    }

    private fun inferTypeFromInitializerImpl(varDef: PascalVariableDefinition, originFile: PsiFile): String? {
        // Check for-in loop first: `for var LItem in Collection do` — infer from Collection's element type
        val forInType = inferTypeFromForInCollection(varDef, originFile)
        if (forInType != null) return forInType

        // Walk AST siblings after the variable definition looking for ASSIGN (:=)
        val node = varDef.node ?: return null
        val parentNode = node.treeParent ?: return null

        var foundSelf = false
        var foundAssign = false
        var child = parentNode.firstChildNode
        while (child != null) {
            if (!foundSelf) {
                if (child == node) foundSelf = true
                child = child.treeNext
                continue
            }
            val elementType = child.elementType
            if (elementType == PascalTokenTypes.ASSIGN) {
                foundAssign = true
                child = child.treeNext
                continue
            }
            if (foundAssign) {
                // Skip whitespace/comments
                if (elementType == PascalTokenTypes.WHITE_SPACE ||
                    elementType == PascalTokenTypes.LINE_COMMENT ||
                    elementType == PascalTokenTypes.BLOCK_COMMENT) {
                    child = child.treeNext
                    continue
                }
                // Found the first meaningful node after :=

                // Check for literals first (strings, integers, booleans, nil)
                val literalType = inferTypeFromLiteral(child)
                if (literalType != null) {
                    if (literalType == NIL_SENTINEL) {
                        maybeLog("[MemberTraversal] inferType: nil literal, no type inferred", originFile)
                        return null
                    }
                    maybeLog("[MemberTraversal] inferType: literal detected, type='$literalType'", originFile)
                    return literalType
                }

                // Find the first identifier to use as chain start
                val identifierNode = if (elementType == PascalTokenTypes.IDENTIFIER) {
                    child
                } else {
                    nl.akiar.pascal.psi.PsiUtil.findFirstRecursiveAnyOf(
                        child,
                        PascalTokenTypes.IDENTIFIER
                    )
                }
                if (identifierNode != null) {
                    val identifierPsi = identifierNode.psi
                    maybeLog("[MemberTraversal] inferType: collecting chain from '${identifierNode.text}'", originFile)

                    // Use collectChain + resolveChainElements to resolve the full member chain
                    val chain = collectChain(identifierPsi)
                    if (chain.isNotEmpty()) {
                        val resolved = resolveChainElements(chain, originFile)
                        // Extract type name from the LAST resolved element
                        val lastResolvedIndex = resolved.resolvedElements.indexOfLast { it != null }
                        val lastResolved = if (lastResolvedIndex >= 0) resolved.resolvedElements[lastResolvedIndex] else null
                        if (lastResolved != null) {
                            var typeName = when (lastResolved) {
                                is PascalRoutine -> lastResolved.returnTypeName
                                    ?: if (isConstructor(lastResolved) && lastResolvedIndex > 0) {
                                        // Constructor: infer type from preceding chain element (the class name)
                                        val prevElement = resolved.resolvedElements.getOrNull(lastResolvedIndex - 1)
                                        when (prevElement) {
                                            is PascalTypeDefinition -> {
                                                extractTypeNameWithGenericsFromChain(chain, lastResolvedIndex - 1)
                                                    ?: prevElement.name
                                            }
                                            else -> null
                                        }
                                    } else null
                                is PascalVariableDefinition -> lastResolved.typeName
                                is PascalProperty -> lastResolved.typeName
                                is PascalTypeDefinition -> lastResolved.name
                                else -> null
                            }
                            // Apply generic type argument substitution using per-element map
                            // (the map active when that element was resolved, i.e. the owner's context)
                            val activeTypeArgMap = if (lastResolvedIndex in resolved.perElementTypeArgMaps.indices)
                                resolved.perElementTypeArgMaps[lastResolvedIndex]
                            else
                                resolved.finalTypeArgMap
                            if (typeName != null && activeTypeArgMap.containsKey(typeName)) {
                                val substituted = activeTypeArgMap[typeName]
                                maybeLog("[MemberTraversal] inferType: substituting generic '$typeName' -> '$substituted'", originFile)
                                typeName = substituted
                            }
                            if (!typeName.isNullOrBlank()) {
                                maybeLog("[MemberTraversal] inferType: chain resolved to '${lastResolved.javaClass.simpleName}', typeName='$typeName'", originFile)
                                return typeName
                            }
                        }
                    }
                }
                break
            }
            child = child.treeNext
        }
        return null
    }

    /**
     * Infer the element type for a for-in loop variable.
     * For `for var LItem in AList` where AList is `TList<TMyClass>`, returns "TMyClass".
     * Uses three strategies in order:
     *  A) Generic type argument extraction (IList<T>, TList<T>, TArray<T>)
     *  B) GetEnumerator().Current resolution (non-generic iterables)
     *  C) Hardcoded string → Char
     */
    private fun inferTypeFromForInCollection(varDef: PascalVariableDefinition, originFile: PsiFile): String? {
        // Only apply to the loop variable itself (direct child of the for-statement),
        // not to variables declared inside the loop body
        val forStmt = varDef.parent as? PascalForStatement ?: return null
        if (!forStmt.isForIn) return null

        // Find the iterable expression: the first meaningful element after 'in' and before 'do'
        // We walk children directly because getIterableExpression() requires PascalExpression interface
        var iterableNode: com.intellij.lang.ASTNode? = null
        var foundIn = false
        var child = forStmt.node.firstChildNode
        while (child != null) {
            if (child.elementType == PascalTokenTypes.KW_IN) {
                foundIn = true
                child = child.treeNext
                continue
            }
            if (child.elementType == PascalTokenTypes.KW_DO) break
            if (foundIn && child.elementType != PascalTokenTypes.WHITE_SPACE) {
                iterableNode = child
                break
            }
            child = child.treeNext
        }
        if (iterableNode == null) return null

        maybeLog("[MemberTraversal] inferForIn: iterableExpr='${iterableNode.text}'", originFile)

        // Find the first identifier in the iterable expression
        val identNode = if (iterableNode.elementType == PascalTokenTypes.IDENTIFIER) iterableNode
            else nl.akiar.pascal.psi.PsiUtil.findFirstRecursiveAnyOf(iterableNode, PascalTokenTypes.IDENTIFIER)
        val firstIdent = identNode?.psi ?: return null

        // Resolve the iterable's type via chain resolution
        val chain = collectChain(firstIdent)
        if (chain.isEmpty()) return null
        val resolved = resolveChainElements(chain, originFile)
        val lastResolvedIndex = resolved.resolvedElements.indexOfLast { it != null }
        val lastResolved = if (lastResolvedIndex >= 0) resolved.resolvedElements[lastResolvedIndex] else null
        if (lastResolved == null) return null

        var iterableTypeName = when (lastResolved) {
            is PascalRoutine -> lastResolved.returnTypeName
            is PascalVariableDefinition -> lastResolved.typeName
                ?: inferTypeFromInitializer(lastResolved, originFile)
            is PascalProperty -> lastResolved.typeName
            is PascalTypeDefinition -> lastResolved.name
            else -> null
        }
        // Apply generic substitution from chain context
        val activeTypeArgMap = if (lastResolvedIndex in resolved.perElementTypeArgMaps.indices)
            resolved.perElementTypeArgMaps[lastResolvedIndex]
        else
            resolved.finalTypeArgMap
        if (iterableTypeName != null && activeTypeArgMap.containsKey(iterableTypeName)) {
            iterableTypeName = activeTypeArgMap[iterableTypeName]
        }
        if (iterableTypeName.isNullOrBlank()) return null

        maybeLog("[MemberTraversal] inferForIn: iterableTypeName='$iterableTypeName'", originFile)

        // Strategy C: string → Char
        if (iterableTypeName.equals("string", ignoreCase = true) ||
            iterableTypeName.equals("AnsiString", ignoreCase = true) ||
            iterableTypeName.equals("UnicodeString", ignoreCase = true)) {
            maybeLog("[MemberTraversal] inferForIn: string iteration → Char", originFile)
            return "Char"
        }

        // Strategy A: Generic type argument extraction
        val (baseName, typeArgs) = parseTypeArguments(iterableTypeName)
        if (typeArgs.isNotEmpty()) {
            val elementType = typeArgs[0]
            maybeLog("[MemberTraversal] inferForIn: generic arg → '$elementType'", originFile)
            return elementType
        }

        // Strategy B: GetEnumerator().Current resolution
        val iterableTypeDef = PascalTypeIndex.findTypeWithTransitiveDeps(baseName, originFile, varDef.textOffset)
            .inScopeTypes.firstOrNull()
            ?: PascalTypeIndex.findTypesWithUsesValidation(baseName, originFile, varDef.textOffset)
                .inScopeTypes.firstOrNull()
        if (iterableTypeDef != null) {
            val getEnumerator = findMemberInType(iterableTypeDef, "GetEnumerator", originFile, true)
            if (getEnumerator is PascalRoutine) {
                val enumeratorTypeName = getEnumerator.returnTypeName
                if (!enumeratorTypeName.isNullOrBlank()) {
                    val enumeratorTypeDef = PascalTypeIndex.findTypeWithTransitiveDeps(enumeratorTypeName, originFile, varDef.textOffset)
                        .inScopeTypes.firstOrNull()
                        ?: PascalTypeIndex.findTypesWithUsesValidation(enumeratorTypeName, originFile, varDef.textOffset)
                            .inScopeTypes.firstOrNull()
                    if (enumeratorTypeDef != null) {
                        val currentMember = findMemberInType(enumeratorTypeDef, "Current", originFile, true)
                        val currentTypeName = when (currentMember) {
                            is PascalProperty -> currentMember.typeName
                            is PascalRoutine -> currentMember.returnTypeName
                            is PascalVariableDefinition -> currentMember.typeName
                            else -> null
                        }
                        if (!currentTypeName.isNullOrBlank()) {
                            maybeLog("[MemberTraversal] inferForIn: GetEnumerator.Current → '$currentTypeName'", originFile)
                            return currentTypeName
                        }
                    }
                }
            }
        }

        return null
    }

    private fun findMemberInType(typeDef: PascalTypeDefinition, name: String, callSiteFile: PsiFile, includeAncestors: Boolean): PsiElement? {
        val project = callSiteFile.project
        val callSiteUnit = nl.akiar.pascal.psi.PsiUtil.getUnitName(callSiteFile)

        // Collect all owner type names using cached inheritance chain
        val owners = mutableListOf<PascalTypeDefinition>()
        owners.add(typeDef)
        if (includeAncestors) {
            owners.addAll(InheritanceChainCache.getAllAncestorTypes(typeDef))
        }

        LOG.debug("[GenericChain] findMemberInType: looking for '$name' in type='${typeDef.name}' unit='${typeDef.unitName}' owners=[${owners.joinToString(", ") { "${it.name}(${it.unitName})" }}]")

        for (ownerType in owners) {
            val ownerName = ownerType.name ?: continue
            val ownerUnit = ownerType.unitName ?: continue

            // 1. Try Scoped Indexes (Deterministic)
            // Fields
            val fieldKey = nl.akiar.pascal.stubs.PascalScopedMemberIndex.compositeKey(ownerUnit, ownerName, name, "field")
            val fields = com.intellij.psi.stubs.StubIndex.getElements(
                nl.akiar.pascal.stubs.PascalScopedMemberIndex.FIELD_KEY,
                fieldKey, project, com.intellij.psi.search.GlobalSearchScope.allScope(project),
                PascalVariableDefinition::class.java
            )
            val field = fields.firstOrNull { isVisible(it, callSiteFile, typeDef) }
            if (field != null) return field

            // Properties
            val propKey = nl.akiar.pascal.stubs.PascalScopedMemberIndex.compositeKey(ownerUnit, ownerName, name, "property")
            val props = com.intellij.psi.stubs.StubIndex.getElements(
                nl.akiar.pascal.stubs.PascalScopedMemberIndex.PROPERTY_KEY,
                propKey, project, com.intellij.psi.search.GlobalSearchScope.allScope(project),
                PascalProperty::class.java
            )
            val prop = props.firstOrNull { isVisible(it, callSiteFile, typeDef) }
            if (prop != null) return prop

            // Routines
            val routineKey = "${ownerUnit}#${ownerName}#${name}".lowercase()
            val routines = nl.akiar.pascal.stubs.PascalScopedRoutineIndex.find(routineKey, project)
            val routine = routines.firstOrNull { isVisible(it, callSiteFile, typeDef) }
            if (routine != null) return routine
        }

        // 2. Fallback: use PSI-based getMembers(true) which handles inheritance and works cross-unit
        if (!DumbService.isDumb(project)) {
            val allMembers = typeDef.getMembers(true)
            // Log with types for debugging property vs field vs routine
            val memberInfo = allMembers.joinToString(", ") { m ->
                val mName = when {
                    m is com.intellij.psi.PsiNameIdentifierOwner -> m.name ?: "?"
                    m.node?.elementType == nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT -> getEnumElementName(m) ?: "?"
                    else -> "?"
                }
                val mType = when (m) {
                    is PascalProperty -> "prop"
                    is PascalRoutine -> "routine"
                    is PascalVariableDefinition -> "field"
                    else -> if (m.node?.elementType == nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT) "enum" else m.javaClass.simpleName
                }
                "'$mName'($mType)"
            }
            LOG.debug("[GenericChain] findMemberInType PSI fallback for '$name' in '${typeDef.name}': ${allMembers.size} members: [$memberInfo]")
            for (m in allMembers) {
                val memberName = when {
                    m is com.intellij.psi.PsiNameIdentifierOwner -> m.name
                    m.node?.elementType == nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT -> getEnumElementName(m)
                    else -> null
                }
                if (memberName != null && memberName.equals(name, ignoreCase = true)) {
                    val vis = when (m) {
                        is PascalRoutine -> m.visibility
                        is PascalProperty -> m.visibility
                        is PascalVariableDefinition -> m.visibility
                        else -> null
                    }
                    if (isVisible(m, callSiteFile, typeDef)) {
                        return m
                    } else {
                        LOG.debug("[GenericChain] findMemberInType: found '$name' but NOT visible (visibility='$vis')")
                    }
                }
            }
        }

        // 3. Fallback: try validated property/routine index with containingClass filter
        // This catches properties that are missed by PSI tree walking (e.g., due to parser quirks)
        if (!DumbService.isDumb(project)) {
            val propResult = PascalPropertyIndex.findPropertiesWithUsesValidation(name, callSiteFile, callSiteFile.textLength.coerceAtMost(1))
            val validatedProps = propResult.inScopeProperties
            LOG.debug("[GenericChain] findMemberInType step 3: validated property index for '$name' returned ${validatedProps.size} results: [${validatedProps.joinToString(", ") { "'${it.name}' in ${it.containingClassName}(${it.unitName})" }}]")
            for (ownerType in owners) {
                val ownerName = ownerType.name ?: continue
                val prop = validatedProps.firstOrNull { p ->
                    p.containingClassName.equals(ownerName, ignoreCase = true) && isVisible(p, callSiteFile, typeDef)
                }
                if (prop != null) {
                    LOG.debug("[GenericChain] findMemberInType: found '$name' via validated property index in owner='$ownerName'")
                    return prop
                }
                val routineResult = PascalRoutineIndex.findRoutinesWithUsesValidation(name, callSiteFile, callSiteFile.textLength.coerceAtMost(1))
                val validatedRoutines = routineResult.inScopeRoutines
                val routine = validatedRoutines.firstOrNull { r ->
                    (r.containingClassName?.equals(ownerName, ignoreCase = true) == true ||
                     r.containingClass?.name?.equals(ownerName, ignoreCase = true) == true) &&
                    isVisible(r, callSiteFile, typeDef)
                }
                if (routine != null) {
                    LOG.debug("[GenericChain] findMemberInType: found '$name' via validated routine index in owner='$ownerName'")
                    return routine
                }
            }
        }

        LOG.debug("[GenericChain] findMemberInType: NO MATCH for '$name' in type='${typeDef.name}' unit='${typeDef.unitName}' ancestors=[${if (owners.size > 1) owners.drop(1).joinToString(", ") { "${it.name}(${it.unitName})" } else "none"}]")
        return null
    }

    private fun getEnumElementName(element: PsiElement): String? {
        // ENUM_ELEMENT nodes may be leaf nodes with no children; use text directly
        for (child in element.children) {
            if (child.node?.elementType == PascalTokenTypes.IDENTIFIER) {
                return child.text
            }
        }
        // Strip ordinal assignment: "askForMileageMode_Always = 2" → "askForMileageMode_Always"
        val text = element.text
        val eqIdx = text.indexOf('=')
        return if (eqIdx > 0) text.substring(0, eqIdx).trim() else text.trim()
    }

    private fun isConstructor(routine: PascalRoutine): Boolean {
        val node = routine.node ?: return false
        var child = node.firstChildNode
        while (child != null) {
            val type = child.elementType
            if (type == PascalTokenTypes.KW_CONSTRUCTOR) return true
            if (type == PascalTokenTypes.KW_PROCEDURE ||
                type == PascalTokenTypes.KW_FUNCTION ||
                type == PascalTokenTypes.KW_DESTRUCTOR) return false
            child = child.treeNext
        }
        return false
    }

    /**
     * Extract a type name with generic arguments from the chain PSI.
     * e.g., for chain element "TDictionary" followed by "<Integer, TTaskTypeInfo>",
     * returns "TDictionary<Integer, TTaskTypeInfo>".
     */
    private fun extractTypeNameWithGenericsFromChain(chain: List<PsiElement>, typeIndex: Int): String? {
        val typeElement = chain.getOrNull(typeIndex) ?: return null
        val next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(typeElement))
        if (next?.node?.elementType == PascalTokenTypes.LT) {
            val gt = skipMatchedForward(next, PascalTokenTypes.LT, PascalTokenTypes.GT)
            if (gt != null) {
                val startOffset = typeElement.textRange.startOffset
                val endOffset = gt.textRange.endOffset
                return typeElement.containingFile.text.substring(startOffset, endOffset)
            }
        }
        return null
    }

    /**
     * Check if a chain element is followed by bracket indexer `[...]` in the source.
     * Skips generic args and parenthesized expressions first.
     */
    private fun hasBracketsAfter(element: PsiElement): Boolean {
        var next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(element))
        // Skip generic args <...>
        if (next?.node?.elementType == PascalTokenTypes.LT && isLikelyGenericBracket(next)) {
            val matched = skipMatchedForward(next, PascalTokenTypes.LT, PascalTokenTypes.GT)
            if (matched != null) next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(matched))
        }
        // Skip parenthesized expressions (...)
        if (next?.node?.elementType == PascalTokenTypes.LPAREN) {
            val matched = skipMatchedForward(next, PascalTokenTypes.LPAREN, PascalTokenTypes.RPAREN)
            if (matched != null) next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(matched))
        }
        return next?.node?.elementType == PascalTokenTypes.LBRACKET
    }

    /**
     * Resolve the element type when brackets `[i]` are applied to a collection type.
     * Finds the default indexed property (typically "Items") and returns its type after
     * generic substitution.
     */
    private fun resolveIndexerElementType(
        currentType: PascalTypeDefinition,
        typeArgMap: Map<String, String>,
        originFile: PsiFile
    ): Triple<PascalTypeDefinition?, String?, Map<String, String>>? {
        // Find the default indexed property — in Delphi this is conventionally "Items"
        val itemsMember = findMemberInType(currentType, "Items", originFile, includeAncestors = true)
        val rawTypeName = when (itemsMember) {
            is PascalProperty -> itemsMember.typeName
            is PascalRoutine -> itemsMember.returnTypeName
            else -> null
        } ?: return null

        // Apply generic substitution
        val substituted = typeArgMap[rawTypeName] ?: rawTypeName

        val newType = getTypeOf(itemsMember, originFile, substituted)
        val (_, typeArgs) = parseTypeArguments(substituted)
        val newMap = if (newType != null && typeArgs.isNotEmpty()) {
            buildTypeArgMap(newType, typeArgs)
        } else {
            emptyMap()
        }
        return Triple(newType, substituted, newMap)
    }

    private fun isVisible(member: PsiElement, callSiteFile: PsiFile, searchContextType: PascalTypeDefinition? = null): Boolean {
        val visibility = when (member) {
            is PascalRoutine -> member.visibility
            is PascalProperty -> member.visibility
            is PascalVariableDefinition -> member.visibility
            else -> null
        } ?: "public"

        if (visibility.equals("public", true) || visibility.equals("published", true)) return true

        val memberUnit = when (member) {
            is PascalRoutine -> member.unitName
            is PascalProperty -> member.unitName
            is PascalVariableDefinition -> member.unitName
            else -> nl.akiar.pascal.psi.PsiUtil.getUnitName(member)
        }
        val callSiteUnit = nl.akiar.pascal.psi.PsiUtil.getUnitName(callSiteFile)

        if (memberUnit.equals(callSiteUnit, ignoreCase = true)) {
            // Delphi rules: private/protected are visible within the same unit
            return true
        }

        // Different unit: private/strict are hidden
        if (visibility.contains("private", ignoreCase = true)) return false

        // Protected: allow access if call site is inside a subclass of the member's declaring class
        if (visibility.contains("protected", ignoreCase = true)) {
            if (searchContextType != null) {
                val memberClassName = getContainingClassName(member)
                if (memberClassName != null && InheritanceChainCache.isDescendantOf(searchContextType, memberClassName)) {
                    return true
                }
            }
            return false
        }

        return true
    }

    private fun getContainingClassName(element: PsiElement): String? {
        return when (element) {
            is PascalRoutine -> element.containingClassName
            is PascalProperty -> element.containingClass?.name
            is PascalVariableDefinition -> element.containingClass?.name
            else -> null
        }
    }

    private fun getCallSiteClassName(callSiteFile: PsiFile, member: PsiElement): String? {
        // Find the class definition at the call site if any
        // For simplicity, we can look for the containing type definition of the call site
        // This is tricky because we don't have the exact call site offset here, just the file.
        // But MemberChainResolver.resolveChain is called with a PsiElement.
        // We might need to pass the call site element to isVisible.
        return null // Fallback
    }

    private fun isAtSubclassOf(callSiteFile: PsiFile, member: PsiElement): Boolean {
        // Implementation of subclass check
        // For now, return false to be safe, or implement using InheritanceChainCache
        val memberClassName = getContainingClassName(member) ?: return false
        // We would need to find which class at callSiteFile we are in.
        // This is complex without the exact element.
        // For Milestone B, let's keep it simple.
        return false
    }

    /**
     * Try to match a prefix of the chain against available unit names (greedy, longest first).
     * Returns Pair(prefixLength, unitPsiFile) or null if no match.
     */
    private fun tryResolveUnitPrefix(
        chain: List<PsiElement>,
        availableUnits: List<String>,
        project: Project
    ): Pair<Int, PsiFile>? {
        val maxPrefixLen = minOf(chain.size - 1, 5)
        for (len in maxPrefixLen downTo 1) {
            val candidate = chain.subList(0, len).joinToString(".") { it.text }
            val matchedUnit = availableUnits.firstOrNull { it.equals(candidate, ignoreCase = true) }
            if (matchedUnit != null) {
                val svc = nl.akiar.pascal.project.PascalProjectService.getInstance(project)
                val vf = svc.resolveUnit(matchedUnit, true) ?: continue
                val psiFile = PsiManager.getInstance(project).findFile(vf) ?: continue
                return Pair(len, psiFile)
            }
        }
        return null
    }

    /**
     * Find a global type/variable/routine within a specific unit.
     */
    @JvmStatic
    fun findGlobalMemberInUnit(name: String, targetUnitName: String?, project: Project): PsiElement? {
        if (targetUnitName == null) return null

        // Try types first
        val types = PascalTypeIndex.findTypes(name, project)
        val typeHit = types.firstOrNull { it.unitName.equals(targetUnitName, ignoreCase = true) }
        if (typeHit != null) return typeHit

        // Try variables
        val vars = PascalVariableIndex.findVariables(name, project)
        val varHit = vars.firstOrNull { it.unitName.equals(targetUnitName, ignoreCase = true) }
        if (varHit != null) return varHit

        // Try routines (global only - no containing class)
        val routines = PascalRoutineIndex.findRoutines(name, project)
        val routineHit = routines.firstOrNull {
            it.unitName.equals(targetUnitName, ignoreCase = true) && it.containingClassName.isNullOrBlank()
        }
        if (routineHit != null) return routineHit

        return null
    }

    /**
     * Extract call-site type arguments from the PSI tree following a chain identifier.
     * For `Resolve<IMutationsRepository>`, walking forward from the `Resolve` identifier
     * past `<` and collecting identifiers until the matching `>`.
     *
     * @return List of type argument strings, e.g., ["IMutationsRepository"]
     */
    private fun extractCallSiteTypeArgs(chainElement: PsiElement): List<String> {
        var next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(chainElement))
        if (next == null || next.node.elementType != PascalTokenTypes.LT || !isLikelyGenericBracket(next)) return emptyList()

        // Walk forward collecting the text between < and > with depth tracking
        val args = mutableListOf<String>()
        val currentArg = StringBuilder()
        var depth = 1
        var cur = PsiTreeUtil.nextLeaf(next)
        while (cur != null && depth > 0) {
            val type = cur.node.elementType
            when {
                type == PascalTokenTypes.LT -> {
                    depth++
                    currentArg.append("<")
                }
                type == PascalTokenTypes.GT -> {
                    depth--
                    if (depth > 0) currentArg.append(">")
                    // depth == 0 means we hit the closing >
                }
                type == PascalTokenTypes.COMMA && depth == 1 -> {
                    val arg = currentArg.toString().trim()
                    if (arg.isNotEmpty()) args.add(arg)
                    currentArg.clear()
                }
                type == PascalTokenTypes.WHITE_SPACE -> {
                    // skip
                }
                type == PascalTokenTypes.IDENTIFIER || type == PascalTokenTypes.DOT -> {
                    currentArg.append(cur.text)
                }
                else -> {
                    // Other tokens (e.g., nested type references) - append text
                    currentArg.append(cur.text)
                }
            }
            cur = PsiTreeUtil.nextLeaf(cur)
        }
        val lastArg = currentArg.toString().trim()
        if (lastArg.isNotEmpty()) args.add(lastArg)
        return args
    }

    /**
     * Extract formal type parameter names from a routine's AST.
     * For `class function Resolve<T>: T`, returns ["T"].
     * For `procedure Process<TKey, TValue>(...)`, returns ["TKey", "TValue"].
     */
    private fun getRoutineTypeParameters(routine: PascalRoutine): List<String> {
        val node = routine.node ?: return emptyList()
        val params = mutableListOf<String>()
        for (child in generateSequence(node.firstChildNode) { it.treeNext }) {
            if (child.elementType == nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER) {
                // Collect all identifiers within the generic parameter node
                collectIdentifiersFromNode(child, params)
            } else if (child.elementType == nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER_LIST ||
                       child.elementType == nl.akiar.pascal.psi.PascalElementTypes.RETURN_TYPE ||
                       child.elementType == PascalTokenTypes.COLON ||
                       child.elementType == PascalTokenTypes.SEMI) {
                break // Stop before parameter list or return type
            }
        }
        return params
    }

    private fun collectIdentifiersFromNode(node: com.intellij.lang.ASTNode, results: MutableList<String>) {
        if (node.elementType == PascalTokenTypes.IDENTIFIER) {
            results.add(node.text)
        }
        var child = node.firstChildNode
        while (child != null) {
            collectIdentifiersFromNode(child, results)
            child = child.treeNext
        }
    }

    /**
     * Parses "TEntityList<TRide>" → Pair("TEntityList", listOf("TRide"))
     * Parses "TEntityList" → Pair("TEntityList", emptyList())
     * Handles nested: "TDict<String, TList<Integer>>" → Pair("TDict", listOf("String", "TList<Integer>"))
     */
    private fun parseTypeArguments(typeName: String): Pair<String, List<String>> {
        val ltIdx = typeName.indexOf('<')
        if (ltIdx < 0) return typeName to emptyList()
        val baseName = typeName.substring(0, ltIdx)
        val gtIdx = typeName.lastIndexOf('>')
        if (gtIdx <= ltIdx) return typeName to emptyList()
        val argsStr = typeName.substring(ltIdx + 1, gtIdx)
        return baseName to splitGenericArgs(argsStr)
    }

    /**
     * Splits generic arguments at depth-0 commas.
     * "String, TList<Integer>" → ["String", "TList<Integer>"]
     */
    private fun splitGenericArgs(argsStr: String): List<String> {
        val args = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in argsStr.indices) {
            when (argsStr[i]) {
                '<' -> depth++
                '>' -> depth--
                ',' -> if (depth == 0) {
                    args.add(argsStr.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
        val last = argsStr.substring(start).trim()
        if (last.isNotEmpty()) args.add(last)
        return args
    }

    /**
     * Build a generic substitution map from a type's formal type parameters and actual type arguments.
     * E.g., for TEntityList<T: class, IEntity> with actual args <TRide>, returns {T → TRide}.
     * Note: constraint identifiers like IEntity are type parameters too, but we only map
     * the first N params to the N args provided.
     */
    private fun buildTypeArgMap(typeDef: PascalTypeDefinition?, typeArgs: List<String>): Map<String, String> {
        if (typeDef == null || typeArgs.isEmpty()) return emptyMap()
        val typeParams = typeDef.typeParameters
        if (typeParams.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (i in typeArgs.indices) {
            if (i < typeParams.size) {
                map[typeParams[i]] = typeArgs[i]
            }
        }
        return map
    }

    /**
     * Detect an "as" type cast prefix before the first chain element.
     * For `(expr as TType).Member`, the chain starts at `Member` and this method
     * walks backward to find the `)`, scans inside the parens for KW_AS, and
     * resolves the type name that follows it.
     */
    private fun tryResolveAsCastPrefix(firstChainElement: PsiElement, originFile: PsiFile): PascalTypeDefinition? {
        // Walk backward from the first chain element: expect DOT then RPAREN
        var prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(firstChainElement))
        if (prev == null || prev.node.elementType != PascalTokenTypes.DOT) return null
        prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(prev))
        if (prev == null || prev.node.elementType != PascalTokenTypes.RPAREN) return null

        // We have ")." before the first chain element. Scan backward inside the parens for KW_AS.
        val rparen = prev
        var depth = 1
        var cur = PsiTreeUtil.prevLeaf(rparen)
        var castTypeName: String? = null
        while (cur != null && depth > 0) {
            val elemType = cur.node.elementType
            if (elemType == PascalTokenTypes.RPAREN) {
                depth++
            } else if (elemType == PascalTokenTypes.LPAREN) {
                depth--
            } else if (depth == 1 && elemType == PascalTokenTypes.KW_AS) {
                // Found 'as' — collect the type name after it
                var typeNamePart = skipForwardWhitespace(PsiTreeUtil.nextLeaf(cur))
                val sb = StringBuilder()
                while (typeNamePart != null && typeNamePart != rparen) {
                    val partType = typeNamePart.node.elementType
                    if (partType == PascalTokenTypes.RPAREN) break
                    if (partType == PascalTokenTypes.IDENTIFIER || partType == PascalTokenTypes.DOT) {
                        sb.append(typeNamePart.text)
                    } else if (partType != PascalTokenTypes.WHITE_SPACE
                        && !(typeNamePart is com.intellij.psi.PsiWhiteSpace)
                        && !(typeNamePart is com.intellij.psi.PsiComment)) {
                        break
                    }
                    typeNamePart = PsiTreeUtil.nextLeaf(typeNamePart)
                }
                if (sb.isNotEmpty()) {
                    // Take just the last segment for type lookup (strip unit prefix)
                    val fullName = sb.toString()
                    castTypeName = if (fullName.contains('.')) fullName.substringAfterLast('.') else fullName
                }
                break
            }
            cur = PsiTreeUtil.prevLeaf(cur)
        }

        if (castTypeName == null) return null

        // Resolve the cast type
        val typeResult = PascalTypeIndex.findTypeWithTransitiveDeps(castTypeName, originFile, firstChainElement.textOffset)
        if (typeResult.inScopeTypes.isNotEmpty()) return typeResult.inScopeTypes.first()

        val globalTypes = PascalTypeIndex.findTypes(castTypeName, originFile.project)
        return globalTypes.firstOrNull()
    }

    /**
     * Find the enclosing function (routine with a return type) for the Result keyword.
     */
    private fun findEnclosingFunction(element: PsiElement): PascalRoutine? {
        var routine = PsiTreeUtil.getParentOfType(element, PascalRoutine::class.java)
        while (routine != null) {
            if (routine.returnTypeName != null) return routine
            routine = PsiTreeUtil.getParentOfType(routine, PascalRoutine::class.java)
        }
        return null
    }

    /**
     * Resolve a type parameter constraint for a given parameter name within enclosing routines/types.
     * For `function ToValidatedStruct<T: TStruct>`, resolving "T" returns the PascalTypeDefinition for TStruct.
     * For unconstrained type parameters, falls back to TObject.
     *
     * @return The constraint type definition, or null if the name is not a type parameter
     */
    private fun resolveTypeParameterConstraint(paramName: String, element: PsiElement, originFile: PsiFile): PascalTypeDefinition? {
        // Check enclosing routines for generic type parameters
        var routine = PsiTreeUtil.getParentOfType(element, PascalRoutine::class.java)
        while (routine != null) {
            var result = findConstraintInGenericParams(paramName, routine.node, originFile, element.textOffset)
            if (result != null) return result

            // Implementation routines may not have GENERIC_PARAMETER nodes — they're in the declaration.
            // Fall back to the declaration routine's GENERIC_PARAMETER children.
            if (routine.isImplementation) {
                val declaration = routine.declaration
                if (declaration != null) {
                    result = findConstraintInGenericParams(paramName, declaration.node, originFile, element.textOffset)
                    if (result != null) return result
                }
            }

            // Also check the containing class for type-level type parameters
            val cls = routine.containingClass
            if (cls != null) {
                val clsResult = findConstraintInGenericParams(paramName, cls.node, originFile, element.textOffset)
                if (clsResult != null) return clsResult
            }

            routine = PsiTreeUtil.getParentOfType(routine, PascalRoutine::class.java)
        }
        // Check if directly inside a type definition (not through a routine)
        val typeDef = PsiTreeUtil.getParentOfType(element, PascalTypeDefinition::class.java)
        if (typeDef != null) {
            val tdResult = findConstraintInGenericParams(paramName, typeDef.node, originFile, element.textOffset)
            if (tdResult != null) return tdResult
        }
        return null
    }

    /**
     * Scan GENERIC_PARAMETER children of a node for a type parameter matching paramName,
     * and return the resolved constraint type (or TObject if unconstrained).
     */
    private fun findConstraintInGenericParams(
        paramName: String,
        node: com.intellij.lang.ASTNode?,
        originFile: PsiFile,
        offset: Int
    ): PascalTypeDefinition? {
        if (node == null) return null
        for (child in generateSequence(node.firstChildNode) { it.treeNext }) {
            if (child.elementType == nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER) {
                // GENERIC_PARAMETER structure: IDENTIFIER(T) [COLON IDENTIFIER/TYPE_REFERENCE(TStruct)]
                var foundParam = false
                var constraintName: String? = null
                var pastColon = false
                var gpChild = child.firstChildNode
                while (gpChild != null) {
                    if (!pastColon && gpChild.elementType == PascalTokenTypes.IDENTIFIER &&
                        gpChild.text.equals(paramName, ignoreCase = true)) {
                        foundParam = true
                    }
                    if (foundParam && gpChild.elementType == PascalTokenTypes.COLON) {
                        pastColon = true
                    }
                    if (pastColon && gpChild.elementType == PascalTokenTypes.IDENTIFIER) {
                        constraintName = gpChild.text
                        break
                    }
                    if (pastColon && gpChild.elementType == nl.akiar.pascal.psi.PascalElementTypes.TYPE_REFERENCE) {
                        constraintName = gpChild.text.trim()
                        break
                    }
                    gpChild = gpChild.treeNext
                }
                if (foundParam && constraintName != null) {
                    val typeResult = PascalTypeIndex.findTypeWithTransitiveDeps(constraintName, originFile, offset)
                    return typeResult.inScopeTypes.firstOrNull()
                }
                if (foundParam) {
                    // Unconstrained type parameter — falls back to TObject
                    val typeResult = PascalTypeIndex.findTypeWithTransitiveDeps("TObject", originFile, offset)
                    return typeResult.inScopeTypes.firstOrNull()
                }
            }
            // Stop at param list or return type — generic params appear before these
            if (child.elementType == nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER_LIST ||
                child.elementType == nl.akiar.pascal.psi.PascalElementTypes.RETURN_TYPE ||
                child.elementType == PascalTokenTypes.SEMI) break
        }
        return null
    }

    /**
     * Check if a name is a type parameter of the enclosing routine/type.
     * Returns the constraint type name (e.g., "TStruct") or "TObject" if unconstrained,
     * or null if the name is not a type parameter.
     */
    @JvmStatic
    fun findTypeParameterConstraintName(paramName: String, element: PsiElement): String? {
        var routine = PsiTreeUtil.getParentOfType(element, PascalRoutine::class.java)
        while (routine != null) {
            var result = findConstraintNameInGenericParams(paramName, routine.node)
            if (result != null) return result
            // Implementation routines may not have GENERIC_PARAMETER nodes — check the declaration
            if (routine.isImplementation) {
                val declaration = routine.declaration
                if (declaration != null) {
                    result = findConstraintNameInGenericParams(paramName, declaration.node)
                    if (result != null) return result
                }
            }
            val cls = routine.containingClass
            if (cls != null) {
                val clsResult = findConstraintNameInGenericParams(paramName, cls.node)
                if (clsResult != null) return clsResult
            }
            routine = PsiTreeUtil.getParentOfType(routine, PascalRoutine::class.java)
        }
        val typeDef = PsiTreeUtil.getParentOfType(element, PascalTypeDefinition::class.java)
        if (typeDef != null) {
            val tdResult = findConstraintNameInGenericParams(paramName, typeDef.node)
            if (tdResult != null) return tdResult
        }
        return null
    }

    private fun findConstraintNameInGenericParams(paramName: String, node: com.intellij.lang.ASTNode?): String? {
        if (node == null) return null
        for (child in generateSequence(node.firstChildNode) { it.treeNext }) {
            if (child.elementType == nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER) {
                var foundParam = false
                var constraintName: String? = null
                var pastColon = false
                var gpChild = child.firstChildNode
                while (gpChild != null) {
                    if (!pastColon && gpChild.elementType == PascalTokenTypes.IDENTIFIER &&
                        gpChild.text.equals(paramName, ignoreCase = true)) {
                        foundParam = true
                    }
                    if (foundParam && gpChild.elementType == PascalTokenTypes.COLON) {
                        pastColon = true
                    }
                    if (pastColon && gpChild.elementType == PascalTokenTypes.IDENTIFIER) {
                        constraintName = gpChild.text
                        break
                    }
                    if (pastColon && gpChild.elementType == nl.akiar.pascal.psi.PascalElementTypes.TYPE_REFERENCE) {
                        constraintName = gpChild.text.trim()
                        break
                    }
                    gpChild = gpChild.treeNext
                }
                if (foundParam) return constraintName ?: "TObject"
            }
            if (child.elementType == nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER_LIST ||
                child.elementType == nl.akiar.pascal.psi.PascalElementTypes.RETURN_TYPE ||
                child.elementType == PascalTokenTypes.SEMI) break
        }
        return null
    }

    /**
     * Find the containing class for an element by walking up through nested routines.
     */
    @JvmStatic
    fun findContainingClass(element: PsiElement): PascalTypeDefinition? {
        var routine = PsiTreeUtil.getParentOfType(element, PascalRoutine::class.java)
        while (routine != null) {
            val cls = routine.containingClass
            if (cls != null) return cls
            routine = PsiTreeUtil.getParentOfType(routine, PascalRoutine::class.java)
        }
        return PsiTreeUtil.getParentOfType(element, PascalTypeDefinition::class.java)
    }
}
