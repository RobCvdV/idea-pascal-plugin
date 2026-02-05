package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalSyntaxHighlighter
import org.junit.Test

class ConstantHighlightingTest : BasePlatformTestCase() {

    @Test
    fun testConstantDeclarationHighlighting() {
        val text = """
            unit test;
            interface
            const
              C1 = 10;
              C2 = 'test';
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("test.pas", text)
        val highlights = myFixture.doHighlighting()

        val c1Offset = text.indexOf("C1")
        val c2Offset = text.indexOf("C2")

        val c1Decl = highlights.find { it.startOffset == c1Offset && it.forcedTextAttributesKey == PascalSyntaxHighlighter.VAR_CONSTANT }
        val c2Decl = highlights.find { it.startOffset == c2Offset && it.forcedTextAttributesKey == PascalSyntaxHighlighter.VAR_CONSTANT }

        assertNotNull("C1 declaration should be highlighted as VAR_CONSTANT", c1Decl)
        assertNotNull("C2 declaration should be highlighted as VAR_CONSTANT", c2Decl)
    }

    @Test
    fun testConstantUsageHighlighting() {
        val text = """
            unit test;
            interface
            const
              C1 = 10;
              C2 = 'test';
            implementation
            procedure P;
            var X: Integer;
            begin
              X := C1;
              WriteLn(C2);
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("test.pas", text)
        val highlights = myFixture.doHighlighting()

        val c1UseOffset = text.indexOf("C1", text.indexOf("begin"))
        val c2UseOffset = text.indexOf("C2", text.indexOf("begin"))

        val c1Use = highlights.find { it.startOffset == c1UseOffset && it.forcedTextAttributesKey == PascalSyntaxHighlighter.VAR_CONSTANT }
        val c2Use = highlights.find { it.startOffset == c2UseOffset && it.forcedTextAttributesKey == PascalSyntaxHighlighter.VAR_CONSTANT }

        assertNotNull("C1 usage should be highlighted as VAR_CONSTANT", c1Use)
        assertNotNull("C2 usage should be highlighted as VAR_CONSTANT", c2Use)
    }
}
