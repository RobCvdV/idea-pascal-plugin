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
import org.sonar.plugins.communitydelphi.api.ast.DelphiNode
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

            val config = au.com.integradev.delphi.file.DelphiFile.createConfig(
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
    }

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        val text = builder.originalText.toString()

        if (text.isNotBlank()) {
            try {
                val components = THREAD_LOCAL_COMPONENTS.get()
                val tempFile = components.tempFile
                tempFile.writeText(text)
                
                val delphiFile = au.com.integradev.delphi.file.DelphiFile.from(tempFile, components.config)
                val ast = delphiFile.ast

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
        return builder.getTreeBuilt()
    }

    private fun handleException(e: Exception, message: String, isParsingError: Boolean = false) {
        if (e is com.intellij.openapi.diagnostic.ControlFlowException || e is com.intellij.openapi.application.ReadAction.CannotReadException) {
            throw e
        }
        if (isParsingError) {
            LOG.warn("$message: ${e.message}")
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

        val markerType = when {
            node is org.sonar.plugins.communitydelphi.api.ast.InterfaceSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ImplementationSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.IMPLEMENTATION_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.UnitDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION
            node.javaClass.simpleName.contains("UsesClause", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.USES_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ProgramDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.PROGRAM_DECL_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.LibraryDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.LIBRARY_DECL_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.TypeDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION
            node is org.sonar.plugins.communitydelphi.api.ast.TypeParameterNode -> nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER
            node.javaClass.simpleName.contains("FormalParameter", ignoreCase = true) && !node.javaClass.simpleName.contains("List", ignoreCase = true) -> {
                nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER
            }
            node is org.sonar.plugins.communitydelphi.api.ast.VarSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.ConstSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.CONST_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.TypeSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.TYPE_SECTION
            node is org.sonar.plugins.communitydelphi.api.ast.PropertyNode -> nl.akiar.pascal.psi.PascalElementTypes.PROPERTY_DEFINITION
            node is org.sonar.plugins.communitydelphi.api.ast.RoutineDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
            node is org.sonar.plugins.communitydelphi.api.ast.RoutineImplementationNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
            node is org.sonar.plugins.communitydelphi.api.ast.RoutineNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
            node is org.sonar.plugins.communitydelphi.api.ast.NameDeclarationNode -> {
                val parent = node.parent
                val parentName = parent?.javaClass?.simpleName ?: ""
                
                val isVarOrParam = parentName.contains("FormalParameter", ignoreCase = true) ||
                                  parentName.contains("NameDeclarationList", ignoreCase = true) ||
                                  parentName.contains("VarDeclaration", ignoreCase = true) ||
                                  parentName.contains("FieldDeclaration", ignoreCase = true) ||
                                  parentName.contains("ConstDeclaration", ignoreCase = true)
                
                if (isVarOrParam) {
                    nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_DEFINITION
                } else {
                    null
                }
            }
            node.javaClass.simpleName.contains("UnitReference", ignoreCase = true) ||
            node.javaClass.simpleName.contains("UnitImport", ignoreCase = true) ||
            node.javaClass.simpleName.contains("UsesItem", ignoreCase = true) ||
            node.javaClass.simpleName.contains("Namespace", ignoreCase = true) ||
            node.javaClass.simpleName.contains("QualifiedNameDeclaration", ignoreCase = true) -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
            else -> null
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
        }
    }

    private fun advanceToEnd(builder: PsiBuilder) {
        while (!builder.eof()) {
            builder.advanceLexer()
        }
    }
}
