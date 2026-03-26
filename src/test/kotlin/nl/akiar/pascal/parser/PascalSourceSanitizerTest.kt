package nl.akiar.pascal.parser

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PascalSourceSanitizerTest {

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
    fun `multiple missing ends are all appended`() {
        val input = "begin\n  try\n    x := 1;\n"
        val result = PascalSourceSanitizer.sanitize(input)
        val endCount = Regex("""\bend\b""", RegexOption.IGNORE_CASE).findAll(result).count()
        assertEquals(2, endCount, "Should have two end; appended for begin+try")
    }

    @Test
    fun `case block gets end appended`() {
        val input = "case X of\n  1: DoSomething;\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertTrue(result.endsWith("end;"), "Expected end; appended for case block")
    }

    @Test
    fun `trailing dot is not modified`() {
        // Mid-file modifications removed — trailing dot should pass through unchanged
        val input = "LResult.\n"
        val result = PascalSourceSanitizer.sanitize(input)
        assertEquals(input, result, "Trailing dot should not be modified (no mid-file sanitization)")
    }

    @Test
    fun `incomplete assignment is not modified`() {
        // Mid-file modifications removed
        val input = "x :=\nend;"
        val result = PascalSourceSanitizer.sanitize(input)
        assertFalse(result.contains("nil"), "Incomplete assignment should not be modified")
    }
}
