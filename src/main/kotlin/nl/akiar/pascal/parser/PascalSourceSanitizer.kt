package nl.akiar.pascal.parser

/**
 * Attempts to fix common incomplete-code patterns so sonar-delphi can parse them.
 * Only EOF-appending fixes are safe here — mid-file text changes break the offset
 * mapping between AST and PsiBuilder (the sanitized AST has different offsets than
 * the original token stream in the builder).
 */
object PascalSourceSanitizer {

    /**
     * Apply all sanitization rules and return the (possibly modified) text.
     * Returns the original text unchanged if no fixes are applicable.
     */
    fun sanitize(text: String): String {
        return fixUnclosedBlocks(text)
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
