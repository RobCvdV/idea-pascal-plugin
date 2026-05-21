package nl.akiar.pascal.uses

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * Inserts a unit into a Pascal file's uses clause.
 *
 * Section choice: if [referenceOffset] is in the interface section we insert
 * there, otherwise into the implementation section. If the chosen section has
 * no `uses` clause yet we add one immediately after the `interface` or
 * `implementation` keyword.
 *
 * We operate on the document text — the lexer reparses and PSI rebuilds. This
 * keeps the editor simple and immune to the parser's many uses-clause shapes.
 */
object PascalUsesClauseEditor {

    /**
     * Insert a unit into the file's uses clause.
     *
     * Must be called inside a write-action context (the calling IntentionAction
     * sets startInWriteAction = true, so the platform opens one for us).
     */
    fun insertUnit(project: Project, file: PsiFile, unitName: String, referenceOffset: Int) {
        val info = PascalUsesClauseInfo.parse(file)
        val useImplementation = info.isInImplementationSection(referenceOffset) &&
            !info.isInInterfaceSection(referenceOffset)
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val text = document.charsSequence

        // 1. Existing uses clause in target section — append.
        val existingUsesRange = findExistingUsesRange(text, info, useImplementation)
        if (existingUsesRange != null) {
            val units = parseUnits(text.substring(existingUsesRange.first, existingUsesRange.second))
            if (units.any { it.equals(unitName, ignoreCase = true) }) return
            val insertAt = existingUsesRange.second // just before the trailing ';'
            document.insertString(insertAt, ", $unitName")
            PsiDocumentManager.getInstance(project).commitDocument(document)
            return
        }

        // 2. No uses clause yet — insert one right after the section keyword.
        val keyword = if (useImplementation) "implementation" else "interface"
        val sectionStart = if (useImplementation) info.implementationSectionStart else info.interfaceSectionStart
        if (sectionStart < 0) return
        val keywordEnd = sectionStart + keyword.length
        document.insertString(keywordEnd, "\n\nuses\n  $unitName;")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    /**
     * Locate the body of an existing `uses ...;` clause in the target section.
     * Returns the offset range that ends just before the terminating `;`,
     * so callers can append `, NewUnit` cleanly.
     */
    private fun findExistingUsesRange(
        text: CharSequence,
        info: PascalUsesClauseInfo,
        useImplementation: Boolean
    ): Pair<Int, Int>? {
        val sectionStart: Int
        val sectionEnd: Int
        if (useImplementation) {
            if (info.implementationSectionStart < 0) return null
            sectionStart = info.implementationSectionStart
            sectionEnd = text.length
        } else {
            if (info.interfaceSectionStart < 0) return null
            sectionStart = info.interfaceSectionStart
            sectionEnd = if (info.implementationSectionStart >= 0) info.implementationSectionStart else text.length
        }
        val chunk = text.subSequence(sectionStart, sectionEnd).toString()
        val lower = chunk.lowercase()
        // Find "uses" as a standalone word (allow newline / whitespace boundaries).
        var idx = 0
        while (idx < lower.length) {
            val found = lower.indexOf("uses", idx)
            if (found < 0) return null
            val beforeOk = found == 0 || !lower[found - 1].isLetterOrDigit()
            val afterIdx = found + 4
            val afterOk = afterIdx >= lower.length || !lower[afterIdx].isLetterOrDigit()
            if (beforeOk && afterOk) {
                val semi = chunk.indexOf(';', afterIdx)
                if (semi < 0) return null
                return (sectionStart + afterIdx) to (sectionStart + semi)
            }
            idx = afterIdx
        }
        return null
    }

    private fun parseUnits(usesBody: String): List<String> {
        return usesBody.split(',').mapNotNull { raw ->
            var s = raw.trim()
            val inIdx = s.lowercase().indexOf(" in ")
            if (inIdx >= 0) s = s.substring(0, inIdx).trim()
            s.trim('\'', '"').trim().takeIf { it.isNotEmpty() }
        }
    }
}
