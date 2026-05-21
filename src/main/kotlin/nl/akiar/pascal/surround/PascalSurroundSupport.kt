package nl.akiar.pascal.surround

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

/**
 * Shared helpers for the four Pascal surrounders.
 *
 * Snap the editor selection to whole lines, detect the leading indent of the
 * first selected line, and produce a wrapped block where the wrapper keywords
 * sit at that indent and the original content is re-indented by two more
 * spaces. The lexer reparses on apply; we do no PSI generation.
 */
internal object PascalSurroundSupport {

    data class Surroundable(
        val range: TextRange,
        val indent: String,
        val body: String,
    )

    /** Selection extended to cover whole lines, plus the leading indent of the first line. */
    fun resolve(editor: Editor, elements: Array<out PsiElement>): Surroundable {
        val doc = editor.document
        val sel = editor.selectionModel
        val rawStart: Int
        val rawEnd: Int
        if (sel.hasSelection()) {
            rawStart = sel.selectionStart
            rawEnd = sel.selectionEnd
        } else if (elements.isNotEmpty()) {
            rawStart = elements.first().textRange.startOffset
            rawEnd = elements.last().textRange.endOffset
        } else {
            val off = editor.caretModel.offset
            rawStart = off; rawEnd = off
        }

        val startLine = doc.getLineNumber(rawStart)
        val endLine = doc.getLineNumber((rawEnd - 1).coerceAtLeast(rawStart))
        val lineStart = doc.getLineStartOffset(startLine)
        val lineEnd = doc.getLineEndOffset(endLine)
        val range = TextRange(lineStart, lineEnd)
        val raw = doc.getText(range)

        val firstLine = raw.lineSequence().firstOrNull().orEmpty()
        val indent = firstLine.takeWhile { it == ' ' || it == '\t' }

        // Strip the common leading indent off every line so we can re-apply it cleanly.
        val stripped = raw.lineSequence().joinToString("\n") { line ->
            if (line.startsWith(indent)) line.substring(indent.length) else line
        }
        return Surroundable(range, indent, stripped)
    }

    /** Indent every non-blank line of [body] by `indent + "  "`. */
    fun reindentInner(indent: String, body: String): String {
        val innerIndent = "$indent  "
        return body.lineSequence().joinToString("\n") { line ->
            if (line.isEmpty()) line else "$innerIndent$line"
        }
    }

    fun replace(editor: Editor, range: TextRange, replacement: String, caretAt: Int): TextRange {
        val document: Document = editor.document
        document.replaceString(range.startOffset, range.endOffset, replacement)
        PsiDocumentManager.getInstance(editor.project!!).commitDocument(document)
        editor.caretModel.moveToOffset(caretAt)
        editor.selectionModel.removeSelection()
        return TextRange(caretAt, caretAt)
    }
}
