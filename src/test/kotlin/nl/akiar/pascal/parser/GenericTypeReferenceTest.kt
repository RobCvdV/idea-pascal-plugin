package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for generic type handling in TYPE_REFERENCE elements.
 * Verifies that closing '>' tokens are properly included in the TYPE_REFERENCE range.
 */
class GenericTypeReferenceTest : BasePlatformTestCase() {

    @Test
    fun testSimpleGenericType() {
        val code = """
            unit Test;
            interface
            var X: TList<Integer>;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // Find all TYPE_REFERENCE elements
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)

        // Should have 2 TYPE_REFERENCE elements: TList<Integer> and Integer
        assertTrue("Should have at least 2 TYPE_REFERENCE elements", typeRefs.size >= 2)

        // Find TList<Integer> reference
        val tlistRef = typeRefs.firstOrNull {
            it.text.contains("TList") && it.text.contains(">")
        }
        assertNotNull("Should find TList<Integer> TYPE_REFERENCE", tlistRef)

        // Verify the closing '>' is included in the text
        assertTrue("TList TYPE_REFERENCE should include closing '>'",
                  tlistRef?.text?.trim()?.endsWith(">") == true)

        // Verify the text is "TList<Integer>"
        assertEquals("TList<Integer>", tlistRef?.text?.trim())
    }

    @Test
    fun testNestedGenericType() {
        val code = """
            unit Test;
            interface
            var X: TDictionary<string, TList<Integer>>;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // Find all TYPE_REFERENCE elements
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)

        // Should have multiple TYPE_REFERENCE elements
        assertTrue("Should have at least 2 TYPE_REFERENCE elements", typeRefs.size >= 2)

        // Find the outermost TDictionary reference
        val tdictRef = typeRefs.firstOrNull {
            it.text.contains("TDictionary") && it.text.contains(">")
        }
        assertNotNull("Should find TDictionary TYPE_REFERENCE", tdictRef)

        // Verify the outer reference includes both closing '>'
        val tdictText = tdictRef?.text?.trim() ?: ""
        assertTrue("TDictionary TYPE_REFERENCE should include closing '>>'",
                  tdictText.endsWith(">>") || tdictText.endsWith("> >"))
    }

    @Test
    fun testGenericInReturnType() {
        val code = """
            unit Test;
            interface
            function GetList: TList<string>;
            implementation
            function GetList: TList<string>;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // Find all TYPE_REFERENCE elements
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)

        // Find TList<string> references (should be 2 - one in interface, one in implementation)
        val tlistRefs = typeRefs.filter {
            it.text.contains("TList") && it.text.contains(">")
        }
        assertTrue("Should find at least 1 TList<string> TYPE_REFERENCE", tlistRefs.isNotEmpty())

        // Verify at least one includes the closing '>'
        assertTrue("At least one TList TYPE_REFERENCE should include closing '>'",
                  tlistRefs.any { it.text.trim().endsWith(">") })
    }

    @Test
    fun testGenericInParameter() {
        val code = """
            unit Test;
            interface
            procedure Process(AList: TList<Integer>);
            implementation
            procedure Process(AList: TList<Integer>);
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // Find all TYPE_REFERENCE elements
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)

        // Find TList<Integer> references
        val tlistRefs = typeRefs.filter {
            it.text.contains("TList") && it.text.contains(">")
        }
        assertTrue("Should find TList<Integer> TYPE_REFERENCE elements", tlistRefs.isNotEmpty())

        // Verify they include the closing '>'
        tlistRefs.forEach { ref ->
            assertTrue("TList TYPE_REFERENCE '${ref.text}' should include closing '>'",
                      ref.text.trim().endsWith(">"))
        }
    }

    @Test
    fun testComplexNestedGeneric() {
        val code = """
            unit Test;
            interface
            type
              TMyType = TDictionary<string, TList<TObjectList<TMyClass>>>;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // Find all TYPE_REFERENCE elements
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)

        // Should have multiple nested TYPE_REFERENCE elements
        assertTrue("Should have multiple TYPE_REFERENCE elements for nested generics",
                  typeRefs.size >= 3)

        // Find the outermost TDictionary reference
        val tdictRef = typeRefs.firstOrNull {
            it.text.contains("TDictionary") && it.text.contains(">")
        }
        assertNotNull("Should find TDictionary TYPE_REFERENCE", tdictRef)

        // The outermost should include all closing '>'
        val tdictText = tdictRef?.text?.trim() ?: ""
        val closeCount = tdictText.count { it == '>' }
        val openCount = tdictText.count { it == '<' }
        assertEquals("Closing '>' count should match opening '<' count", openCount, closeCount)
    }
}

