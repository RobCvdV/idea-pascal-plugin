package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PascalRtlUnitsParserTest : LightPlatformCodeInsightFixture4TestCase() {

    @Test
    fun testSystemCharacterUnitHeaderAndUses() {
        // Load the real RTL unit from test data
        val psiFile = myFixture.configureByFile("src/test/data/System.Character.pas")
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        assertTrue(debugInfo.contains("UNIT_DECL_SECTION"), "System.Character: Should contain UNIT_DECL_SECTION")
        assertTrue(debugInfo.contains("UNIT_REFERENCE"), "System.Character: Should contain UNIT_REFERENCE (unit name and uses items)")
        assertTrue(debugInfo.contains("USES_SECTION"), "System.Character: Should contain USES_SECTION")
    }

    @Test
    fun testSystemClassesUnitHeaderAndUses() {
        // Load the real RTL unit from test data
        val psiFile = myFixture.configureByFile("src/test/data/System.Classes.pas")
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        // Even if sonar-delphi fails, our tests highlight what should be present; use these to drive patches
        assertTrue(debugInfo.contains("UNIT_DECL_SECTION"), "System.Classes: Should contain UNIT_DECL_SECTION")
        assertTrue(debugInfo.contains("UNIT_REFERENCE"), "System.Classes: Should contain UNIT_REFERENCE (unit name and uses items)")
        assertTrue(debugInfo.contains("USES_SECTION"), "System.Classes: Should contain USES_SECTION")
    }
}
