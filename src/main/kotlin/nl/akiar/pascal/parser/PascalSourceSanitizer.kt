package nl.akiar.pascal.parser

/**
 * Attempts to fix common incomplete-code patterns so sonar-delphi can parse them.
 * All fixes **append** text rather than replace, preserving existing character offsets.
 * The synthetic tokens appended at the end don't exist in the original builder token stream
 * and will be ignored during PSI mapping.
 */
object PascalSourceSanitizer {

    /**
     * Apply all sanitization rules and return the (possibly modified) text.
     * Returns the original text unchanged if no fixes are applicable.
     */
    fun sanitize(text: String): String {
        var result = text

        result = fixTrailingDot(result)
        result = fixIncompleteAssignment(result)
        result = fixUnclosedStringLiteral(result)
        result = fixTrailingCommaInUses(result)
        result = fixUnmatchedParentheses(result)
        result = fixUnclosedBlocks(result)

        return result
    }

    /**
     * Trailing dot: `obj.` at line end or before whitespace/newline → append `__x__` after dot.
     * Handles member access that the user is still typing.
     * Excludes `end.` which is a valid Pascal file terminator.
     */
    private fun fixTrailingDot(text: String): String {
        // Match an identifier followed by a dot, then optional whitespace and end-of-line or end-of-string
        // Excludes 'end.' which is a valid program/unit terminator
        val regex = Regex("""(?<!\bend)\.(\s*?)(\r?\n|$)""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        return regex.replace(text) { match ->
            val dot = "."
            val ws = match.groupValues[1]
            val lineEnd = match.groupValues[2]
            "${dot}__x__${ws}${lineEnd}"
        }
    }

    /**
     * Incomplete assignment: `x :=` at line end → append `nil`.
     */
    private fun fixIncompleteAssignment(text: String): String {
        val regex = Regex(""":=\s*(\r?\n|$)""", RegexOption.MULTILINE)
        return regex.replace(text) { match ->
            val lineEnd = match.groupValues[1]
            ":= nil${lineEnd}"
        }
    }

    /**
     * Unclosed string literal: a line with an odd number of single quotes → append `'`.
     */
    private fun fixUnclosedStringLiteral(text: String): String {
        val lines = text.split("\n")
        val sb = StringBuilder()
        for ((i, line) in lines.withIndex()) {
            val quoteCount = line.count { it == '\'' }
            if (quoteCount % 2 != 0) {
                sb.append(line).append("'")
            } else {
                sb.append(line)
            }
            if (i < lines.size - 1) sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Trailing comma in uses clause: `uses SysUtils,` → append `__DummyUnit__`.
     */
    private fun fixTrailingCommaInUses(text: String): String {
        // Find uses clause followed by a trailing comma before semicolon or section keyword
        val regex = Regex(
            """(?i)(uses\s+(?:[\w.]+\s*,\s*)*[\w.]+\s*),(\s*(?:;|\btype\b|\bvar\b|\bconst\b|\binterface\b|\bimplementation\b|\bbegin\b|$))""",
            RegexOption.MULTILINE
        )
        return regex.replace(text) { match ->
            "${match.groupValues[1]}, __DummyUnit__${match.groupValues[2]}"
        }
    }

    /**
     * Unmatched parentheses: if a line has more `(` than `)`, append `)` at end.
     */
    private fun fixUnmatchedParentheses(text: String): String {
        val lines = text.split("\n")
        val sb = StringBuilder()
        for ((i, line) in lines.withIndex()) {
            val opens = line.count { it == '(' }
            val closes = line.count { it == ')' }
            val missing = opens - closes
            if (missing > 0) {
                sb.append(line).append(")".repeat(missing))
            } else {
                sb.append(line)
            }
            if (i < lines.size - 1) sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Unclosed blocks: if there are more `begin`/`try`/`case` than `end`, append `end;` at file end.
     */
    private fun fixUnclosedBlocks(text: String): String {
        val beginCount = Regex("""\b(?:begin|try|case)\b""", RegexOption.IGNORE_CASE).findAll(text).count()
        val endCount = Regex("""\bend\b""", RegexOption.IGNORE_CASE).findAll(text).count()
        val missing = beginCount - endCount
        if (missing > 0) {
            val sb = StringBuilder(text)
            repeat(missing) {
                sb.append("\nend;")
            }
            return sb.toString()
        }
        return text
    }
}
