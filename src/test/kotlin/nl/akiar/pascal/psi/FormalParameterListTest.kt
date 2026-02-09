package nl.akiar.pascal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for FORMAL_PARAMETER_LIST PSI element.
 */
class FormalParameterListTest : BasePlatformTestCase() {

    @Test
    fun testEmptyParameterList() {
        val code = """
            unit Test;
            interface
            procedure NoParams();
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val paramLists = PsiTreeUtil.findChildrenOfType(psiFile, PascalFormalParameterList::class.java)

        assertTrue("Should find at least one parameter list", paramLists.isNotEmpty())

        val paramList = paramLists.first()
        assertTrue("Empty param list should be empty", paramList.isEmpty)
        assertEquals("Empty param list should have 0 parameters", 0, paramList.parameterCount)
        assertNotNull("Should have open paren", paramList.openParen)
        assertNotNull("Should have close paren", paramList.closeParen)
    }

    @Test
    fun testSingleParameter() {
        val code = """
            unit Test;
            interface
            procedure SingleParam(A: Integer);
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val paramLists = PsiTreeUtil.findChildrenOfType(psiFile, PascalFormalParameterList::class.java)

        assertTrue("Should find at least one parameter list", paramLists.isNotEmpty())

        val paramList = paramLists.first()
        assertFalse("Single param list should not be empty", paramList.isEmpty)
        assertEquals("Should have 1 parameter", 1, paramList.parameterCount)

        val params = paramList.parameters
        assertEquals("Should have 1 parameter in list", 1, params.size)
        assertEquals("Parameter name should be A", "A", params[0].name)
    }

    @Test
    fun testMultipleParameters() {
        val code = """
            unit Test;
            interface
            procedure MultiParams(A: Integer; B: String; C: Boolean);
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val paramLists = PsiTreeUtil.findChildrenOfType(psiFile, PascalFormalParameterList::class.java)

        assertTrue("Should find at least one parameter list", paramLists.isNotEmpty())

        val paramList = paramLists.first()
        assertEquals("Should have 3 parameters", 3, paramList.parameterCount)

        val params = paramList.parameters
        assertEquals("First param should be A", "A", params[0].name)
        assertEquals("Second param should be B", "B", params[1].name)
        assertEquals("Third param should be C", "C", params[2].name)
    }

    @Test
    fun testParametersWithModifiers() {
        val code = """
            unit Test;
            interface
            procedure WithModifiers(var A: Integer; const B: String; out C: Boolean; D: Double);
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val paramLists = PsiTreeUtil.findChildrenOfType(psiFile, PascalFormalParameterList::class.java)

        assertTrue("Should find at least one parameter list", paramLists.isNotEmpty())

        val paramList = paramLists.first()
        assertEquals("Should have 4 parameters", 4, paramList.parameterCount)

        val params = paramList.parameters
        assertEquals("A should be VAR", ParameterModifier.VAR, params.find { it.name == "A" }?.parameterModifier)
        assertEquals("B should be CONST", ParameterModifier.CONST, params.find { it.name == "B" }?.parameterModifier)
        assertEquals("C should be OUT", ParameterModifier.OUT, params.find { it.name == "C" }?.parameterModifier)
        assertEquals("D should be VALUE", ParameterModifier.VALUE, params.find { it.name == "D" }?.parameterModifier)
    }

    @Test
    fun testFunctionParameterList() {
        val code = """
            unit Test;
            interface
            function WithReturn(A: Integer; B: String): Boolean;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val paramLists = PsiTreeUtil.findChildrenOfType(psiFile, PascalFormalParameterList::class.java)

        assertTrue("Should find at least one parameter list", paramLists.isNotEmpty())

        val paramList = paramLists.first()
        assertEquals("Should have 2 parameters", 2, paramList.parameterCount)
        // Verify the parameter list text contains the parentheses
        assertTrue("Text should start with (", paramList.parameterListText.startsWith("("))
    }
}
