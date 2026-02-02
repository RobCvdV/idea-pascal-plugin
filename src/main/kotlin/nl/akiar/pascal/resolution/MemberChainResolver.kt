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

    // Short-lived memo for chains to avoid repeated resolves during daemon passes
    private data class ChainKey(val filePath: String?, val startOffset: Int, val chainText: String)
    private val chainMemo = java.util.concurrent.ConcurrentHashMap<ChainKey, PsiElement?>()
    @Volatile private var lastMemoClearTs: Long = System.currentTimeMillis()
    private const val MEMO_TTL_MS = 2000L

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
        maybeLog("[MemberTraversal] resolveChain start element='${startElement.text}' file='${originFile?.name}'", originFile)
        val chain = collectChain(startElement)
        maybeLog("[MemberTraversal] collected chain size=${chain.size} parts=${chain.map { it.text }}", originFile)
        val resolved = resolveChainElements(chain, originFile)
        maybeLog("[MemberTraversal] resolved chain parts=${resolved.map { it?.javaClass?.simpleName ?: "<unresolved>" }}", originFile)
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
            val resolved = resolveChainElements(chain, element.containingFile)
            val myIndex = chain.indexOf(element)
            val targetIndex = if (myIndex >= 0) myIndex else chain.lastIndex
            val target = resolved.getOrNull(targetIndex)
            if (target != null) {
                maybeLog("[MemberTraversal] resolved chain parts=${resolved.filterNotNull().map { it.javaClass.simpleName }}", element.containingFile)
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
    }

    /**
     * Collect all identifiers in a member access chain.
     * For "a.b.c", returns [a, b, c]
     */
    private fun collectChain(element: PsiElement): List<PsiElement> {
        // Collect identifiers following DOTs: qualifier.Member.Next
        val parts = mutableListOf<PsiElement>()
        // Walk backwards to find the first identifier of the chain
        var start: PsiElement = element
        var prev = PsiTreeUtil.prevLeaf(element)
        while (prev != null && prev.node.elementType == PascalTokenTypes.DOT) {
            val beforeDot = PsiTreeUtil.prevLeaf(prev)
            if (beforeDot == null) {
                break
            }
            start = beforeDot
            prev = PsiTreeUtil.prevLeaf(beforeDot)
        }
        // Walk forward collecting identifiers separated by DOT
        var current: PsiElement? = start
        while (current != null) {
            if (current.node.elementType.toString().contains("IDENTIFIER")) {
                parts.add(current)
            }
            val next = PsiTreeUtil.nextLeaf(current)
            if (next == null || next.node.elementType != PascalTokenTypes.DOT) break
            current = PsiTreeUtil.nextLeaf(next)
        }
        return parts
    }

    /**
     * Resolve a member chain starting from an identifier.
     *
     * @param startElement The first identifier in the chain (or any identifier in the chain)
     * @return ChainResolutionResult containing resolved elements for each part
     */
    private fun resolveChainElements(chain: List<PsiElement>, originFile: PsiFile): List<PsiElement?> {
        val results = MutableList<PsiElement?>(chain.size) { null }
        if (chain.isEmpty()) return results

        val first = chain.first()
        maybeLog("[MemberTraversal] resolving first '${first.text}' at offset=${first.textOffset}", originFile)

        val usesInfo = nl.akiar.pascal.uses.PascalUsesClauseInfo.parse(originFile)
        val availableUnitsAtOffset = usesInfo.getAvailableUnits(first.textOffset)
        maybeLog("[MemberTraversal] uses at offset=${first.textOffset} size=${availableUnitsAtOffset.size} sample=${availableUnitsAtOffset.take(10)}", originFile)

        val varResult = nl.akiar.pascal.stubs.PascalVariableIndex.findVariablesWithUsesValidation(first.text, originFile, first.textOffset)
        val resolvedFirst: PsiElement? = when {
            varResult.inScopeVariables.isNotEmpty() -> {
                maybeLog("[MemberTraversal] first resolve refs=${varResult.inScopeVariables.size} for '${first.text}'", originFile)
                varResult.inScopeVariables.first().also {
                    maybeLog("[MemberTraversal] first resolved as variable '${first.text}'", originFile)
                    maybeLog("[MemberTraversal] first resolved -> ${it.javaClass.simpleName}", originFile)
                }
            }
            else -> {
                val typeResult = nl.akiar.pascal.stubs.PascalTypeIndex.findTypesWithUsesValidation(first.text, originFile, first.textOffset)
                typeResult.inScopeTypes.firstOrNull()?.also { maybeLog("[MemberTraversal] first resolved as type '${first.text}' -> ${it.name}", originFile) }
            }
        }
        results[0] = resolvedFirst

        var currentType: PascalTypeDefinition? = getTypeOf(resolvedFirst, originFile)
        var currentOwnerName: String? = when (resolvedFirst) {
            is PascalVariableDefinition -> resolvedFirst.typeName
            is PascalProperty -> resolvedFirst.typeName
            else -> null
        }
        if (resolvedFirst != null) {
            maybeLog("[MemberTraversal] getTypeOf element='${resolvedFirst.javaClass.simpleName}' typeName='${(resolvedFirst as? PascalVariableDefinition)?.typeName ?: (resolvedFirst as? PascalProperty)?.typeName ?: "<null>"}'", originFile)
        }
        if (currentType != null) {
            maybeLog("[MemberTraversal] typeOf lookup '${currentType.name}' in-scope=[${currentType.name}] out-of-scope=[]", originFile)
            maybeLog("[MemberTraversal] first type -> ${currentType.name}", originFile)
            maybeLog("[MemberTraversal] confirm type index for '${currentType.name}': in-scope=[${currentType.name}] out-of-scope=[]", originFile)
            currentOwnerName = currentType.name
        }

        // Resolve subsequent chain parts using member lookup on currentType
        for (i in 1 until chain.size) {
            val name = chain[i].text
            if (name.equals("Add", ignoreCase = true)) {
                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                    LOG.info("[MemberTraversal][Diag] resolving member 'Add' step=${i} currentType=${currentType?.name} currentOwnerName=${currentOwnerName}")
                }
            }
            var member: PsiElement? = null
            if (currentType != null) {
                member = findMemberInType(currentType, name, originFile, includeAncestors = true)
                if (name.equals("Add", ignoreCase = true)) {
                    if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                        LOG.info("[MemberTraversal][Diag] findMemberInType(owner=${currentType.name}) -> ${member?.javaClass?.simpleName ?: "<none>"}")
                    }
                }
            } else if (!currentOwnerName.isNullOrBlank()) {
                val props = PascalPropertyIndex.findProperties(name, originFile.project)
                member = props.firstOrNull { p -> p.containingClass?.name.equals(currentOwnerName, ignoreCase = true) }
                if (member == null) {
                    val routines = PascalRoutineIndex.findRoutines(name, originFile.project)
                    member = routines.firstOrNull { r -> r.containingClassName != null && r.containingClassName.equals(currentOwnerName, ignoreCase = true) }
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
            currentType = getTypeOf(member, originFile)
            currentOwnerName = when (member) {
                is PascalVariableDefinition -> member.typeName
                is PascalProperty -> member.typeName
                else -> currentType?.name
            }
            if (name.equals("Add", ignoreCase = true)) {
                if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(originFile)) {
                    LOG.info("[MemberTraversal][Diag] post 'Add' getTypeOf -> ${currentType?.name ?: "<none>"} ownerName=${currentOwnerName}")
                }
            }
        }
        return results
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
     */
    private fun getTypeOf(element: PsiElement?, originFile: PsiFile): PascalTypeDefinition? {
        if (element == null) return null
        val typeName = when (element) {
            is PascalVariableDefinition -> element.typeName
            is PascalProperty -> element.typeName
            is PascalRoutine -> element.returnTypeName  // Now uses stub-based return type
            else -> null
        }
        if (typeName.isNullOrBlank()) return null

        val contextFile = originFile
        if (typeName.equals("TStrings", ignoreCase = true)) {
            if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(contextFile)) {
                LOG.info("[MemberTraversal][Diag] getTypeOf(TStrings) enter [ctx=${contextFile.name}] element=${element.javaClass.simpleName}")
            }
        }

        val disableBuiltins = true  //java.lang.Boolean.getBoolean("pascal.memberTraversal.builtin.disable")

        return MemberResolutionCache.getOrComputeTypeOf(element, originFile, contextFile) {
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
    }

    private fun findMemberInType(typeDef: PascalTypeDefinition, name: String, callSiteFile: PsiFile, includeAncestors: Boolean): PsiElement? {
        // Collect all owner type names using cached inheritance chain
        val owners = mutableSetOf<String>()
        typeDef.name?.let { owners.add(it) }
        if (includeAncestors) {
            // Use cached inheritance chain for ancestor lookup
            val ancestors = InheritanceChainCache.getAllAncestorTypes(typeDef)
            ancestors.forEach { it.name?.let { n -> owners.add(n) } }
        }

        // Search for fields (variables) in the index
        val fields = PascalVariableIndex.findVariables(name, callSiteFile.project)
        val field = fields.firstOrNull { v ->
            v.variableKind == nl.akiar.pascal.psi.VariableKind.FIELD &&
            v.containingClass?.name?.let { owners.contains(it) } == true
        }
        if (field != null) return field

        // Search for properties in the index
        val props = PascalPropertyIndex.findProperties(name, callSiteFile.project)
        val prop = props.firstOrNull { p ->
            val owner = p.containingClass?.name
            owner != null && owners.contains(owner)
        }
        if (prop != null) return prop

        // Search for routines in the index
        val routines = PascalRoutineIndex.findRoutines(name, callSiteFile.project)
        val routine = routines.firstOrNull { r ->
            val ownerName = r.containingClassName
            ownerName != null && owners.contains(ownerName)
        }
        if (routine != null) return routine

        // Skip file scanning if indices are not ready
        if (DumbService.isDumb(callSiteFile.project)) {
            maybeLog("[MemberTraversal] dumb mode: skip PSI scan for '$name' in type='${typeDef.name}'", callSiteFile)
            return null
        }

        // Index miss: try to scan the owning unit file for a matching member
        val project = callSiteFile.project
        val ownerUnit = typeDef.unitName
        if (!ownerUnit.isNullOrBlank()) {
            val vf = nl.akiar.pascal.project.PascalProjectService.getInstance(project).resolveUnit(ownerUnit, true)
            if (vf != null) {
                val psi = com.intellij.psi.PsiManager.getInstance(project).findFile(vf)
                if (psi != null) {
                    // Try fields first
                    val fileFields = PsiTreeUtil.findChildrenOfType(psi, PascalVariableDefinition::class.java)
                    val fHit = fileFields.firstOrNull { f ->
                        f.name.equals(name, true) &&
                        f.variableKind == nl.akiar.pascal.psi.VariableKind.FIELD &&
                        f.containingClass?.name?.let { owners.contains(it) } == true
                    }
                    if (fHit != null) {
                        maybeLog("[MemberTraversal] member '$name' (scan) -> PascalVariableDefinitionImpl in unit=${ownerUnit}", callSiteFile)
                        return fHit
                    }
                    // Try properties
                    val fileProps = PsiTreeUtil.findChildrenOfType(psi, PascalProperty::class.java)
                    val pHit = fileProps.firstOrNull { p -> p.name.equals(name, true) && p.containingClass?.name?.let { owners.contains(it) } == true }
                    if (pHit != null) {
                        maybeLog("[MemberTraversal] member '$name' (scan) -> PascalPropertyImpl in unit=${ownerUnit}", callSiteFile)
                        return pHit
                    }
                    // Then routines
                    val fileRoutines = PsiTreeUtil.findChildrenOfType(psi, PascalRoutine::class.java)
                    val rHit = fileRoutines.firstOrNull { r -> r.name.equals(name, true) && (r.containingClassName?.let { owners.contains(it) } == true || r.containingClass?.name?.let { owners.contains(it) } == true) }
                    if (rHit != null) {
                        maybeLog("[MemberTraversal] member '$name' (scan) -> PascalRoutineImpl in unit=${ownerUnit}", callSiteFile)
                        return rHit
                    }
                }
            }
        }

        // Last resort: scan the type definition directly if it's accessible
        // This handles cases where index isn't populated (e.g., test fixtures)
        val typeFile = typeDef.containingFile
        if (typeFile != null) {
            for (ownerType in owners.mapNotNull { ownerName ->
                PsiTreeUtil.findChildrenOfType(typeFile, PascalTypeDefinition::class.java)
                    .firstOrNull { it.name.equals(ownerName, true) }
            }) {
                // Check fields
                for (f in ownerType.fields) {
                    if (f.name.equals(name, true)) {
                        maybeLog("[MemberTraversal] member '$name' (direct scan) -> field in type=${ownerType.name}", callSiteFile)
                        return f
                    }
                }
                // Check properties
                for (p in ownerType.properties) {
                    if (p.name.equals(name, true)) {
                        maybeLog("[MemberTraversal] member '$name' (direct scan) -> property in type=${ownerType.name}", callSiteFile)
                        return p
                    }
                }
                // Check methods
                for (m in ownerType.methods) {
                    if (m.name.equals(name, true)) {
                        maybeLog("[MemberTraversal] member '$name' (direct scan) -> method in type=${ownerType.name}", callSiteFile)
                        return m
                    }
                }
            }
        }

        maybeLog("[MemberTraversal] findMemberInType: no match for '$name' in type='${typeDef.name}' (owners=${owners})", callSiteFile)
        return null
    }
}
