package nl.akiar.pascal.surround

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class PascalBeginEndSurrounder : Surrounder {
    override fun getTemplateDescription(): String = "begin..end"
    override fun isApplicable(elements: Array<out PsiElement>): Boolean = true

    override fun surroundElements(
        project: Project,
        editor: Editor,
        elements: Array<out PsiElement>
    ): TextRange {
        val s = PascalSurroundSupport.resolve(editor, elements)
        val inner = PascalSurroundSupport.reindentInner(s.indent, s.body)
        val replacement = "${s.indent}begin\n$inner\n${s.indent}end;"
        val caretAt = s.range.startOffset + "${s.indent}begin\n".length + inner.length
        return PascalSurroundSupport.replace(editor, s.range, replacement, caretAt)
    }
}
