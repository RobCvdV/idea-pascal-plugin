package nl.akiar.pascal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import org.junit.Test

/**
 * Tests for PsiUtil.isLikelyInsideAttributeBrackets().
 * Validates the fix that distinguishes attribute brackets [Attr] from array literal
 * brackets [expr1, expr2] inside PRIMARY_EXPRESSION nodes.
 */
class PascalAttributeBracketsTest : BasePlatformTestCase() {

    private fun isInsideAttributeBrackets(code: String): Boolean {
        val file = myFixture.configureByText("TestUnit.pas", code)
        val offset = myFixture.caretOffset
        val element = file.findElementAt(offset) ?: return false
        return PsiUtil.isLikelyInsideAttributeBrackets(element)
    }

    @Test
    fun testAttributeBracketsDetected() {
        val code = """
            unit TestUnit;
            interface
            type
              [Authen<caret>ticate]
              TFoo = class
              end;
            implementation
            end.
        """.trimIndent()

        assertTrue("Element inside [Authenticate] should be detected as attribute", isInsideAttributeBrackets(code))
    }

    @Test
    fun testAfterBeginNotDetectedAsAttribute() {
        // Inside a method body after 'begin', a bracket is code-level not attribute-level.
        // The backward walk encounters 'begin' before any '[', so returns false.
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure DoWork;
              end;
            implementation
            procedure TFoo.DoWork;
            begin
              Do<caret>Something;
            end;
            end.
        """.trimIndent()

        assertFalse("Element after begin should not be detected as attribute", isInsideAttributeBrackets(code))
    }

    @Test
    fun testAfterClosingBracketNotDetected() {
        // After a closing bracket, should not be detected as inside attribute
        val code = """
            unit TestUnit;
            interface
            type
              [Authenticate]
              TFoo<caret> = class
              end;
            implementation
            end.
        """.trimIndent()

        assertFalse("Element after closing bracket should not be detected", isInsideAttributeBrackets(code))
    }

    @Test
    fun testBracketsAfterSemicolon() {
        // After a semicolon, a new bracket starts an attribute
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure DoWork;
              end;
              [Base<caret>Url('/api')]
              TBar = class
              end;
            implementation
            procedure TFoo.DoWork; begin end;
            end.
        """.trimIndent()

        assertTrue("Element in attribute after semicolon should be detected", isInsideAttributeBrackets(code))
    }

    @Test
    fun testOutsideBracketsNotDetected() {
        // An identifier NOT inside any brackets
        val code = """
            unit TestUnit;
            interface
            type
              TFoo<caret> = class
              end;
            implementation
            end.
        """.trimIndent()

        assertFalse("Element outside brackets should not be detected", isInsideAttributeBrackets(code))
    }
}
