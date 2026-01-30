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
        private val definitions = emptySet<String>()

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
            node is org.sonar.plugins.communitydelphi.api.ast.InterfaceSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ImplementationSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.IMPLEMENTATION_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.UnitDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION
            // Treat various sonar nodes representing unit names as UNIT_REFERENCE
            node.javaClass.simpleName.contains("QualifiedNameDeclaration", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("Namespace", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("UnitReference", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("UnitImport", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("UsesItem", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            node.javaClass.simpleName.contains("UsesClause", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.USES_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ProgramDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.PROGRAM_DECL_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.LibraryDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.LIBRARY_DECL_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.TypeDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION
            node is org.sonar.plugins.communitydelphi.api.ast.TypeParameterNode -> nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER
            // Map formal parameter sections robustly
            node.javaClass.simpleName.contains("FormalParameter", ignoreCase = true) && !node.javaClass.simpleName.contains("List", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER
            node.javaClass.simpleName.contains("Parameter", ignoreCase = true) &&
                !node.javaClass.simpleName.contains("TypeParameter", ignoreCase = true) &&
                !node.javaClass.simpleName.contains("List", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER
            // Avoid misclassifying identifiers in unit/uses contexts as variables/parameters
            node is org.sonar.plugins.communitydelphi.api.ast.NameDeclarationNode -> {
                val parent = node.parent
                val parentName = parent?.javaClass?.simpleName ?: ""

                // If in unit header or uses, do not produce VARIABLE_DEFINITION
                val inUnitOrUses = parentName.contains("UnitDeclaration", ignoreCase = true) ||
                                   parentName.contains("Uses", ignoreCase = true)

                val isVarOrParam = parentName.contains("FormalParameter", ignoreCase = true) ||
                                  parentName.contains("NameDeclarationList", ignoreCase = true) ||
                                  parentName.contains("VarDeclaration", ignoreCase = true) ||
                                  parentName.contains("FieldDeclaration", ignoreCase = true) ||
                                  parentName.contains("ConstDeclaration", ignoreCase = true)

                if (!inUnitOrUses && isVarOrParam) {
                    nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_DEFINITION
                } else {
                    null
                }
            }
            node is org.sonar.plugins.communitydelphi.api.ast.VarSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ConstSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.CONST_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.TypeSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.TYPE_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.PropertyNode -> nl.akiar.pascal.psi.PascalElementTypes.PROPERTY_DEFINITION
            node is org.sonar.plugins.communitydelphi.api.ast.RoutineDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
            node is org.sonar.plugins.communitydelphi.api.ast.RoutineImplementationNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
            node is org.sonar.plugins.communitydelphi.api.ast.RoutineNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
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
}
