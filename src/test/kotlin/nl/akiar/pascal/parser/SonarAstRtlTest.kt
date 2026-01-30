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

    private fun parseAstRootFor(path: String, defs: Set<String> = emptySet()): org.sonar.plugins.communitydelphi.api.ast.DelphiNode? {
        val file = File(path)
        require(file.exists()) { "Test data not found: $path" }
        val cfg = makeConfig(defs)
        val delphiFile = DelphiFile.from(file, cfg)
        val astRoot = delphiFile.ast
        return astRoot as? org.sonar.plugins.communitydelphi.api.ast.DelphiNode
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

    @Test
    fun systemCharacter_has_Unit_and_Uses() {
        val names = collectNodeSimpleNames(parseAstRootFor("src/test/data/System.Character.pas"))
        assertTrue("System.Character should contain UnitDeclarationNode", names.contains("UnitDeclarationNode"))
        assertTrue("System.Character should contain UsesClauseNode", names.contains("UsesClauseNode"))
    }

    @Test
    fun systemClasses_emptyDefs_missing_Unit_or_Uses_evidence() {
        val names = collectNodeSimpleNames(parseAstRootFor("src/test/data/System.Classes.pas"))
        assertTrue("System.Classes (empty defs) should contain UnitDeclarationNode", names.contains("UnitDeclarationNode"))
        assertTrue("System.Classes (empty defs) should contain UsesClauseNode", names.contains("UsesClauseNode"))
    }

    @Test
    fun systemClasses_windowsDefs_has_Unit_and_Uses() {
        val defs = setOf("MSWINDOWS")
        val names = collectNodeSimpleNames(parseAstRootFor("src/test/data/System.Classes.pas", defs))
        assertTrue("System.Classes (MSWINDOWS) should contain UnitDeclarationNode", names.contains("UnitDeclarationNode"))
        assertTrue("System.Classes (MSWINDOWS) should contain UsesClauseNode", names.contains("UsesClauseNode"))
    }
}
