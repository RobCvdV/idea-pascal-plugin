package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalSyntaxHighlighter
import org.junit.Test

class RoutineCallHighlightingTest : BasePlatformTestCase() {

    @Test
    fun testFunctionAndProcedureCalls() {
        val text = """
            unit test;
            interface
            procedure P1; forward;
            function F1: Integer; forward;
            implementation
            procedure P1; begin end;
            function F1: Integer; begin Result := 0; end;
            procedure Caller;
            begin
              P1();
              P1;
              F1();
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("test.pas", text)
        val highlights = myFixture.doHighlighting()

        val p1CallWithParensOffset = text.indexOf("P1();")
        val p1CallNoParensOffset = text.indexOf("P1;", p1CallWithParensOffset + 1)
        val f1CallOffset = text.indexOf("F1();")

        val p1With = highlights.find { it.startOffset == p1CallWithParensOffset && it.forcedTextAttributesKey == PascalSyntaxHighlighter.ROUTINE_CALL }
        val p1No = highlights.find { it.startOffset == p1CallNoParensOffset && it.forcedTextAttributesKey == PascalSyntaxHighlighter.ROUTINE_CALL }
        val f1 = highlights.find { it.startOffset == f1CallOffset && it.forcedTextAttributesKey == PascalSyntaxHighlighter.ROUTINE_CALL }

        assertNotNull("Procedure call with parens should be ROUTINE_CALL", p1With)
        assertNotNull("Procedure call without parens should be ROUTINE_CALL", p1No)
        assertNotNull("Function call should be ROUTINE_CALL", f1)
    }

    @Test
    fun testMethodCall() {
        val text = """
            unit test;
            interface
            type
              TClass = class
                procedure M1; 
              end;
            implementation
            procedure CallIt;
            var o: TClass;
            begin
              o.M1();
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("test.pas", text)
        val highlights = myFixture.doHighlighting()

        val m1CallOffset = text.indexOf("M1();")
        val m1 = highlights.find { it.startOffset == m1CallOffset && it.forcedTextAttributesKey == PascalSyntaxHighlighter.METHOD_CALL }
        assertNotNull("Method call should be METHOD_CALL", m1)
    }
}
