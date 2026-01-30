package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

class PascalRtlUnitsParserTest : LightPlatformCodeInsightFixture4TestCase() {

    private fun loadFile(path: String): String {
        val f = File(path)
        require(f.exists()) { "Missing test data: $path" }
        return f.readText()
    }

    @Test
    fun testSystemCharacterUnitHeaderAndUses() {
        val content = loadFile("src/test/data/System.Character.pas")
        val psiFile = myFixture.configureByText("System.Character.pas", content)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        assertTrue(debugInfo.contains("UNIT_DECL_SECTION"))
        assertTrue(debugInfo.contains("UNIT_REFERENCE"))
        assertTrue(debugInfo.contains("USES_SECTION"))
    }

    @Test
    fun testSystemClassesUnitHeaderAndUses() {
        val content = loadFile("src/test/data/System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        assertTrue(debugInfo.contains("UNIT_DECL_SECTION"))
        assertTrue(debugInfo.contains("UNIT_REFERENCE"))
        assertTrue(debugInfo.contains("USES_SECTION"))
    }
}
