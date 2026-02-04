package nl.akiar.pascal.parser

import au.com.integradev.delphi.compiler.CompilerVersion
import au.com.integradev.delphi.compiler.Platform
import au.com.integradev.delphi.compiler.Toolchain
import au.com.integradev.delphi.file.DelphiFile
import au.com.integradev.delphi.file.DelphiFileConfig
import au.com.integradev.delphi.preprocessor.DelphiPreprocessorFactory
import au.com.integradev.delphi.preprocessor.search.SearchPath
import au.com.integradev.delphi.type.factory.TypeFactoryImpl
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.tree.IElementType
import java.io.File
import java.nio.charset.StandardCharsets

class PascalSonarParser : PsiParser {
    companion object {
        private val LOG = Logger.getInstance(PascalSonarParser::class.java)

        private val compilerVersion = CompilerVersion.fromVersionNumber("35.0") // Delphi 11
        private val platform = Platform.WINDOWS
        private val toolchain = Toolchain.DCC32
        // Default definitions for Windows x86 - required for RTL units like System.Classes
        // which have conditional procedure bodies that become empty without platform definitions
        private val definitions = setOf("MSWINDOWS", "WIN32", "CPUX86")

        private data class ParserComponents(
            val preprocessorFactory: DelphiPreprocessorFactory,
            val typeFactory: TypeFactoryImpl,
            val config: DelphiFileConfig,
            val tempFile: File
        )

        private val THREAD_LOCAL_COMPONENTS = ThreadLocal.withInitial {
            val preprocessorFactory = DelphiPreprocessorFactory(compilerVersion, platform)
            val typeFactory = TypeFactoryImpl(toolchain, compilerVersion)
            
            // Create a dummy search path that doesn't look for anything.
            // This avoids sonar-delphi's DefaultSearchPath which can cause freezes
            // by recursively indexing directories (especially when files are in /tmp).
            val searchPath = object : SearchPath {
                override fun search(fileName: String, relativeTo: java.nio.file.Path?): java.nio.file.Path? = null
                override fun getRootDirectories(): Set<java.nio.file.Path> = emptySet()
            }

            val config = DelphiFile.createConfig(
                StandardCharsets.UTF_8.name(),
                preprocessorFactory,
                typeFactory,
                searchPath,
                definitions
            )
            
            // Create a private temp directory for this thread to isolate the temp file
            // and avoid any potential directory scanning issues.
            val tempDir = java.nio.file.Files.createTempDirectory("pascal_parse_").toFile()
            tempDir.deleteOnExit()
            val tempFile = File(tempDir, "input.pas")
            tempFile.deleteOnExit()
            
            ParserComponents(preprocessorFactory, typeFactory, config, tempFile)
        }

        private val DIAG_ENABLED: Boolean = java.lang.Boolean.getBoolean("pascal.parser.diag")
        private val DIAG_ONLY_UNIT: String? = System.getProperty("pascal.parser.diag.onlyUnit")
        private val DIAG_ONLY_REGEX: Regex? = System.getProperty("pascal.parser.diag.onlyUnitRegex")?.let { Regex(it, setOf(RegexOption.IGNORE_CASE)) }
        private val CURRENT_SHOULD_DIAG: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
        private fun diag(msg: String) { if (DIAG_ENABLED && CURRENT_SHOULD_DIAG.get()) LOG.info("[PascalParser][Diag] $msg") }

        // Lightweight parse statistics for confirmation of produced PSI elements
        private data class ParseStats(
            var unitDeclCount: Int = 0,
            var usesSectionCount: Int = 0,
            var unitRefCount: Int = 0,
            val unitDeclSamples: MutableList<String> = mutableListOf(),
            val usesSectionSamples: MutableList<String> = mutableListOf(),
            val unitRefSamples: MutableList<String> = mutableListOf()
        )
        private val STATS_TL = ThreadLocal.withInitial { ParseStats() }
        private const val MAX_SAMPLES = 8
        private fun addSample(list: MutableList<String>, value: String) {
            if (list.size < MAX_SAMPLES) list.add(value)
        }
    }

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        var text = builder.originalText.toString()

        // Reset stats per parse
        STATS_TL.set(ParseStats())

