package nl.akiar.pascal.parser

import au.com.integradev.delphi.compiler.CompilerVersion
import au.com.integradev.delphi.compiler.Platform
import au.com.integradev.delphi.compiler.Toolchain
import au.com.integradev.delphi.file.DelphiFile
import au.com.integradev.delphi.file.DelphiFileConfig
import au.com.integradev.delphi.preprocessor.DelphiPreprocessorFactory
import au.com.integradev.delphi.preprocessor.search.SearchPath
import au.com.integradev.delphi.type.factory.TypeFactoryImpl
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.File
import java.nio.charset.StandardCharsets

class SonarAstRtlTest {

    private fun makeConfig(defs: Set<String>): DelphiFileConfig {
        val compilerVersion = CompilerVersion.fromVersionNumber("35.0") // Delphi 11
        val platform = Platform.WINDOWS
        val toolchain = Toolchain.DCC32
        val preprocessorFactory = DelphiPreprocessorFactory(compilerVersion, platform)
        val typeFactory = TypeFactoryImpl(toolchain, compilerVersion)
        val searchPath = object : SearchPath {
            override fun search(fileName: String, relativeTo: java.nio.file.Path?) = null
            override fun getRootDirectories(): Set<java.nio.file.Path> = emptySet()
        }
        return DelphiFile.createConfig(
            StandardCharsets.UTF_8.name(),
            preprocessorFactory,
            typeFactory,
            searchPath,
            defs
        )
    }

    /**
     * Parse result containing either a node or an error with diagnostics.
     */
    data class ParseResult(
        val node: org.sonar.plugins.communitydelphi.api.ast.DelphiNode?,
        val error: Throwable?,
        val errorContext: String?
    )

    private fun parseAstRootFor(path: String, defs: Set<String> = emptySet()): ParseResult {
        val file = File(path)
        require(file.exists()) { "Test data not found: $path" }
        val cfg = makeConfig(defs)
        return try {
            val delphiFile = DelphiFile.from(file, cfg)
            val astRoot = delphiFile.ast
            ParseResult(astRoot as? org.sonar.plugins.communitydelphi.api.ast.DelphiNode, null, null)
        } catch (e: Throwable) {
            // Extract context from exception chain
            val context = buildString {
                var cause: Throwable? = e
                while (cause != null) {
                    appendLine("  ${cause.javaClass.simpleName}: ${cause.message}")
                    cause = cause.cause
                }
            }
            ParseResult(null, e, context)
        }
    }

