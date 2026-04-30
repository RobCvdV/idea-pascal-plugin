package nl.akiar.pascal.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import nl.akiar.pascal.PascalLanguage
import nl.akiar.pascal.PascalTokenTypes

object PascalPsiFactory {

    fun createIdentifier(project: Project, name: String): PsiElement {
        val dummyFile = PsiFileFactory.getInstance(project)
            .createFileFromText(
                "dummy.pas",
                PascalLanguage.INSTANCE,
                "unit dummy;\ninterface\ntype\n  $name = class end;\nimplementation\nend."
            )
        // Walk the PSI tree to find the IDENTIFIER token matching our name
        return findIdentifier(dummyFile, name)
            ?: throw IllegalStateException("Failed to create identifier PSI element for '$name'")
    }

    private fun findIdentifier(element: PsiElement, name: String): PsiElement? {
        if (element.node?.elementType == PascalTokenTypes.IDENTIFIER && element.text == name) {
            return element
        }
        var child = element.firstChild
        while (child != null) {
            val found = findIdentifier(child, name)
            if (found != null) return found
            child = child.nextSibling
        }
        return null
    }

    fun replaceIdentifier(oldIdentifier: PsiElement, newName: String): PsiElement {
        val newIdentifier = createIdentifier(oldIdentifier.project, newName)
        return oldIdentifier.replace(newIdentifier)
    }
}