        // Try to detect the unit name early for diagnostics filtering
        val headerRegex = Regex("""(?i)\bunit\s+([A-Za-z_][\w.]*)\s*;""")
        val detectedUnit = headerRegex.find(text)?.groupValues?.getOrNull(1)
        // Apply both legacy diag-only properties and the new UnitLogFilter (if configured)
        val unitLogFilterAllows = nl.akiar.pascal.log.UnitLogFilter.shouldLog(detectedUnit)
        val shouldDiagThisFile = if (!DIAG_ENABLED) false else when {
            DIAG_ONLY_UNIT != null -> detectedUnit?.equals(DIAG_ONLY_UNIT, ignoreCase = true) == true && unitLogFilterAllows
            DIAG_ONLY_REGEX != null -> (detectedUnit != null && DIAG_ONLY_REGEX.containsMatchIn(detectedUnit)) && unitLogFilterAllows
            else -> unitLogFilterAllows
        }
        CURRENT_SHOULD_DIAG.set(shouldDiagThisFile)
        diag("parse start len=${text.length} unit=${detectedUnit ?: "<unknown>"}")

        // Strip include directives to prevent sonar-delphi preprocessor from attempting to resolve .inc files
        // Handles {$I filename.inc} and {$INCLUDE filename.inc} (case-insensitive), with optional spaces
        // Keeps directive length similar by replacing with whitespace to preserve offsets roughly
        // Note: use RegexOption.IGNORE_CASE to avoid unsupported escape sequences in Kotlin string.
        val includeDirectiveRegex = Regex("""\{\$\s*(i|include)\b[^}]*}""", setOf(RegexOption.IGNORE_CASE))
        text = text.replace(includeDirectiveRegex) { match ->
            " ".repeat(match.value.length)
        }

        if (text.isNotBlank()) {
            try {
                val components = THREAD_LOCAL_COMPONENTS.get()
                val tempFile = components.tempFile
                tempFile.writeText(text)
                diag("temp write ok path=${tempFile.path}")

                val delphiFile = DelphiFile.from(tempFile, components.config)
                val ast = delphiFile.ast
                diag("ast ready? ${ast != null}")

                if (ast != null) {
                    val lineOffsets = calculateLineOffsets(text)
                    try {
                        mapNode(ast, builder, lineOffsets)
                    } catch (e: Exception) {
                        handleException(e, "Error during mapNode")
                    }
                } else {
                    LOG.warn("PascalSonarParser: AST is null")
                }
            } catch (e: Exception) {
                if (e.message?.contains("Empty files are not allowed") == true) {
                    LOG.debug("PascalSonarParser: sonar-delphi reported empty file")
                } else {
                    handleException(e, "Error parsing with sonar-delphi", true)
                }
            }
        }

        // Minimal guarded PSI fallback: if sonar-delphi produced no unit/uses/refs, synthesize from text
        runFallbackIfNoUnitElements(builder, text)

        // ALWAYS consume all tokens to prevent "Missed tokens" errors in IntelliJ
        advanceToEnd(builder)

