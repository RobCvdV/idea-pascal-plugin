package nl.akiar.pascal.parser

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PascalSourceSanitizerTest {

    @Test
    fun `trailing dot gets identifier appended`() {
        val input = "LResult.\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertTrue(result.contains("LResult.__x__"), "Expected trailing dot fix, got: $result")
    }

    @Test
    fun `trailing dot mid-file preserves surrounding text`() {
        val input = "x := Obj.\nNextLine := 1;"
        val result = PascalSourceSanitizer.sanitize(input)
        assertTrue(result.contains("Obj.__x__"), "Expected trailing dot fix, got: $result")
        assertTrue(result.contains("NextLine"), "Should preserve next line")
    }

    @Test
    fun `complete member access is not modified`() {
        val input = "x := Obj.Prop;\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertFalse(result.contains("__x__"), "Complete member access should not be modified, got: $result")
    }

    @Test
    fun `incomplete assignment gets nil appended`() {
        val input = "x :=\nend;"
        val result = PascalSourceSanitizer.sanitize(input)
        assertTrue(result.contains(":= nil"), "Expected assignment fix, got: $result")
    }

    @Test
    fun `complete assignment is not modified`() {
        val input = "x := 42;\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertFalse(result.contains("nil"), "Complete assignment should not be modified")
    }

    @Test
    fun `unclosed string literal gets quote appended`() {
        val input = "s := 'hello\n"
        val result = PascalSourceSanitizer.sanitize(input)
        // The line should end with two quotes (original + appended)
        val line = result.lines()[0]
        val quoteCount = line.count { it == '\'' }
        assertEquals(0, quoteCount % 2, "Quote count should be even after fix, line: $line")
    }

    @Test
    fun `closed string literal is not modified`() {
        val input = "s := 'hello';\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertEquals(input, result, "Closed string should not be modified")
    }

    @Test
    fun `trailing comma in uses clause gets dummy unit`() {
        val input = "uses SysUtils, Classes,\ntype"
        val result = PascalSourceSanitizer.sanitize(input)
        assertTrue(result.contains("__DummyUnit__"), "Expected dummy unit, got: $result")
    }

    @Test
    fun `unmatched parenthesis gets closed`() {
        val input = "DoSomething(x, y\nend;"
        val result = PascalSourceSanitizer.sanitize(input)
        val firstLine = result.lines()[0]
        val opens = firstLine.count { it == '(' }
        val closes = firstLine.count { it == ')' }
        assertEquals(opens, closes, "Parentheses should be balanced on line: $firstLine")
    }

    @Test
    fun `unclosed begin block gets end appended`() {
        val input = "begin\n  x := 1;\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertTrue(result.contains("end;"), "Expected end; to be appended, got: $result")
    }

    @Test
    fun `balanced begin-end is not modified`() {
        val input = "begin\n  x := 1;\nend;\n"
        val result = PascalSourceSanitizer.sanitize(input)
        // Count ends should be same
        val endCount = Regex("""\bend\b""", RegexOption.IGNORE_CASE).findAll(result).count()
        assertEquals(1, endCount, "Should still have exactly one end")
    }

    @Test
    fun `unchanged text returns same reference`() {
        val input = "unit Test;\ninterface\nimplementation\nend.\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertEquals(input, result, "Valid text should not be modified")
    }

    @Test
    fun `multiple issues fixed together`() {
        val input = "x :=\nObj.\ns := 'hello\nbegin\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertTrue(result.contains(":= nil"), "Should fix assignment")
        assertTrue(result.contains("__x__"), "Should fix trailing dot")
        assertTrue(result.contains("end;"), "Should fix unclosed block")
    }
}
