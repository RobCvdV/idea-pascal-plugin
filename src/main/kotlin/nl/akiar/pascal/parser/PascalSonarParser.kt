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

        private fun createDelphiFileConfig(): DelphiFileConfig {
            val preprocessorFactory = DelphiPreprocessorFactory(compilerVersion, platform)
            val typeFactory = TypeFactoryImpl(toolchain, compilerVersion)
            val searchPath = SearchPath.create(emptyList())

            return DelphiFile.createConfig(
                StandardCharsets.UTF_8.name(),
                preprocessorFactory,
                typeFactory,
                searchPath,
                definitions
            )
        }
    }

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        val text = builder.originalText.toString()

        if (text.isNotBlank()) {
            try {
                // LOG.info("PascalSonarParser: Starting parse of ${text.length} characters")

                val tempFile = File.createTempFile("pascal_parse", ".pas")
                tempFile.writeText(text)
                try {
                    // Create a fresh config per parse to avoid thread-safety issues in shared preprocessor components
                    val delphiFileConfig = createDelphiFileConfig()
                    val delphiFile = DelphiFile.from(tempFile, delphiFileConfig)
                    val ast = delphiFile.ast

                    if (ast != null) {
                        // LOG.info("PascalSonarParser: AST obtained successfully. Root class: ${ast.javaClass.name}")

                        val lineOffsets = calculateLineOffsets(text)
                        try {
                            // println("START MAPPING")
                            mapNode(ast, builder, lineOffsets)
                            // println("END MAPPING")
                        } catch (e: Exception) {
                            handleException(e, "Error during mapNode")
                        }
                    } else {
                        LOG.warn("PascalSonarParser: AST is null")
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("Empty files are not allowed") == true) {
                        // This happens when the file only contains comments or whitespace that the preprocessor removes
                        LOG.debug("PascalSonarParser: sonar-delphi reported empty file")
                    } else {
                        handleException(e, "Error parsing with sonar-delphi")
                    }
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                handleException(e, "Error in PascalSonarParser")
            }
        }

        // ALWAYS consume all tokens to prevent "Missed tokens" errors in IntelliJ
        advanceToEnd(builder)

        rootMarker.done(root)
        return builder.getTreeBuilt()
    }

    private fun handleException(e: Exception, message: String) {
        if (e is com.intellij.openapi.diagnostic.ControlFlowException || e is com.intellij.openapi.application.ReadAction.CannotReadException) {
            throw e
        }
        LOG.error(message, e)
    }

    private fun calculateLineOffsets(text: String): IntArray {
        val offsets = mutableListOf(0)
        for (i in text.indices) {
            if (text[i] == '\n') {
                offsets.add(i + 1)
            }
        }
        return offsets.toIntArray()
    }

    private fun getOffset(line: Int, column: Int, lineOffsets: IntArray): Int {
        if (line <= 0 || line > lineOffsets.size) return 0
        // sonar-delphi columns are 1-based, but column 1 means offset 0 in the line
        return (lineOffsets[line - 1] + (column - 1)).coerceAtLeast(0)
    }

    private fun mapNode(node: org.sonar.plugins.communitydelphi.api.ast.DelphiNode, builder: PsiBuilder, lineOffsets: IntArray) {
        val firstToken = node.firstToken
        val lastToken = node.lastToken

        val markerType = when (node) {
            is org.sonar.plugins.communitydelphi.api.ast.InterfaceSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_SECTION
            is org.sonar.plugins.communitydelphi.api.ast.ImplementationSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.IMPLEMENTATION_SECTION
            is org.sonar.plugins.communitydelphi.api.ast.UnitDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION
            is org.sonar.plugins.communitydelphi.api.ast.ProgramDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.PROGRAM_DECL_SECTION
            is org.sonar.plugins.communitydelphi.api.ast.LibraryDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.LIBRARY_DECL_SECTION
            is org.sonar.plugins.communitydelphi.api.ast.TypeDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION
            is org.sonar.plugins.communitydelphi.api.ast.TypeParameterNode -> nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER
            is org.sonar.plugins.communitydelphi.api.ast.NameDeclarationNode -> {
                val parent = node.parent
                val isVariableOrField = parent is org.sonar.plugins.communitydelphi.api.ast.NameDeclarationListNode || 
                                       parent is org.sonar.plugins.communitydelphi.api.ast.FormalParameterNode ||
                                       parent is org.sonar.plugins.communitydelphi.api.ast.VarDeclarationNode ||
                                       parent is org.sonar.plugins.communitydelphi.api.ast.FieldDeclarationNode ||
                                       parent?.javaClass?.simpleName?.contains("FormalParameterData", ignoreCase = true) == true
                if (isVariableOrField) {
                    nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_DEFINITION
                } else {
                    null
                }
            }
            // Add sections that contain declarations
            is org.sonar.plugins.communitydelphi.api.ast.VarSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION
            is org.sonar.plugins.communitydelphi.api.ast.ConstSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION
            is org.sonar.plugins.communitydelphi.api.ast.TypeSectionNode -> nl.akiar.pascal.psi.PascalElementTypes.TYPE_SECTION
            is org.sonar.plugins.communitydelphi.api.ast.FormalParameterNode -> nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER
            is org.sonar.plugins.communitydelphi.api.ast.RoutineDeclarationNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
            is org.sonar.plugins.communitydelphi.api.ast.RoutineImplementationNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
            is org.sonar.plugins.communitydelphi.api.ast.RoutineNode -> nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION
            else -> {
                // Heuristic for Unit references: if parent is UsesClauseNode and it's not mapped yet
                if (node.javaClass.simpleName.contains("UnitReference", ignoreCase = true) ||
                    node.javaClass.simpleName.contains("UsesItem", ignoreCase = true) ||
                    node.javaClass.simpleName.contains("Namespace", ignoreCase = true)) {
                    nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE
                } else {
                    null
                }
            }
        }

        if (firstToken == null || lastToken == null || firstToken.isImaginary || lastToken.isImaginary) {
            var marker: PsiBuilder.Marker? = null
            if (markerType != null) {
                marker = builder.mark()
            }

            val sortedChildren = node.children.sortedBy { 
                it.firstToken?.let { t -> getOffset(t.beginLine, t.beginColumn, lineOffsets) } ?: Int.MAX_VALUE
            }
            for (child in sortedChildren) {
                mapNode(child, builder, lineOffsets)
            }

            marker?.done(markerType!!)
            return
        }

        val startOffset = getOffset(firstToken.beginLine, firstToken.beginColumn, lineOffsets)
        val endOffset = getOffset(lastToken.endLine, lastToken.endColumn, lineOffsets) + lastToken.image.length

        // Skip nodes that end before current position
        if (endOffset <= builder.currentOffset) {
            return
        }

        var marker: PsiBuilder.Marker? = null
        if (markerType != null) {
            while (!builder.eof() && builder.currentOffset < startOffset) {
                builder.advanceLexer()
            }
            marker = builder.mark()
        }

        // Recursively map children before closing the marker
        // IMPORTANT: We must sort children by start offset to avoid backtracking the builder
        val sortedChildren = node.children.sortedBy { 
            it.firstToken?.let { t -> getOffset(t.beginLine, t.beginColumn, lineOffsets) } ?: Int.MAX_VALUE
        }

        for (child in sortedChildren) {
            mapNode(child, builder, lineOffsets)
        }

        if (marker != null) {
            // Ensure we advance to at least the end of this node's tokens
            while (!builder.eof() && builder.currentOffset < endOffset) {
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
