package nl.akiar.pascal.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import nl.akiar.pascal.uses.PascalUsesClauseEditor

/**
 * Alt+Enter quick-fix offered on an unresolved symbol when a known unit
 * defines it. Adds the unit to the file's uses clause (interface section if
 * the reference is in interface, implementation section otherwise).
 */
class AddUnitToUsesClauseFix(
    private val unitName: String,
    private val referenceOffset: Int
) : IntentionAction {

    override fun getText(): String = "Add unit '$unitName' to uses clause"
    override fun getFamilyName(): String = "Add unit to uses clause"

    /** IntelliJ opens the write context for us when we return true here. */
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return file != null && unitName.isNotBlank()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null) return
        PascalUsesClauseEditor.insertUnit(project, file, unitName, referenceOffset)
    }
}
