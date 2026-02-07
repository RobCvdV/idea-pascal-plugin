package nl.akiar.pascal.parser

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalInterfaceGuid

/**
 * Simple debugging test to check GUID parsing
 */
class GuidDebugTest : BasePlatformTestCase() {

    fun testSimpleGuidParsing() {
        val code = """
            unit TestUnit;
            interface
            type
              IMyInterface = interface
                ['{7D9971AC-ED96-4F0A-AC90-9CF0B0F10550}']
                function GetSessionId: Int64;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.pas", code)

        val guids = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalInterfaceGuid::class.java)
        }

        println("Found ${guids.size} GUID elements")
        for (guid in guids) {
            println("  GUID: ${guid.text}")
            println("  Parent: ${guid.parent?.javaClass?.simpleName}")
            println("  Parent text (first 50 chars): ${guid.parent?.text?.take(50)}")
        }

        // Print the PSI tree for debugging
        val debugStr = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, true)
        }
        println("\nPSI Tree:")
        println(debugStr.lines().take(50).joinToString("\n"))

        assertEquals("Should find exactly 1 GUID", 1, guids.size)
    }
}

