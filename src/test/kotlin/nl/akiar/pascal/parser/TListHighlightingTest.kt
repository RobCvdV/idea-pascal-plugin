package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalSyntaxHighlighter
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.VariableKind
import com.intellij.psi.util.PsiTreeUtil
import org.junit.Test

/**
 * Tests for TList class from System.Classes to verify correct PSI structure
 * and highlighting when parameter/method names use Pascal keywords like "Index".
 */
class TListHighlightingTest : BasePlatformTestCase() {

    private val tListCode = """
        unit TestUnit;
        interface
        type
          TPointerList = array of Pointer;
          TList = class(TObject)
          private
            FList: TPointerList;
            FCount: Integer;
            FCapacity: Integer;
          protected
            function Get(Index: Integer): Pointer;
            procedure Grow; virtual;
            procedure Put(Index: Integer; Item: Pointer);
          public
            function Add(Item: Pointer): Integer;
            procedure Clear; virtual;
            procedure Delete(Index: Integer);
            property Count: Integer read FCount;
          end;
        implementation
        end.
    """.trimIndent()

    @Test
    fun testGetMethodHasCorrectName() {
        val psiFile = myFixture.configureByText("TestUnit.pas", tListCode)

        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val getMethod = routines.find { it.name?.equals("Get", ignoreCase = true) == true }

        assertNotNull("Should find method named 'Get'", getMethod)
        assertEquals("Method name should be 'Get'", "Get", getMethod!!.name)
    }

    @Test
    fun testGetMethodIsMethod() {
        val psiFile = myFixture.configureByText("TestUnit.pas", tListCode)

        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val getMethod = routines.find { it.name?.equals("Get", ignoreCase = true) == true }

        assertNotNull("Should find method 'Get'", getMethod)
        assertTrue("Get should be recognized as a method", getMethod!!.isMethod)
    }

    @Test
    fun testIndexParameterHasCorrectName() {
        val psiFile = myFixture.configureByText("TestUnit.pas", tListCode)

        val variables = PsiTreeUtil.findChildrenOfType(psiFile, PascalVariableDefinition::class.java)
        val indexParams = variables.filter {
            it.name?.equals("Index", ignoreCase = true) == true &&
            it.variableKind == VariableKind.PARAMETER
        }

        assertTrue("Should find at least one 'Index' parameter", indexParams.isNotEmpty())
        assertEquals("Index parameter name should be 'Index'", "Index", indexParams.first().name)
    }

    @Test
    fun testIndexParameterIsParameter() {
        val psiFile = myFixture.configureByText("TestUnit.pas", tListCode)

        val variables = PsiTreeUtil.findChildrenOfType(psiFile, PascalVariableDefinition::class.java)
        val indexParams = variables.filter {
            it.name?.equals("Index", ignoreCase = true) == true
        }

        assertTrue("Should find 'Index' variables", indexParams.isNotEmpty())

        for (indexParam in indexParams) {
            assertEquals(
                "Index should be recognized as a parameter, not ${indexParam.variableKind}",
                VariableKind.PARAMETER,
                indexParam.variableKind
            )
        }
    }

    @Test
    fun testGetMethodHighlightedAsMethod() {
        myFixture.configureByText("TestUnit.pas", tListCode)
        val highlighter = myFixture.doHighlighting()

        // Find the offset of "Get" method declaration
        val getOffset = tListCode.indexOf("function Get(")
        val getNameOffset = getOffset + "function ".length

        val getHighlight = highlighter.find {
            it.startOffset == getNameOffset &&
            it.forcedTextAttributesKey == PascalSyntaxHighlighter.METHOD_DECLARATION
        }

        assertNotNull("Get method should be highlighted as METHOD_DECLARATION", getHighlight)
    }

    @Test
    fun testIndexParameterHighlightedAsParameter() {
        myFixture.configureByText("TestUnit.pas", tListCode)
        val highlighter = myFixture.doHighlighting()

        // Find the offset of first "Index" parameter in Get method
        val getMethodStart = tListCode.indexOf("function Get(")
        val indexOffset = tListCode.indexOf("Index", getMethodStart)

        val indexHighlight = highlighter.find {
            it.startOffset == indexOffset &&
            it.forcedTextAttributesKey == PascalSyntaxHighlighter.VAR_PARAMETER
        }

        assertNotNull("Index parameter should be highlighted as VAR_PARAMETER", indexHighlight)
    }

    @Test
    fun testPutMethodParameters() {
        val psiFile = myFixture.configureByText("TestUnit.pas", tListCode)

        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val putMethod = routines.find { it.name?.equals("Put", ignoreCase = true) == true }

        assertNotNull("Should find method 'Put'", putMethod)

        // Get parameters of Put method
        val putParams = PsiTreeUtil.findChildrenOfType(putMethod!!, PascalVariableDefinition::class.java)
            .filter { it.variableKind == VariableKind.PARAMETER }

        assertEquals("Put should have 2 parameters", 2, putParams.size)

        val paramNames = putParams.mapNotNull { it.name }
        assertTrue("Put should have 'Index' parameter", paramNames.any { it.equals("Index", ignoreCase = true) })
        assertTrue("Put should have 'Item' parameter", paramNames.any { it.equals("Item", ignoreCase = true) })
    }

    @Test
    fun testAllMethodsRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", tListCode)

        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val methodNames = routines.mapNotNull { it.name }.map { it.lowercase() }

        val expectedMethods = listOf("get", "grow", "put", "add", "clear", "delete")
        for (expected in expectedMethods) {
            assertTrue("Should find method '$expected'", methodNames.contains(expected))
        }
    }
}
