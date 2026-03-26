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
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
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
            if (ResolverConfig.enablePerformanceMetrics) timings.add(label to nanos)
        }
        @Synchronized fun snapshot(): List<Pair<String, Long>> = timings.toList()
        @Synchronized fun clear() { timings.clear() }
    }

    private fun maybeLog(msg: String, file: PsiFile? = null) {
        if (LOG_ENABLED && (file == null || nl.akiar.pascal.log.UnitLogFilter.shouldLog(file))) LOG.info(msg)
    }

    private fun clearExpiredMemo() {
        val now = System.currentTimeMillis()
        if (now - lastMemoClearTs > MEMO_TTL_MS) {
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
        } catch (_: Throwable) {
            // best effort
        }
        try {
            InheritanceChainCache.clearAll(project)
        } catch (_: Throwable) {
            // best effort
        }
        if (project != null) {
            try {
                DaemonCodeAnalyzer.getInstance(project).restart()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    // Reentrancy guard to avoid infinite resolution loops across handlers
    private val RESOLVE_IN_PROGRESS: Key<Boolean> = Key.create("pascal.member.resolve.in.progress")

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

    /**
     * Collect all identifiers in a member access chain.
     * For "a.b.c", returns [a, b, c]
     */
    private fun isChainIdentifier(element: PsiElement): Boolean {
        val elemType = element.node.elementType
        if (elemType == PascalTokenTypes.KW_SELF) return true
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
            if (diagLog) LOG.info("[GenericChain] backward walk: prev='${prev.text}' type=${prev.node.elementType}")
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
                if (diagLog) LOG.info("[GenericChain] backward walk: attempting GT skip from '${prev.text}' at offset=${prev.textOffset}")
                val matched = skipMatchedBackward(prev, PascalTokenTypes.LT, PascalTokenTypes.GT)
                if (matched != null) {
                    if (diagLog) LOG.info("[GenericChain] backward walk: GT matched LT at '${matched.text}' offset=${matched.textOffset}")
                    prev = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(matched))
                    continue
                } else {
                    if (diagLog) LOG.info("[GenericChain] backward walk: GT has no matching LT, treating as chain boundary")
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
                    if (diagLog) LOG.info("[GenericChain] after-dot backward: attempting GT skip at offset=${beforeDot.textOffset}")
                    val matched = skipMatchedBackward(beforeDot, PascalTokenTypes.LT, PascalTokenTypes.GT)
                    if (matched != null) {
                        if (diagLog) LOG.info("[GenericChain] after-dot backward: GT matched LT at offset=${matched.textOffset}")
                        beforeDot = skipBackwardWhitespace(PsiTreeUtil.prevLeaf(matched))
                    } else {
                        if (diagLog) LOG.info("[GenericChain] after-dot backward: GT has no matching LT, stopping")
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
            if (diagLog && next != null) LOG.info("[GenericChain] forward walk: after '${current.text}', next='${next.text}' type=${next.node.elementType}")
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
                if (diagLog) LOG.info("[GenericChain] forward walk: attempting LT skip after '${current.text}'")
                val matched = skipMatchedForward(next, PascalTokenTypes.LT, PascalTokenTypes.GT)
                if (matched != null) {
                    if (diagLog) LOG.info("[GenericChain] forward walk: LT matched GT at offset=${matched.textOffset}")
                    next = skipForwardWhitespace(PsiTreeUtil.nextLeaf(matched))
                } else {
                    if (diagLog) LOG.info("[GenericChain] forward walk: LT has no matching GT, treating as chain boundary")
                }
            }
            if (next == null || next.node.elementType != PascalTokenTypes.DOT) break
            current = skipForwardWhitespace(PsiTreeUtil.nextLeaf(next))
        }
        if (diagLog) LOG.info("[GenericChain] collectChain result: [${parts.joinToString(", ") { "'${it.text}'" }}] from element='${element.text}'")
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
                LOG.info("[GenericChain] CACHE HIT for key='$cacheKey' resolved=[${cached.resolvedElements.joinToString(", ") { it?.javaClass?.simpleName ?: "<null>" }}]")
                return cached
            }

            val first = chain.first()
            maybeLog("[MemberTraversal] resolving first '${first.text}' at offset=${first.textOffset}", originFile)

            val usesInfo = nl.akiar.pascal.uses.PascalUsesClauseInfo.parse(originFile)
            val availableUnitsAtOffset = usesInfo.getAvailableUnits(first.textOffset)
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
            // Try unit-qualified prefix (e.g. Spring.Collections.Lists.TList)
            else if (chain.size >= 2) {
                val unitPrefixResult = tryResolveUnitPrefix(chain, availableUnitsAtOffset, originFile.project)
                if (unitPrefixResult != null) {
                    val (prefixLen, unitPsiFile) = unitPrefixResult
                    for (idx in 0 until prefixLen) {
                        results[idx] = unitPsiFile
                    }
                    val memberName = chain[prefixLen].text
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

            // Normal first-element resolution (if not handled by Self or unit prefix)
            if (startIndex == 1 && results[0] == null) {
                // Use scope-aware variable lookup (local > field > global) for proper anonymous routine support
                val resolvedVar = nl.akiar.pascal.stubs.PascalVariableIndex.findVariableAtPosition(first.text, originFile, first.textOffset)
                val resolvedFirst: PsiElement? = if (resolvedVar != null) {
                    maybeLog("[MemberTraversal] first resolved as variable '${first.text}' (scope-aware)", originFile)
                    resolvedVar
                } else {
                    val typeResult = nl.akiar.pascal.stubs.PascalTypeIndex.findTypesWithUsesValidation(first.text, originFile, first.textOffset)
                    typeResult.inScopeTypes.firstOrNull()?.also { maybeLog("[MemberTraversal] first resolved as type '${first.text}' -> ${it.name}", originFile) }
                        ?: run {
                            // Try routine index (function as first element in chain, e.g. CoStatus.HandleStatus)
                            val routineResult = PascalRoutineIndex.findRoutinesWithUsesValidation(first.text, originFile, first.textOffset)
                            routineResult.inScopeRoutines.firstOrNull()?.also {
                                maybeLog("[MemberTraversal] first resolved as routine '${first.text}' -> returnType=${it.returnTypeName}", originFile)
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
                        is PascalProperty -> resolvedFirst.typeName
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
                LOG.info("[GenericChain] first element resolved: '${first.text}' -> ${resolvedFirst?.javaClass?.simpleName ?: "<null>"} type=${currentType?.name ?: "<null>"} ownerName=$currentOwnerName typeArgMap=$currentTypeArgMap")
            }

            // Resolve subsequent chain parts using member lookup on currentType
            for (i in startIndex until chain.size) {
                // Record the typeArgMap that is active for this element (the owner's context)
                perElementMaps[i] = currentTypeArgMap
                val name = chain[i].text
                LOG.info("[GenericChain] resolving step[$i] member='$name' currentType=${currentType?.name ?: "<null>"} currentOwnerName=$currentOwnerName typeArgMap=$currentTypeArgMap")
                var member: PsiElement? = null
                if (currentType != null) {
                    member = findMemberInType(currentType, name, originFile, includeAncestors = true)
                    LOG.info("[GenericChain] findMemberInType(owner=${currentType.name}, member=$name) -> ${member?.javaClass?.simpleName ?: "<none>"}")
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
                    else -> null
                }
                LOG.info("[GenericChain] step[$i] '$name' rawTypeName='$memberRawTypeName' currentTypeArgMap=$currentTypeArgMap")

                // Check for call-site generic type arguments on this chain element
                // e.g., Resolve<IMutationsRepository> → extract ["IMutationsRepository"]
                // and build a method-level typeArgMap if the member is a generic routine
                var effectiveTypeArgMap = currentTypeArgMap
                if (member is PascalRoutine) {
                    val callSiteArgs = extractCallSiteTypeArgs(chain[i])
                    if (callSiteArgs.isNotEmpty()) {
                        val routineTypeParams = getRoutineTypeParameters(member)
                        LOG.info("[GenericChain] step[$i] '$name' generic method: callSiteArgs=$callSiteArgs routineTypeParams=$routineTypeParams")
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
                            LOG.info("[GenericChain] step[$i] '$name' merged typeArgMap=$effectiveTypeArgMap")
                            // Update the per-element map so doc provider sees the call-site substitution
                            perElementMaps[i] = effectiveTypeArgMap
                        }
                    }
                }

                val substitutedTypeName = if (memberRawTypeName != null && effectiveTypeArgMap.containsKey(memberRawTypeName)) {
                    val sub = effectiveTypeArgMap[memberRawTypeName]!!
                    LOG.info("[GenericChain] step[$i] '$name' SUBSTITUTION: '$memberRawTypeName' -> '$sub'")
                    sub
                } else {
                    memberRawTypeName
                }

                currentType = if (substitutedTypeName != null && substitutedTypeName != memberRawTypeName) {
                    // Use the substituted type name for lookup
                    getTypeOf(member, originFile, substitutedTypeName)
                } else {
                    getTypeOf(member, originFile)
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

                LOG.info("[GenericChain] step[$i] '$name' RESULT: member=${member?.javaClass?.simpleName ?: "<null>"} nextType=${currentType?.name ?: "<null>"} nextOwner=$currentOwnerName effectiveType=$effectiveTypeName nextTypeArgMap=$currentTypeArgMap")
            }
            val internalResult = ChainResolutionInternal(results, currentTypeArgMap, perElementMaps)
            // Always cache — the key includes modCount so stale results expire when
            // the file changes. Within the same modCount, results should be deterministic.
            cache[cacheKey] = internalResult
            return internalResult
        } finally {
            PerformanceMetrics.record("MemberChainResolver.resolveChainElements", System.nanoTime() - start)
        }
    }

    private fun resolveMemberChain(element: PsiElement): PsiElement? {
        val start = System.nanoTime()
        try {
            // Skip resolution for already resolved elements
            val cached = chainMemo.values.find { it?.textRange == element.textRange }
            if (cached != null) {
                maybeLog("[MemberTraversal] resolveMemberChain: using cached result for '${element.text}'", element.containingFile)
                return cached
            }

            // Proceed with normal resolution
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
            PerformanceMetrics.record("MemberChainResolver.resolveMemberChain", System.nanoTime() - start)
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

        return if (typeNameOverride != null) {
            // Bypass cache: type name override changes the meaning of the lookup
            computeType()
        } else {
            MemberResolutionCache.getOrComputeTypeOf(element, originFile, contextFile, computeType)
        }
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
     * Infer the type of an inline variable from its initializer expression.
     * For `var X := SomeFunc()`, resolves SomeFunc and returns its return type name.
     * For `var X := SomeVar`, resolves SomeVar and returns its type name.
     */
    private fun inferTypeFromInitializer(varDef: PascalVariableDefinition, originFile: PsiFile): String? {
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
                // The identifier may be a direct child (simple assignment) or nested
                // inside an expression node (e.g. PRIMARY_EXPRESSION for function calls)
                val identifierNode = if (elementType == PascalTokenTypes.IDENTIFIER) {
                    child
                } else {
                    // Drill into expression nodes to find the first identifier
                    nl.akiar.pascal.psi.PsiUtil.findFirstRecursiveAnyOf(
                        child,
                        PascalTokenTypes.IDENTIFIER
                    )
                }
                if (identifierNode != null) {
                    val rhsName = identifierNode.text
                    maybeLog("[MemberTraversal] inferType: trying RHS identifier '$rhsName'", originFile)

                    // Try as routine first (function call)
                    val routineResult = PascalRoutineIndex.findRoutinesWithUsesValidation(rhsName, originFile, identifierNode.startOffset)
                    val routine = routineResult.inScopeRoutines.firstOrNull()
                    if (routine != null && !routine.returnTypeName.isNullOrBlank()) {
                        maybeLog("[MemberTraversal] inferType: '$rhsName' is routine, returnType='${routine.returnTypeName}'", originFile)
                        return routine.returnTypeName
                    }

                    // Try as variable
                    val varResult = PascalVariableIndex.findVariablesWithUsesValidation(rhsName, originFile, identifierNode.startOffset)
                    val variable = varResult.inScopeVariables.firstOrNull()
                    if (variable != null && !variable.typeName.isNullOrBlank()) {
                        maybeLog("[MemberTraversal] inferType: '$rhsName' is variable, typeName='${variable.typeName}'", originFile)
                        return variable.typeName
                    }

                    // Try as property (uses-validated)
                    val propResult = PascalPropertyIndex.findPropertiesWithUsesValidation(rhsName, originFile, identifierNode.startOffset)
                    val prop = propResult.inScopeProperties.firstOrNull()
                    if (prop != null && !prop.typeName.isNullOrBlank()) {
                        maybeLog("[MemberTraversal] inferType: '$rhsName' is property, typeName='${prop.typeName}'", originFile)
                        return prop.typeName
                    }
                }
                break
            }
            child = child.treeNext
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

        LOG.info("[GenericChain] findMemberInType: looking for '$name' in type='${typeDef.name}' unit='${typeDef.unitName}' owners=[${owners.joinToString(", ") { "${it.name}(${it.unitName})" }}]")

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
            val field = fields.firstOrNull { isVisible(it, callSiteFile) }
            if (field != null) return field

            // Properties
            val propKey = nl.akiar.pascal.stubs.PascalScopedMemberIndex.compositeKey(ownerUnit, ownerName, name, "property")
            val props = com.intellij.psi.stubs.StubIndex.getElements(
                nl.akiar.pascal.stubs.PascalScopedMemberIndex.PROPERTY_KEY,
                propKey, project, com.intellij.psi.search.GlobalSearchScope.allScope(project),
                PascalProperty::class.java
            )
            val prop = props.firstOrNull { isVisible(it, callSiteFile) }
            if (prop != null) return prop

            // Routines
            val routineKey = "${ownerUnit}#${ownerName}#${name}".lowercase()
            val routines = nl.akiar.pascal.stubs.PascalScopedRoutineIndex.find(routineKey, project)
            val routine = routines.firstOrNull { isVisible(it, callSiteFile) }
            if (routine != null) return routine
        }

        // 2. Fallback: use PSI-based getMembers(true) which handles inheritance and works cross-unit
        if (!DumbService.isDumb(project)) {
            val allMembers = typeDef.getMembers(true)
            // Log with types for debugging property vs field vs routine
            val memberInfo = allMembers.joinToString(", ") { m ->
                val mName = (m as? com.intellij.psi.PsiNameIdentifierOwner)?.name ?: "?"
                val mType = when (m) {
                    is PascalProperty -> "prop"
                    is PascalRoutine -> "routine"
                    is PascalVariableDefinition -> "field"
                    else -> m.javaClass.simpleName
                }
                "'$mName'($mType)"
            }
            LOG.info("[GenericChain] findMemberInType PSI fallback for '$name' in '${typeDef.name}': ${allMembers.size} members: [$memberInfo]")
            for (m in allMembers) {
                if (m is com.intellij.psi.PsiNameIdentifierOwner && m.name.equals(name, ignoreCase = true)) {
                    val vis = when (m) {
                        is PascalRoutine -> m.visibility
                        is PascalProperty -> m.visibility
                        is PascalVariableDefinition -> m.visibility
                        else -> null
                    }
                    if (isVisible(m, callSiteFile)) {
                        return m
                    } else {
                        LOG.info("[GenericChain] findMemberInType: found '$name' but NOT visible (visibility='$vis')")
                    }
                }
            }
        }

        // 3. Fallback: try validated property/routine index with containingClass filter
        // This catches properties that are missed by PSI tree walking (e.g., due to parser quirks)
        if (!DumbService.isDumb(project)) {
            val propResult = PascalPropertyIndex.findPropertiesWithUsesValidation(name, callSiteFile, callSiteFile.textLength.coerceAtMost(1))
            val validatedProps = propResult.inScopeProperties
            LOG.info("[GenericChain] findMemberInType step 3: validated property index for '$name' returned ${validatedProps.size} results: [${validatedProps.joinToString(", ") { "'${it.name}' in ${it.containingClassName}(${it.unitName})" }}]")
            for (ownerType in owners) {
                val ownerName = ownerType.name ?: continue
                val prop = validatedProps.firstOrNull { p ->
                    p.containingClassName.equals(ownerName, ignoreCase = true) && isVisible(p, callSiteFile)
                }
                if (prop != null) {
                    LOG.info("[GenericChain] findMemberInType: found '$name' via validated property index in owner='$ownerName'")
                    return prop
                }
                val routineResult = PascalRoutineIndex.findRoutinesWithUsesValidation(name, callSiteFile, callSiteFile.textLength.coerceAtMost(1))
                val validatedRoutines = routineResult.inScopeRoutines
                val routine = validatedRoutines.firstOrNull { r ->
                    (r.containingClassName?.equals(ownerName, ignoreCase = true) == true ||
                     r.containingClass?.name?.equals(ownerName, ignoreCase = true) == true) &&
                    isVisible(r, callSiteFile)
                }
                if (routine != null) {
                    LOG.info("[GenericChain] findMemberInType: found '$name' via validated routine index in owner='$ownerName'")
                    return routine
                }
            }
        }

        LOG.info("[GenericChain] findMemberInType: NO MATCH for '$name' in type='${typeDef.name}' unit='${typeDef.unitName}' ancestors=[${if (owners.size > 1) owners.drop(1).joinToString(", ") { "${it.name}(${it.unitName})" } else "none"}]")
        return null
    }

    private fun isVisible(member: PsiElement, callSiteFile: PsiFile): Boolean {
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

        // Protected is also hidden across units unless we are in a subclass
        // (Simplified for now: hide unless we have proof of subclassing)
        if (visibility.contains("protected", ignoreCase = true)) return false

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
