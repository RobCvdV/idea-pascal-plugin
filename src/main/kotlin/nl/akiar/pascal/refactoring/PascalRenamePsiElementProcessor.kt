package nl.akiar.pascal.refactoring

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.PascalProperty

class PascalRenamePsiElementProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        return element is PascalRoutine ||
            element is PascalTypeDefinition ||
            element is PascalVariableDefinition ||
            element is PascalProperty
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        if (element is PascalRoutine) {
            // Pair declaration ↔ implementation: renaming one renames the other
            if (element.isImplementation) {
                element.declaration?.let { allRenames[it] = newName }
            } else {
                element.implementation?.let { allRenames[it] = newName }
            }
        }
    }
}