    private fun collectNodeSimpleNames(root: org.sonar.plugins.communitydelphi.api.ast.DelphiNode?): Set<String> {
        if (root == null) return emptySet()
        val names = mutableSetOf<String>()
        val q: java.util.ArrayDeque<org.sonar.plugins.communitydelphi.api.ast.DelphiNode> = java.util.ArrayDeque()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            names.add(n.javaClass.simpleName)
            // children is a raw list in the API; cast defensively
            for (childAny in n.children) {
                val child = childAny as? org.sonar.plugins.communitydelphi.api.ast.DelphiNode ?: continue
                q.add(child)
            }
        }
        return names
    }

    private fun collectNodeCounts(root: org.sonar.plugins.communitydelphi.api.ast.DelphiNode?): Map<String, Int> {
        if (root == null) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        val q: java.util.ArrayDeque<org.sonar.plugins.communitydelphi.api.ast.DelphiNode> = java.util.ArrayDeque()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val name = n.javaClass.simpleName
            counts[name] = (counts[name] ?: 0) + 1
            for (childAny in n.children) {
                val child = childAny as? org.sonar.plugins.communitydelphi.api.ast.DelphiNode ?: continue
                q.add(child)
            }
        }
        return counts
    }

    private fun printDiagnostics(label: String, result: ParseResult) {
        println("\n=== DIAGNOSTICS: $label ===")
        if (result.error != null) {
            println("PARSE ERROR:")
            println(result.errorContext)
        }
        if (result.node != null) {
            val counts = collectNodeCounts(result.node)
            println("Node counts (${counts.size} unique types, ${counts.values.sum()} total):")
            // Print key nodes first (match with "Impl" suffix)
            val keyPatterns = listOf("UnitDeclaration", "UsesClause", "UnitImport",
                                     "InterfaceSection", "ImplementationSection",
                                     "TypeDeclaration", "RoutineDeclaration")
            for (pattern in keyPatterns) {
                val matchingEntries = counts.entries.filter { it.key.contains(pattern) }
                val total = matchingEntries.sumOf { it.value }
                println("  *$pattern*: $total")
            }
            // Print all others sorted by count
            println("  --- all types by count ---")
            counts.entries.sortedByDescending { it.value }.take(30).forEach { (name, count) ->
                println("  $name: $count")
            }
        } else {
            println("No AST available (parse failed)")
        }
        println("=== END DIAGNOSTICS ===\n")
    }

    private fun hasNodeMatching(names: Set<String>, pattern: String): Boolean {
        return names.any { it.contains(pattern) }
    }

    @Test
    fun systemCharacter_has_Unit_and_Uses() {
        val result = parseAstRootFor("src/test/data/System.Character.pas")
        printDiagnostics("System.Character.pas", result)

        if (result.error != null) {
            fail("Parse failed: ${result.errorContext}")
        }
        val names = collectNodeSimpleNames(result.node)
        assertTrue("System.Character should contain UnitDeclarationNode*. Found: $names",
                   hasNodeMatching(names, "UnitDeclaration"))
        assertTrue("System.Character should contain UsesClauseNode*. Found: $names",
                   hasNodeMatching(names, "UsesClause"))
    }

    // Default definitions matching PascalSonarParser defaults (Windows x86)
    private val defaultDefs = setOf("MSWINDOWS", "WIN32", "CPUX86")

    @Test
    fun systemClasses_defaultDefs_has_Unit_and_Uses() {
        // Use default definitions (Windows x86) - same as PascalSonarParser
        val result = parseAstRootFor("src/test/data/System.Classes.pas", defaultDefs)
        printDiagnostics("System.Classes.pas (default defs: MSWINDOWS+WIN32+CPUX86)", result)

        if (result.error != null) {
            fail("Parse failed: ${result.errorContext}")
        }
        val names = collectNodeSimpleNames(result.node)
        assertTrue("System.Classes (default defs) should contain UnitDeclarationNode*. Found: $names",
                   hasNodeMatching(names, "UnitDeclaration"))
        assertTrue("System.Classes (default defs) should contain UsesClauseNode*. Found: $names",
                   hasNodeMatching(names, "UsesClause"))
    }

    @Test
    fun systemClasses_windowsDefs_has_Unit_and_Uses() {
        // MSWINDOWS alone is not enough - the file also uses CPUX86/CPUX64 conditionals
        val defs = setOf("MSWINDOWS", "CPUX64", "WIN64")
        val result = parseAstRootFor("src/test/data/System.Classes.pas", defs)
        printDiagnostics("System.Classes.pas (MSWINDOWS+CPUX64)", result)

        if (result.error != null) {
            fail("Parse failed: ${result.errorContext}")
        }
        val names = collectNodeSimpleNames(result.node)
        assertTrue("System.Classes (MSWINDOWS+CPUX64) should contain UnitDeclarationNode*. Found: $names",
                   hasNodeMatching(names, "UnitDeclaration"))
        assertTrue("System.Classes (MSWINDOWS+CPUX64) should contain UsesClauseNode*. Found: $names",
                   hasNodeMatching(names, "UsesClause"))
    }

    @Test
    fun systemClasses_posixDefs_has_Unit_and_Uses() {
        // Try with POSIX definitions to see if it parses
        val defs = setOf("POSIX", "LINUX", "LINUX64", "CPUX64")
        val result = parseAstRootFor("src/test/data/System.Classes.pas", defs)
        printDiagnostics("System.Classes.pas (POSIX+LINUX64)", result)

        if (result.error != null) {
            fail("Parse failed: ${result.errorContext}")
        }
        val names = collectNodeSimpleNames(result.node)
        assertTrue("System.Classes (POSIX) should contain UnitDeclarationNode*. Found: $names",
                   hasNodeMatching(names, "UnitDeclaration"))
        assertTrue("System.Classes (POSIX) should contain UsesClauseNode*. Found: $names",
                   hasNodeMatching(names, "UsesClause"))
    }

    /**
     * Diagnostic test: try parsing just the header portion to isolate issues
     */
    @Test
    fun systemClasses_headerOnly_diagnostic() {
        val file = File("src/test/data/System.Classes.pas")
        val fullText = file.readText()

        // Extract just the first 500 lines (should include unit, interface, uses, and some types)
        val lines = fullText.lines()
        val headerText = lines.take(500).joinToString("\n")

        // Write to temp file
        val tempFile = File.createTempFile("System.Classes_header", ".pas")
        tempFile.deleteOnExit()
        tempFile.writeText(headerText)

        println("\n=== HEADER-ONLY TEST (first 500 lines) ===")
        println("Temp file: ${tempFile.absolutePath}")
        println("Text length: ${headerText.length} chars, ${lines.take(500).size} lines")

        val cfg = makeConfig(emptySet())
        try {
            val delphiFile = DelphiFile.from(tempFile, cfg)
            val ast = delphiFile.ast as? org.sonar.plugins.communitydelphi.api.ast.DelphiNode
            printDiagnostics("System.Classes HEADER ONLY", ParseResult(ast, null, null))

            val names = collectNodeSimpleNames(ast)
            println("SUCCESS: Parsed header-only version")
            println("UnitDeclarationNode present: ${names.contains("UnitDeclarationNode")}")
            println("UsesClauseNode present: ${names.contains("UsesClauseNode")}")
        } catch (e: Throwable) {
            println("HEADER-ONLY PARSE FAILED:")
            var cause: Throwable? = e
            while (cause != null) {
                println("  ${cause.javaClass.simpleName}: ${cause.message}")
                cause = cause.cause
            }
        }
        println("=== END HEADER-ONLY TEST ===\n")
    }

    /**
     * Bisection test to find exactly where parsing fails
     */
    @Test
    fun systemClasses_bisection_diagnostic() {
        val file = File("src/test/data/System.Classes.pas")
        val fullText = file.readText()
        val lines = fullText.lines()

        println("\n=== BISECTION TEST ===")
        println("Total lines: ${lines.size}")

        val cfg = makeConfig(emptySet())
        val lineCounts = listOf(50, 100, 200, 250, 300, 350, 400, 450, 475, 480, 485, 490, 500)

        for (n in lineCounts) {
            val testText = lines.take(n).joinToString("\n")
            val tempFile = File.createTempFile("bisect_$n", ".pas")
            tempFile.deleteOnExit()
            tempFile.writeText(testText)

            val result = try {
                val delphiFile = DelphiFile.from(tempFile, cfg)
                val ast = delphiFile.ast
                if (ast != null) "OK (${collectNodeSimpleNames(ast as org.sonar.plugins.communitydelphi.api.ast.DelphiNode).size} node types)" else "NULL AST"
            } catch (e: Throwable) {
                val msg = e.message ?: e.cause?.message ?: e.javaClass.simpleName
                "FAIL: ${msg.take(80)}"
            }
            println("  Lines 1-$n: $result")
        }
        println("=== END BISECTION TEST ===\n")
    }

    /**
     * Test parsing around the failure point (lines 250-260)
     */
    @Test
    fun systemClasses_lineContext_diagnostic() {
        val file = File("src/test/data/System.Classes.pas")
        val lines = file.readLines()

        println("\n=== LINE CONTEXT (around line 253 and 476) ===")
        println("Lines 248-260:")
        for (i in 248..260) {
            if (i < lines.size) {
                println("  $i: ${lines[i - 1]}")
            }
        }
        println("\nLines 470-485:")
        for (i in 470..485) {
            if (i < lines.size) {
                println("  $i: ${lines[i - 1]}")
            }
        }
        println("=== END LINE CONTEXT ===\n")
    }
}
