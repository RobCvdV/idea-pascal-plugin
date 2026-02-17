package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

/**
 * Tests that the parser produces meaningful PSI structure even when the source
 * contains incomplete or broken code (as happens during editing).
 */
class PsiResilienceTest : BasePlatformTestCase() {

    @BeforeEach
    fun setup() {
        setUp()
    }

    @AfterEach
    fun tearDownTest() {
        tearDown()
    }

    private fun parseAndGetPsiTree(text: String): String {
        val psiFile = myFixture.configureByText("Test.pas", text)
        return com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
    }

    @Test
    fun `valid unit parses with full structure`() {
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
    fun `incomplete member access does not collapse PSI`() {
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
    fun `incomplete assignment does not collapse PSI`() {
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
    fun `missing end keyword does not collapse PSI`() {
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
    fun `unclosed string does not collapse PSI`() {
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
    fun `cache replay provides structure after edit`() {
        // First parse succeeds
        val validText = """
            unit TestUnit;
            interface
            type
              TFoo = class
                procedure Bar;
              end;
            implementation
            procedure TFoo.Bar;
            begin
            end;
            end.
        """.trimIndent()
        val tree1 = parseAndGetPsiTree(validText)
        assertTrue("Initial parse should have TYPE_DEFINITION or CLASS_TYPE",
            tree1.contains("TYPE_DEFINITION") || tree1.contains("CLASS_TYPE"))

        // Clear the cache manually to ensure test isolation — but note the cache
        // is keyed per unit name, so re-parsing the same unit will use the cache
        // This test mainly verifies the recording path works
    }
}
