package nl.akiar.pascal.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameDialog
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition

/**
 * Rename handler for Pascal named elements (types, routines, variables of any
 * kind — including parameters, locals, and fields — and properties).
 *
 * Activates whenever the caret is on a Pascal-named declaration or its
 * identifier. Registered with order="first" in plugin.xml so it preempts
 * IntelliJ's standard handler, which doesn't recognize all of our PSI shapes
 * (notably parameter / local / field declarations) and would otherwise leave
 * Shift+F6 unavailable in those positions.
 */
class PascalRenameHandler : PsiElementRenameHandler() {

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        return findPascalElement(dataContext) != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        val element = findPascalElement(dataContext)
        if (element != null) {
            RenameDialog(project, element, null, editor).show()
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        val element = elements.firstOrNull { isPascalNamedElement(it) }
        if (element != null) {
            val editor = CommonDataKeys.EDITOR.getData(dataContext)
            RenameDialog(project, element, null, editor).show()
        }
    }

    private fun findPascalElement(dataContext: DataContext): PsiElement? {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return null
        val offset = editor.caretModel.offset
        var element: PsiElement? = file.findElementAt(offset)
        while (element != null && !isPascalNamedElement(element)) {
            element = element.parent
        }
        return element
    }

    private fun isPascalNamedElement(element: PsiElement): Boolean {
        return element is PascalTypeDefinition ||
            element is PascalRoutine ||
            element is PascalVariableDefinition ||
            element is PascalProperty
    }
}
