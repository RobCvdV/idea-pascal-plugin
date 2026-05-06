package nl.akiar.pascal.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameDialog
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition

/**
 * Custom rename handler for Pascal elements in external source paths.
 * Only activates when the element is in a file outside project content roots
 * (which would cause the standard handler to show "not in project" error).
 * For in-project files, defers to the standard handler.
 */
class PascalRenameHandler : PsiElementRenameHandler() {

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val element = findPascalElement(dataContext) ?: return false
        // Only intercept for non-project files; let standard handler work for project files
        val vFile = element.containingFile?.virtualFile ?: return false
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        return !ProjectFileIndex.getInstance(project).isInContent(vFile)
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
