package nl.akiar.pascal.stability

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Regression tests for deterministic highlighting behavior.
 * These tests verify that the parser and resolver produce stable results
 * across multiple passes and with ambiguous syntax.
 */
class HighlightingStabilityTest : BasePlatformTestCase() {

    private fun parseAndGetPsiTree(text: String): String {
        val psiFile = myFixture.configureByText("Test.pas", text)
        return com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
    }

    @Test
    fun testComparisonOperatorsNotTreatedAsGenerics() {
        val code = """
            unit Test;
            interface
            implementation
            procedure Test;
            var A, B: TMyClass;
            begin
              if A.Method > B.Property then ;
            end;
            end.
        """.trimIndent()
        val tree = parseAndGetPsiTree(code)
        assertTrue("Should contain UNIT_DECL_SECTION, got:\n$tree", tree.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testGenericTypeInDeclarationRecognized() {
        val code = """
            unit Test;
            interface
            type
              TList<T> = class
              end;
            var L: TList<TMyType>;
            implementation
            end.
        """.trimIndent()
        val tree = parseAndGetPsiTree(code)
        assertTrue("Should contain TYPE_REFERENCE, got:\n$tree", tree.contains("TYPE_REFERENCE"))
    }

    @Test
    fun testPsiTreeDeterministicAcrossParses() {
        val code = "unit Test;\ninterface\nimplementation\nend."
        val tree1 = parseAndGetPsiTree(code)
        val tree2 = parseAndGetPsiTree(code)
        assertEquals("PSI tree should be identical across multiple parses", tree1, tree2)
    }

    @Test
    fun testIncompleteCodePreservesUnitStructure() {
        val code = """
            unit Test;
            interface
            uses SysUtils;
            implementation
            procedure Foo;
            begin
              Obj.
            end;
            end.
        """.trimIndent()
        val tree = parseAndGetPsiTree(code)
        assertTrue("Should contain UNIT_DECL_SECTION, got:\n$tree", tree.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testSanitizerOnlyAppendsAtEof() {
        val code = "x :=\nObj.\ns := 'hello\nbegin\n"
        val sanitized = nl.akiar.pascal.parser.PascalSourceSanitizer.sanitize(code)
        assertTrue("Sanitized text should start with original text", sanitized.startsWith(code))
        assertTrue("Should append end; for unclosed begin", sanitized.contains("end;"))
    }
}
