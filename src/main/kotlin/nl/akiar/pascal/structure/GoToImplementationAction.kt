package nl.akiar.pascal.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.NavigatablePsiElement
import nl.akiar.pascal.psi.PascalRoutine

class GoToImplementationAction : AnAction(
    "Go to Implementation",
    "Navigate to the implementation of this routine",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val routine = getRoutine(e) ?: return
        val impl = routine.implementation ?: return
        if (impl is NavigatablePsiElement) {
            impl.navigate(true)
        }
    }

    override fun update(e: AnActionEvent) {
        val routine = getRoutine(e)
        e.presentation.isEnabledAndVisible = routine != null &&
            !routine.isImplementation &&
            routine.implementation != null
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT

    private fun getRoutine(e: AnActionEvent): PascalRoutine? {
        val psi = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psi is PascalRoutine) return psi

        // Try from structure view selection
        val navigatable = e.getData(CommonDataKeys.NAVIGATABLE)
        if (navigatable is PascalRoutine) return navigatable

        return null
    }
}