        rootMarker.done(root)
        // Emit a concise parse summary for diagnostics
        val stats = STATS_TL.get()
        diag("summary: UNIT_DECL_SECTION=${stats.unitDeclCount} USES_SECTION=${stats.usesSectionCount} UNIT_REFERENCE=${stats.unitRefCount}")
        if (stats.unitDeclSamples.isNotEmpty()) diag("sample UNIT_DECL_SECTION: ${stats.unitDeclSamples}")
        if (stats.usesSectionSamples.isNotEmpty()) diag("sample USES_SECTION: ${stats.usesSectionSamples}")
        if (stats.unitRefSamples.isNotEmpty()) diag("sample UNIT_REFERENCE: ${stats.unitRefSamples}")
        diag("parse done, tree built")
        CURRENT_SHOULD_DIAG.set(false)
        return builder.getTreeBuilt()
    }

    private fun runFallbackIfNoUnitElements(builder: PsiBuilder, text: String) {
        val stats = STATS_TL.get()
        if (stats.unitDeclCount > 0 || stats.usesSectionCount > 0 || stats.unitRefCount > 0) return

        // Detect unit header and interface/uses locations
        val unitHeaderRegex = Regex("""(?is)\bunit\s+([A-Za-z_][\w.]*)\s*;""")
        val interfaceRegex = Regex("""(?is)\binterface\b""")
        val usesRegex = Regex("""(?is)\buses\b""")

        val unitMatch = unitHeaderRegex.find(text) ?: run {
            return
        }
        val unitStart = unitMatch.range.first
        val unitEnd = unitMatch.range.last + 1

        // Synthesize UNIT_DECL_SECTION for header span
        synthesize(builder, unitStart, unitEnd, nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION)

        // Try to find uses section in interface part
        val interfaceMatch = interfaceRegex.find(text)
        val usesMatch = usesRegex.find(text, startIndex = interfaceMatch?.range?.first ?: 0)
        if (usesMatch != null) {
            // Heuristic: capture until the next ';' after 'uses'
            val usesStart = usesMatch.range.first
            var usesEnd = usesStart
            while (usesEnd < text.length && text[usesEnd] != ';') usesEnd++
            if (usesEnd < text.length) usesEnd++ // include semicolon
            synthesize(builder, usesStart, usesEnd, nl.akiar.pascal.psi.PascalElementTypes.USES_SECTION)
        }

        diag("[fallback] synthesized UNIT/USES from text for file with zero sonar elements")
    }

    private fun synthesize(builder: PsiBuilder, startOffset: Int, endOffset: Int, type: IElementType) {
        // Advance to start, mark, advance to end, then done
        while (!builder.eof() && builder.currentOffset < startOffset) {
            builder.advanceLexer()
        }
        val marker = builder.mark()
        while (!builder.eof() && builder.currentOffset < endOffset) {
            builder.advanceLexer()
        }
        marker.done(type)
        val stats = STATS_TL.get()
        when (type) {
            nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION -> stats.unitDeclCount++
            nl.akiar.pascal.psi.PascalElementTypes.USES_SECTION -> stats.usesSectionCount++
        }
    }

    private fun handleException(e: Exception, message: String, isParsingError: Boolean = false) {
        if (e is com.intellij.openapi.diagnostic.ControlFlowException) {
            throw e
        }
        if (isParsingError) {
            // no-op
        } else {
            LOG.error(message, e)
        }
    }

    private fun calculateLineOffsets(text: String): IntArray {
        var count = 1
        for (c in text) {
            if (c == '\n') count++
        }
        val offsets = IntArray(count)
        offsets[0] = 0
        var idx = 1
        for (i in text.indices) {
            if (text[i] == '\n') {
                offsets[idx++] = i + 1
            }
        }
        return offsets
    }

    private fun getOffset(line: Int, column: Int, lineOffsets: IntArray): Int {
        if (line <= 0 || line > lineOffsets.size) return 0
        // sonar-delphi columns are 1-based, but column 1 means offset 0 in the line
        return (lineOffsets[line - 1] + (column - 1)).coerceAtLeast(0)
    }

    private fun mapNode(node: org.sonar.plugins.communitydelphi.api.ast.DelphiNode, builder: PsiBuilder, lineOffsets: IntArray) {
        com.intellij.openapi.progress.ProgressManager.checkCanceled()

        val firstToken = node.firstToken
        val lastToken = node.lastToken

        if (firstToken == null || lastToken == null || firstToken.isImaginary || lastToken.isImaginary) {
            for (child in node.children) {
                mapNode(child, builder, lineOffsets)
            }
            return
        }

        var nodeStartOffset = getOffset(firstToken.beginLine, firstToken.beginColumn, lineOffsets)
        var nodeEndOffset = getOffset(lastToken.endLine, lastToken.endColumn, lineOffsets) + 1

        // Determine element type, with special handling for unit header and uses items
        val markerType = when {
            // ============================================================================
            // Unit Structure Sections
            // ============================================================================
            node is org.sonar.plugins.communitydelphi.api.ast.InterfaceSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ImplementationSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.IMPLEMENTATION_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.UnitDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ProgramDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.PROGRAM_DECL_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.LibraryDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.LIBRARY_DECL_SECTION

            // ============================================================================
            // Uses Clause / Unit References
            // ============================================================================
            node.javaClass.simpleName.contains("QualifiedNameDeclaration", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("Namespace", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("UnitReference", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("UnitImport", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("UsesItem", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("UsesClause", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.USES_SECTION

            // ============================================================================
            // Type Definitions - Specific Types
            // ============================================================================
            // Check for specific type definitions before generic TypeDeclarationNode
            node.javaClass.simpleName.contains("ClassType", ignoreCase = true) &&
                !node.javaClass.simpleName.contains("Reference", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.CLASS_TYPE
            node.javaClass.simpleName.contains("RecordType", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.RECORD_TYPE
            node.javaClass.simpleName.contains("InterfaceType", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_TYPE
            node.javaClass.simpleName.contains("EnumType", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.ENUM_TYPE
            // Generic type declaration (fallback)
            node is org.sonar.plugins.communitydelphi.api.ast.TypeDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION

            node is org.sonar.plugins.communitydelphi.api.ast.TypeParameterNode -> nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER

            // ============================================================================
            // Attributes/Decorators
            // ============================================================================
            // AttributeNode is the actual attribute (name + optional args) - brackets stripped
            node is org.sonar.plugins.communitydelphi.api.ast.AttributeNode -> nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_DEFINITION
            // AttributeListNode is the container for ALL attribute groups - this becomes ATTRIBUTE_LIST
            node is org.sonar.plugins.communitydelphi.api.ast.AttributeListNode -> nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_LIST
            // AttributeGroupNode represents a single [...] group - skip it (just process children)
            // The brackets become siblings of ATTRIBUTE_DEFINITION inside ATTRIBUTE_LIST
            node is org.sonar.plugins.communitydelphi.api.ast.AttributeGroupNode -> null

            // ============================================================================
            // Scope/Body Sections (for variable scope checking)
            // ============================================================================
            node.javaClass.simpleName.contains("RoutineBody", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_BODY
            node.javaClass.simpleName.contains("VisibilitySection", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.VISIBILITY_SECTION

            // ============================================================================
            // Enum Elements
            // ============================================================================
            node.javaClass.simpleName.contains("EnumElement", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT

            // ============================================================================
            // Field Definitions (in records/classes)
            // ============================================================================
            node.javaClass.simpleName.contains("FieldDeclaration", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.FIELD_DEFINITION

            // ============================================================================
            // Parameters
            // ============================================================================
            node.javaClass.simpleName.contains("FormalParameter", ignoreCase = true) &&
                !node.javaClass.simpleName.contains("List", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER
            node.javaClass.simpleName.contains("Parameter", ignoreCase = true) &&
                !node.javaClass.simpleName.contains("TypeParameter", ignoreCase = true) &&
                !node.javaClass.simpleName.contains("List", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER

            // ============================================================================
            // Variable/Constant Definitions with Context
            // ============================================================================
            // Note: We use VARIABLE_DEFINITION (stub-based) for all variable declarations
            // to ensure proper indexing and IDE features. The variableKind property on
            // PascalVariableDefinition distinguishes between GLOBAL, FIELD, PARAMETER, LOCAL.
            node is org.sonar.plugins.communitydelphi.api.ast.NameDeclarationNode -> {
                val parent = node.parent
                val parentName = parent?.javaClass?.simpleName ?: ""

                diag("NameDeclarationNode: nodeName=${node.javaClass.simpleName} parentName=$parentName")

                // If in unit header or uses, do not produce VARIABLE_DEFINITION
                val inUnitOrUses = parentName.contains("UnitDeclaration", ignoreCase = true) ||
                                   parentName.contains("Uses", ignoreCase = true)

                when {
                    inUnitOrUses -> null
                    // All variable-like declarations (vars, consts, fields, params) use stub-based VARIABLE_DEFINITION
                    parentName.contains("ConstDeclaration", ignoreCase = true) ||
                        parentName.contains("FieldDeclaration", ignoreCase = true) ||
                        parentName.contains("FormalParameter", ignoreCase = true) ||
                        parentName.contains("NameDeclarationList", ignoreCase = true) ||
                        parentName.contains("VarDeclaration", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_DEFINITION
                    else -> {
                        diag("NameDeclarationNode: unhandled parentName=$parentName")
                        null
                    }
                }
            }

            // ============================================================================
            // Declaration Sections
            // ============================================================================
            node is org.sonar.plugins.communitydelphi.api.ast.VarSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ConstSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.CONST_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.TypeSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.TYPE_SECTION

            // ============================================================================
            // Properties
            // ============================================================================
            node is org.sonar.plugins.communitydelphi.api.ast.PropertyNode -> nl.akiar.pascal.psi.PascalElementTypes.PROPERTY_DEFINITION

            // ============================================================================
            // Routines - use stub-based ROUTINE_DECLARATION for all routines
            // ============================================================================
            // Note: We use the stub-based ROUTINE_DECLARATION for proper indexing.
            // The PascalRoutine implementation can determine specific routine type
            // (standalone, method, constructor, etc.) based on context.
            node is org.sonar.plugins.communitydelphi.api.ast.RoutineDeclarationNode ||
                node is org.sonar.plugins.communitydelphi.api.ast.RoutineImplementationNode ||
                node is org.sonar.plugins.communitydelphi.api.ast.RoutineNode ->
                    nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION

            // ============================================================================
            // Labels
            // ============================================================================
            node.javaClass.simpleName.contains("LabelDeclaration", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.LABEL_DEFINITION

            else -> null
        }
        if (markerType != null) {
            diag("map: ${node.javaClass.simpleName} -> ${markerType}")
        }

        if (markerType != null) {
            // Global skip of leading punctuation for any mapped node
            val text = builder.originalText
            while (nodeStartOffset < text.length && (text[nodeStartOffset] == '(' || text[nodeStartOffset] == ',' || text[nodeStartOffset] == ';' || text[nodeStartOffset] == '<' || text[nodeStartOffset] == '>' || text[nodeStartOffset].isWhitespace())) {
                nodeStartOffset++
            }

            // Special handling for ATTRIBUTE_DEFINITION: strip leading '[' and whitespace
            // sonar-delphi's AttributeNode includes the bracket, but we only want the name + args
            if (markerType == nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_DEFINITION) {
                while (nodeStartOffset < text.length && (text[nodeStartOffset] == '[' || text[nodeStartOffset].isWhitespace())) {
                    nodeStartOffset++
                }
                // Also strip trailing ']' and whitespace
                while (nodeEndOffset > nodeStartOffset && nodeEndOffset <= text.length && (text[nodeEndOffset - 1] == ']' || text[nodeEndOffset - 1].isWhitespace())) {
                    nodeEndOffset--
                }
            }

            // Global strip of trailing punctuation
            while (nodeEndOffset > nodeStartOffset && nodeEndOffset <= text.length && (text[nodeEndOffset - 1] == ')' || text[nodeEndOffset - 1] == ',' || text[nodeEndOffset - 1] == ';' || text[nodeEndOffset - 1] == '<' || text[nodeEndOffset - 1] == '>' || text[nodeEndOffset - 1].isWhitespace())) {
                nodeEndOffset--
            }

            // Collect stats and a few samples
            val stats = STATS_TL.get()
            when (markerType) {
                nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION -> {
                    stats.unitDeclCount++
                    val snippet = try { text.subSequence(nodeStartOffset, nodeEndOffset).toString().take(120) } catch (_: Throwable) { "" }
                    if (snippet.isNotBlank()) addSample(stats.unitDeclSamples, snippet)
                }
                nl.akiar.pascal.psi.PascalElementTypes.USES_SECTION -> {
                    stats.usesSectionCount++
                    val snippet = try { text.subSequence(nodeStartOffset, nodeEndOffset).toString().take(120) } catch (_: Throwable) { "" }
                    if (snippet.isNotBlank()) addSample(stats.usesSectionSamples, snippet)
                }
                nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE -> {
                    stats.unitRefCount++
                    val snippet = try { text.subSequence(nodeStartOffset, nodeEndOffset).toString().take(80) } catch (_: Throwable) { "" }
                    if (snippet.isNotBlank()) addSample(stats.unitRefSamples, snippet)
                }
            }
        }

        if (nodeEndOffset <= builder.currentOffset) {
            return
        }

        var marker: PsiBuilder.Marker? = null
        if (markerType != null) {
            while (!builder.eof() && builder.currentOffset < nodeStartOffset) {
                builder.advanceLexer()
            }
            marker = builder.mark()
        }

        // Special handling for TYPE_DEFINITION and ROUTINE_DECLARATION: synthesize ATTRIBUTE_LIST/ATTRIBUTE_DEFINITION
        // for leading attribute brackets that sonar-delphi doesn't wrap in AttributeListNode
        if (markerType == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION ||
            markerType == nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION) {
            synthesizeAttributesForDeclaration(builder, nodeStartOffset, nodeEndOffset)
        }

        for (child in node.children) {
            mapNode(child, builder, lineOffsets)
        }

        if (marker != null) {
            while (!builder.eof() && builder.currentOffset < nodeEndOffset) {
                builder.advanceLexer()
            }
            marker.done(markerType!!)
            if (DIAG_ENABLED) {
                diag("done: ${markerType} span=${nodeStartOffset}..${nodeEndOffset}")
            }
        }
    }

    private fun advanceToEnd(builder: PsiBuilder) {
        while (!builder.eof()) {
            builder.advanceLexer()
        }
    }

    /**
     * Synthesize ATTRIBUTE_LIST and ATTRIBUTE_DEFINITION PSI elements for declarations.
     *
     * Sonar-delphi doesn't always create AttributeListNode for type declarations and some routine
     * declarations. The attribute tokens ([AttrName] or [AttrName(args)]) are absorbed directly
     * into the declaration node span. This function detects such attributes and creates proper
     * PSI structure for them.
     *
     * @param builder The PsiBuilder positioned at the start of the declaration
     * @param nodeStartOffset The start offset of the declaration node (includes attributes)
     * @param nodeEndOffset The end offset of the declaration node
     */
    private fun synthesizeAttributesForDeclaration(builder: PsiBuilder, nodeStartOffset: Int, nodeEndOffset: Int) {
        val text = builder.originalText
        var pos = builder.currentOffset

        // Look for attribute pattern: [AttrName] or [AttrName(args)]
        // Collect all consecutive attribute groups starting from current position
        data class AttrRange(val bracketStart: Int, val contentStart: Int, val contentEnd: Int, val bracketEnd: Int)
        val attributeRanges = mutableListOf<AttrRange>()

        var scanPos = pos
        while (scanPos < nodeEndOffset) {
            // Skip whitespace
            while (scanPos < nodeEndOffset && text[scanPos].isWhitespace()) scanPos++

            if (scanPos >= nodeEndOffset || text[scanPos] != '[') break

            // Found '[', look for matching ']'
            val bracketStart = scanPos
            scanPos++ // skip '['
            val contentStart = scanPos

            var bracketDepth = 1
            var parenDepth = 0

            while (scanPos < nodeEndOffset && bracketDepth > 0) {
                when (text[scanPos]) {
                    '[' -> bracketDepth++
                    ']' -> {
                        bracketDepth--
                        if (bracketDepth == 0) {
                            val contentEnd = scanPos
                            scanPos++ // skip ']'
                            attributeRanges.add(AttrRange(bracketStart, contentStart, contentEnd, scanPos))
                        }
                    }
                    '(' -> parenDepth++
                    ')' -> parenDepth--
                }
                if (bracketDepth > 0) scanPos++
            }
        }

        if (attributeRanges.isEmpty()) return

        diag("synthesizing ${attributeRanges.size} attributes for type declaration")

        // Create ATTRIBUTE_LIST marker containing all attributes
        // First, advance to the first bracket
        while (!builder.eof() && builder.currentOffset < attributeRanges.first().bracketStart) {
            builder.advanceLexer()
        }

        val listMarker = builder.mark()

        // Create ATTRIBUTE_DEFINITION for each [attr]
        for (attrRange in attributeRanges) {
            // Advance to bracket start (skip whitespace between attributes)
            while (!builder.eof() && builder.currentOffset < attrRange.bracketStart) {
                builder.advanceLexer()
            }

            // Consume the '['
            if (!builder.eof()) {
                builder.advanceLexer()
            }

            // Create marker for ATTRIBUTE_DEFINITION (the content without brackets)
            val defMarker = builder.mark()

            // Advance to the content end (before ']')
            while (!builder.eof() && builder.currentOffset < attrRange.contentEnd) {
                builder.advanceLexer()
            }

            defMarker.done(nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_DEFINITION)

            // Consume the ']'
            if (!builder.eof() && builder.currentOffset < attrRange.bracketEnd) {
                builder.advanceLexer()
            }
        }

        listMarker.done(nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_LIST)
    }
}
