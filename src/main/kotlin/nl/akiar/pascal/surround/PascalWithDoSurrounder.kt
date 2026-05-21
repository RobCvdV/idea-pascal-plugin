package nl.akiar.pascal.surround

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class PascalWithDoSurrounder : Surrounder {
    override fun getTemplateDescription(): String = "with..do begin..end"
    override fun isApplicable(elements: Array<out PsiElement>): Boolean = true

    override fun surroundElements(
        project: Project,
        editor: Editor,
        elements: Array<out PsiElement>
    ): TextRange {
        val s = PascalSurroundSupport.resolve(editor, elements)
        val inner = PascalSurroundSupport.reindentInner(s.indent, s.body)
        // Placeholder for the qualifier; caret will land on it pre-selected so
        // the user can type the object name immediately.
        val placeholder = "Obj"
        val replacement = "${s.indent}with $placeholder do\n${s.indent}begin\n$inner\n${s.indent}end;"
        val placeholderOffset = s.range.startOffset + "${s.indent}with ".length
        PascalSurroundSupport.replace(editor, s.range, replacement, placeholderOffset)
        editor.selectionModel.setSelection(placeholderOffset, placeholderOffset + placeholder.length)
        editor.caretModel.moveToOffset(placeholderOffset + placeholder.length)
        return TextRange(placeholderOffset, placeholderOffset + placeholder.length)
    }
}
