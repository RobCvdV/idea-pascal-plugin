package nl.akiar.pascal.reference

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet
import nl.akiar.pascal.PascalLexerAdapter
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.*

class PascalFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner {
        return DefaultWordsScanner(
            PascalLexerAdapter(),
            TokenSet.create(PascalTokenTypes.IDENTIFIER),
            TokenSet.create(PascalTokenTypes.LINE_COMMENT, PascalTokenTypes.BLOCK_COMMENT),
            TokenSet.create(PascalTokenTypes.STRING_LITERAL)
        )
    }

    override fun canFindUsagesFor(element: PsiElement): Boolean {
        return element is PascalTypeDefinition ||
            element is PascalRoutine ||
            element is PascalVariableDefinition ||
            element is PascalProperty
    }

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String {
        return when (element) {
            is PascalTypeDefinition -> when (element.typeKind) {
                TypeKind.CLASS -> "class"
                TypeKind.RECORD -> "record"
                TypeKind.INTERFACE -> "interface"
                TypeKind.ENUM -> "enum"
                else -> "type"
            }
            is PascalRoutine -> {
                val node = element.node
                var child = node?.firstChildNode
                while (child != null) {
                    when (child.elementType) {
                        PascalTokenTypes.KW_FUNCTION -> return "function"
                        PascalTokenTypes.KW_PROCEDURE -> return "procedure"
                        PascalTokenTypes.KW_CONSTRUCTOR -> return "constructor"
                        PascalTokenTypes.KW_DESTRUCTOR -> return "destructor"
                    }
                    child = child.treeNext
                }
                "routine"
            }
            is PascalVariableDefinition -> when (element.variableKind) {
                VariableKind.FIELD -> "field"
                VariableKind.CONSTANT -> "constant"
                VariableKind.PARAMETER -> "parameter"
                VariableKind.GLOBAL -> "variable"
                VariableKind.LOCAL -> "variable"
                else -> "variable"
            }
            is PascalProperty -> "property"
            else -> "element"
        }
    }

    override fun getDescriptiveName(element: PsiElement): String {
        if (element is PascalRoutine) {
            val className = element.containingClassName
            val name = element.name ?: return ""
            return if (className != null) "$className.$name" else name
        }
        return (element as? PsiNamedElement)?.name ?: ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
        if (useFullName && element is PascalRoutine) {
            return getDescriptiveName(element)
        }
        return (element as? PsiNamedElement)?.name ?: element.text
    }
}
