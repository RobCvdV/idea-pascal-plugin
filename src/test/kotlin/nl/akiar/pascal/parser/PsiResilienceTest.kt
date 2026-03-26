package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests that the parser produces meaningful PSI structure even when the source
 * contains incomplete or broken code (as happens during editing).
 */
class PsiResilienceTest : BasePlatformTestCase() {

    private fun parseAndGetPsiTree(text: String): String {
        val psiFile = myFixture.configureByText("Test.pas", text)
        return com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
    }

    @Test
    fun testValidUnitParsesWithFullStructure() {
        val text = """
            unit TestUnit;
            interface
            uses SysUtils;
            type
              TMyClass = class
              end;
            implementation
            procedure TMyClass.DoSomething;
            begin
            end;
            end.
        """.trimIndent()

        val tree = parseAndGetPsiTree(text)
        assertTrue("Should contain UNIT_DECL_SECTION, got:\n$tree", tree.contains("UNIT_DECL_SECTION"))
        assertTrue("Should contain INTERFACE_SECTION, got:\n$tree", tree.contains("INTERFACE_SECTION"))
        assertTrue("Should contain IMPLEMENTATION_SECTION, got:\n$tree", tree.contains("IMPLEMENTATION_SECTION"))
        assertTrue("Should contain USES_SECTION, got:\n$tree", tree.contains("USES_SECTION"))
        assertTrue("Should contain TYPE_DEFINITION or CLASS_TYPE, got:\n$tree",
            tree.contains("TYPE_DEFINITION") || tree.contains("CLASS_TYPE"))
    }

    @Test
    fun testIncompleteMemberAccessDoesNotCollapsePsi() {
        // User just typed "LResult." — trailing dot
        val text = """
            unit TestUnit;
            interface
            uses SysUtils;
            type
              TMyClass = class
                procedure DoSomething;
              end;
            implementation
            procedure TMyClass.DoSomething;
            var
              LResult: TMyClass;
            begin
              LResult.
            end;
            end.
        """.trimIndent()

        val tree = parseAndGetPsiTree(text)
        // Even if parse fails, we should have at least unit structure from fallback/cache/sanitization
        assertTrue("Should contain UNIT_DECL_SECTION, got:\n$tree", tree.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testIncompleteAssignmentDoesNotCollapsePsi() {
        val text = """
            unit TestUnit;
            interface
            implementation
            procedure DoIt;
            var x: Integer;
            begin
              x :=
            end;
            end.
        """.trimIndent()

        val tree = parseAndGetPsiTree(text)
        assertTrue("Should contain UNIT_DECL_SECTION, got:\n$tree", tree.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testMissingEndKeywordDoesNotCollapsePsi() {
        val text = """
            unit TestUnit;
            interface
            type
              TMyClass = class
                procedure DoSomething;
              end;
            implementation
            procedure TMyClass.DoSomething;
            begin
              if True then
              begin
                x := 1;
            end.
        """.trimIndent()

        val tree = parseAndGetPsiTree(text)
        assertTrue("Should contain UNIT_DECL_SECTION, got:\n$tree", tree.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testUnclosedStringDoesNotCollapsePsi() {
        val text = """
            unit TestUnit;
            interface
            implementation
            procedure DoIt;
            begin
              ShowMessage('hello
            end;
            end.
        """.trimIndent()

        val tree = parseAndGetPsiTree(text)
        assertTrue("Should contain UNIT_DECL_SECTION, got:\n$tree", tree.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testPsiTreeIsDeterministicAcrossParses() {
        val text = "unit Test;\ninterface\nimplementation\nend."
        val psi1 = myFixture.configureByText("TestA.pas", text)
        val tree1 = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psi1, false)
        }
        val psi2 = myFixture.configureByText("TestB.pas", text)
        val tree2 = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psi2, false)
        }
        assertEquals("PSI tree should be identical across parses of the same text", tree1, tree2)
    }
}
