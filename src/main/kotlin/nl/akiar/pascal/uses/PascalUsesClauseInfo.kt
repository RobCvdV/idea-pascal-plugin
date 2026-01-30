package nl.akiar.pascal.uses

import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.parser.PascalSonarParser
import nl.akiar.pascal.psi.PascalElementTypes
import nl.akiar.pascal.psi.PsiUtil

/**
 * Holds parsed uses clause information for a Pascal file.
 *
 * Key differences from the old Java implementation:
 * - Preserves the ORDER of units in uses clauses (critical for Delphi's "last wins" rule)
 * - Uses Lists instead of Sets
 *
 * According to Delphi documentation:
 * "If two or more units declare the same identifier in their interface sections,
 * an unqualified reference to the identifier selects the declaration in the innermost scope,
 * that is, in the unit where the reference itself occurs, or, if that unit does not declare
 * the identifier, in the LAST unit in the uses clause that does declare the identifier."
 */
data class PascalUsesClauseInfo(
    /** Units in the interface uses clause, in ORDER (first to last) */
    val interfaceUses: List<String>,
    /** Units in the implementation uses clause, in ORDER (first to last) */
    val implementationUses: List<String>,
    /** Offset where interface section starts (-1 if not found) */
    val interfaceSectionStart: Int,
    /** Offset where implementation section starts (-1 if not found) */
    val implementationSectionStart: Int
) {
    /** All units from both uses clauses, interface first then implementation */
    val allUses: List<String> by lazy {
        interfaceUses + implementationUses
    }

    /** Set of all units (lowercased) for quick contains checks */
    private val interfaceUsesSet: Set<String> by lazy {
        interfaceUses.map { it.lowercase() }.toSet()
    }

    private val implementationUsesSet: Set<String> by lazy {
        implementationUses.map { it.lowercase() }.toSet()
    }

    private val allUsesSet: Set<String> by lazy {
        interfaceUsesSet + implementationUsesSet
    }

    /**
     * Get units available at a given offset, preserving order.
     * In implementation section: both interface and implementation uses are available.
     * In interface section: only interface uses are available.
     */
    fun getAvailableUnits(offset: Int): List<String> {
        return if (implementationSectionStart >= 0 && offset >= implementationSectionStart) {
            interfaceUses + implementationUses
        } else {
            interfaceUses
        }
    }

    /**
     * Get the index of a unit in the available uses list (for "last wins" comparison).
     * Returns -1 if not found.
     * Higher index = higher priority (last wins).
     */
    fun getUnitPriority(unitName: String, offset: Int, scopes: List<String>? = null): Int {
        val availableUnits = getAvailableUnits(offset)
        val lowerUnit = unitName.lowercase()

        // Direct match
        availableUnits.forEachIndexed { index, unit ->
            if (unit.lowercase() == lowerUnit) {
                return index
            }
        }

        // Try scope-based matching
        if (scopes != null) {
            for (scope in scopes) {
                val lowerScope = scope.lowercase()

                // Scope itself is always available (e.g., "System" is implicitly available)
                if (lowerUnit == lowerScope) {
                    return -1 // Special case: scope units are available but with lowest priority
                }

                // Scoped match: "System.SysUtils" in uses allows "SysUtils"
                val scopedName = "$lowerScope.$lowerUnit"
                availableUnits.forEachIndexed { index, unit ->
                    if (unit.lowercase() == scopedName) {
                        return index
                    }
                }

                // Reverse scoped match: "SysUtils" in uses allows "System.SysUtils" unit
                if (lowerUnit.startsWith("$lowerScope.")) {
                    val shortName = lowerUnit.substring(lowerScope.length + 1)
                    availableUnits.forEachIndexed { index, unit ->
                        if (unit.lowercase() == shortName) {
                            return index
                        }
                    }
                }
            }
        }

        return -2 // Not found at all
    }

    /**
     * Check if a unit is available at a given offset.
     * @param unitName The unit name to check
     * @param offset The offset in the file
     * @param scopes Optional list of scope names (e.g., ["System", "Vcl"])
     * @return The name found in uses clause if available, null otherwise
     */
    fun findUnitInUses(unitName: String, offset: Int, scopes: List<String>? = null): String? {
        val lowerUnit = unitName.lowercase()

        // Implicit inclusion: if unitName itself is a scope name, it's always available
        if (scopes != null) {
            for (scope in scopes) {
                if (lowerUnit.equals(scope, ignoreCase = true)) {
                    return unitName
                }
            }
        }

        val availableUnits = getAvailableUnits(offset)
        val availableUnitsLower = availableUnits.map { it.lowercase() }

        // Direct match
        if (lowerUnit in availableUnitsLower) {
            return availableUnits[availableUnitsLower.indexOf(lowerUnit)]
        }

        if (scopes != null) {
            for (scope in scopes) {
                val lowerScope = scope.lowercase()

                // Scoped match: "System.SysUtils" in uses allows "SysUtils"
                val scopedName = "$lowerScope.$lowerUnit"
                if (scopedName in availableUnitsLower) {
                    return availableUnits[availableUnitsLower.indexOf(scopedName)]
                }

                // Reverse scoped match: "SysUtils" in uses allows "System.SysUtils" unit
                if (lowerUnit.startsWith("$lowerScope.")) {
                    val shortName = lowerUnit.substring(lowerScope.length + 1)
                    if (shortName in availableUnitsLower) {
                        return availableUnits[availableUnitsLower.indexOf(shortName)]
                    }
                }
            }
        }

        return null
    }

    /**
     * Check if a unit is available at a given offset.
     */
    fun isUnitAvailable(unitName: String, offset: Int, scopes: List<String>? = null): Boolean {
        return findUnitInUses(unitName, offset, scopes) != null
    }

    /**
     * Check if an offset is in the interface section.
     */
    fun isInInterfaceSection(offset: Int): Boolean {
        if (interfaceSectionStart < 0) return false
        if (implementationSectionStart < 0) return offset >= interfaceSectionStart
        return offset in interfaceSectionStart until implementationSectionStart
    }

    /**
     * Check if an offset is in the implementation section.
     */
    fun isInImplementationSection(offset: Int): Boolean {
        return implementationSectionStart >= 0 && offset >= implementationSectionStart
    }

    companion object {
        private val CACHE_KEY = Key.create<com.intellij.psi.util.CachedValue<PascalUsesClauseInfo>>("PASCAL_USES_CLAUSE_CACHE_V2")
        private val LOG = Logger.getInstance(PascalUsesClauseInfo::class.java)

        /**
         * Parse uses clauses from a Pascal file (cached).
         */
        @JvmStatic
        fun parse(file: PsiFile): PascalUsesClauseInfo {
            return CachedValuesManager.getManager(file.project).getCachedValue(file, CACHE_KEY, {
                // Fast path: parse uses from file text without loading AST to keep stub-safe and performant
                val fast = parseFromText(file)
                val result = fast ?: parseImpl(file)
                CachedValueProvider.Result.create(result, file)
            }, false)
        }

        /**
         * Stub-safe text-based parser for uses clauses and section starts. Avoids AST loads.
         */
        private fun parseFromText(file: PsiFile): PascalUsesClauseInfo? {
            val text = file.viewProvider.document?.text ?: return null
            val lower = text.lowercase()
            var interfaceStart = -1
            var implementationStart = -1
            val interfaceUses = mutableListOf<String>()
            val implementationUses = mutableListOf<String>()

            // Locate section starts (approximate)
            interfaceStart = lower.indexOf("interface").takeIf { it >= 0 } ?: -1
            implementationStart = lower.indexOf("implementation").takeIf { it >= 0 } ?: -1

            // Find uses in interface
            if (interfaceStart >= 0) {
                val ifaceChunk = lower.substring(interfaceStart, if (implementationStart > interfaceStart) implementationStart else lower.length)
                interfaceUses.addAll(extractUsesNames(ifaceChunk))
            }
            // Find uses in implementation
            if (implementationStart >= 0) {
                val implChunk = lower.substring(implementationStart)
                implementationUses.addAll(extractUsesNames(implChunk))
            }

            if (interfaceStart < 0 && implementationStart < 0 && (interfaceUses.isNotEmpty() || implementationUses.isNotEmpty())) {
                interfaceStart = 0
            }

            return PascalUsesClauseInfo(
                interfaceUses = interfaceUses.map { nl.akiar.pascal.psi.PsiUtil.normalizeUnitName(it) },
                implementationUses = implementationUses.map { nl.akiar.pascal.psi.PsiUtil.normalizeUnitName(it) },
                interfaceSectionStart = interfaceStart,
                implementationSectionStart = implementationStart
            )
        }

        private fun extractUsesNames(chunk: String): List<String> {
            val result = mutableListOf<String>()
            var idx = 0
            while (true) {
                val usesIdx = chunk.indexOf("uses", idx)
                if (usesIdx < 0) break
                // From uses to next ';'
                val semiIdx = chunk.indexOf(';', usesIdx)
                val end = if (semiIdx > usesIdx) semiIdx else chunk.length
                val usesBody = chunk.substring(usesIdx + 4, end)
                // Split by commas, strip "in 'path'" parts
                val parts = usesBody.split(',')
                for (p in parts) {
                    var unit = p.trim()
                    val inIdx = unit.indexOf(" in ")
                    if (inIdx >= 0) unit = unit.substring(0, inIdx).trim()
                    // remove quotes and whitespace
                    unit = unit.replace("\"", "").replace("'", "").trim()
                    if (unit.isNotEmpty()) result.add(unit)
                }
                idx = end + 1
            }
            return result
        }

        private fun parseImpl(file: PsiFile): PascalUsesClauseInfo {
            val interfaceUses = mutableListOf<String>()
            val implementationUses = mutableListOf<String>()
            var interfaceSectionStart = -1
            var implementationSectionStart = -1

            val fileNode = file.node ?: return PascalUsesClauseInfo(
                interfaceUses, implementationUses, interfaceSectionStart, implementationSectionStart
            )

            val state = ParseState()
            findSectionsAndUses(fileNode, state, interfaceUses, implementationUses)
            if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(file)) {
                LOG.info("[UsesClauses] Parsing uses clauses for file: ${file.name} all uses so far: ${interfaceUses + implementationUses}")
            }

            // For program files (no interface/implementation), treat all uses as "interface" uses
            if (state.interfaceSectionStart < 0 && state.implementationSectionStart < 0 && interfaceUses.isNotEmpty()) {
                state.interfaceSectionStart = 0
            }

            // Handle .dpr/.lpr project files
            if (state.interfaceSectionStart < 0 && (file.name.endsWith(".dpr") || file.name.endsWith(".lpr"))) {
                state.interfaceSectionStart = 0
            }

            return PascalUsesClauseInfo(
                interfaceUses.toList(),
                implementationUses.toList(),
                state.interfaceSectionStart,
                state.implementationSectionStart
            )
        }

        private class ParseState(
            var inInterfaceSection: Boolean = false,
            var inImplementationSection: Boolean = false,
            var interfaceSectionStart: Int = -1,
            var implementationSectionStart: Int = -1
        )

        private fun findSectionsAndUses(
            node: ASTNode,
            state: ParseState,
            interfaceUses: MutableList<String>,
            implementationUses: MutableList<String>
        ) {
            var child: ASTNode? = node.firstChildNode
//            if (interfaceUses.size > 3) {
//                LOG.info("[UsesClauses] Scanning node: ${node.elementType} at offset ${node.text}")
//                LOG.info("[UsesClauses] uses so far: ${interfaceUses}, implementation=${implementationUses}")
//            }
            while (child != null) {
                val type: IElementType = child.elementType

                when (type) {
                    PascalTokenTypes.KW_INTERFACE -> {
                        state.inInterfaceSection = true
                        state.inImplementationSection = false
                        if (state.interfaceSectionStart < 0) {
                            state.interfaceSectionStart = child.startOffset
                        }
                    }
                    PascalTokenTypes.KW_IMPLEMENTATION -> {
                        state.inInterfaceSection = false
                        state.inImplementationSection = true
                        if (state.implementationSectionStart < 0) {
                            state.implementationSectionStart = child.startOffset
                        }
                    }
                    PascalTokenTypes.KW_USES -> {
                        val targetList = if (state.inImplementationSection) implementationUses else interfaceUses
                        parseUsesClauseContent(child, targetList)
                    }
                }

                // Recurse into children (but not into KW_USES as it's a leaf)
                if (type != PascalTokenTypes.KW_USES) {
                    findSectionsAndUses(child, state, interfaceUses, implementationUses)
                }

                child = child.treeNext
            }
        }

        private fun parseUsesClauseContent(usesNode: ASTNode, targetList: MutableList<String>) {
            // Handle structured children of uses node
            var structChild: ASTNode? = usesNode.firstChildNode
            while (structChild != null) {
                when (structChild.elementType) {
                    PascalElementTypes.UNIT_REFERENCE -> {
                        targetList.add(PsiUtil.normalizeUnitName(structChild.text))
                    }
                    PascalTokenTypes.IDENTIFIER -> {
                        val unitName = buildDottedName(structChild)
                        targetList.add(PsiUtil.normalizeUnitName(unitName))
                    }
                }
                structChild = structChild.treeNext
            }

            // Handle siblings after 'uses' keyword
            var current: ASTNode? = skipIgnorableTokens(usesNode.treeNext)

            while (current != null) {
                val type = current.elementType

                when {
                    type == PascalTokenTypes.SEMI -> break

                    type == PascalElementTypes.UNIT_REFERENCE -> {
                        targetList.add(PsiUtil.normalizeUnitName(current.text))
                        current = skipIgnorableTokens(current.treeNext)
                    }

                    type == PascalTokenTypes.IDENTIFIER -> {
                        val unitName = StringBuilder(current.text)
                        current = skipIgnorableTokens(current.treeNext)

                        // Handle dotted names
                        while (current != null && current.elementType == PascalTokenTypes.DOT) {
                            unitName.append(".")
                            current = skipIgnorableTokens(current.treeNext)
                            if (current != null && current.elementType == PascalTokenTypes.IDENTIFIER) {
                                unitName.append(current.text)
                                current = skipIgnorableTokens(current.treeNext)
                            } else {
                                break
                            }
                        }
                        targetList.add(PsiUtil.normalizeUnitName(unitName.toString()))
                    }

                    type == PascalTokenTypes.KW_IN -> {
                        // Skip "in 'path'" part
                        current = current.treeNext
                        while (current != null) {
                            val curType = current.elementType
                            if (curType == PascalTokenTypes.COMMA || curType == PascalTokenTypes.SEMI) {
                                break
                            }
                            current = current.treeNext
                        }
                        if (current == null) break
                    }

                    type == PascalTokenTypes.COMMA -> {
                        current = skipIgnorableTokens(current.treeNext)
                    }

                    else -> {
                        current = skipIgnorableTokens(current.treeNext)
                    }
                }
            }
        }

        private fun buildDottedName(startNode: ASTNode): String {
            val name = StringBuilder(startNode.text)
            var next: ASTNode? = startNode.treeNext

            while (next != null) {
                when (next.elementType) {
                    PascalTokenTypes.DOT -> {
                        name.append(".")
                        next = next.treeNext
                        // Skip whitespace/comments
                        while (next != null && isIgnorable(next.elementType)) {
                            next = next.treeNext
                        }
                        if (next != null && next.elementType == PascalTokenTypes.IDENTIFIER) {
                            name.append(next.text)
                            next = next.treeNext
                        } else {
                            break
                        }
                    }
                    else -> {
                        if (isIgnorable(next.elementType)) {
                            next = next.treeNext
                        } else {
                            break
                        }
                    }
                }
            }
            return name.toString()
        }

        private fun skipIgnorableTokens(node: ASTNode?): ASTNode? {
            var current = node
            while (current != null && isIgnorable(current.elementType)) {
                current = current.treeNext
            }
            return current
        }

        private fun isIgnorable(type: IElementType): Boolean {
            return type == PascalTokenTypes.WHITE_SPACE ||
                   type == PascalTokenTypes.LINE_COMMENT ||
                   type == PascalTokenTypes.BLOCK_COMMENT ||
                   type == PascalTokenTypes.COMPILER_DIRECTIVE
        }
    }
}
