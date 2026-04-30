package nl.akiar.pascal.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.*
import javax.swing.Icon

class PascalStructureViewElement(private val element: PsiElement) :
    StructureViewTreeElement,
    SortableTreeElement {

    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) {
        if (element is NavigatablePsiElement) {
            element.navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean =
        element is NavigatablePsiElement && element.canNavigate()

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun getAlphaSortKey(): String =
        presentation.presentableText ?: ""

    override fun getPresentation(): ItemPresentation = PascalItemPresentation(element)

    override fun getChildren(): Array<StructureViewTreeElement> {
        return when (element) {
            is PsiFile -> getFileChildren()
            is PascalTypeDefinition -> getTypeChildren()
            else -> emptyArray()
        }
    }

    private fun getFileChildren(): Array<StructureViewTreeElement> {
        val children = mutableListOf<StructureViewTreeElement>()

        // Types (skip forward declarations)
        PsiTreeUtil.findChildrenOfType(element, PascalTypeDefinition::class.java)
            .filter { !it.isForwardDeclaration }
            .forEach { children.add(PascalStructureViewElement(it)) }

        // Top-level routines (not methods — those are under their type)
        PsiTreeUtil.findChildrenOfType(element, PascalRoutine::class.java)
            .filter { !it.isMethod && !it.isImplementation }
            .forEach { children.add(PascalStructureViewElement(it)) }

        // Top-level variables and constants
        PsiTreeUtil.findChildrenOfType(element, PascalVariableDefinition::class.java)
            .filter {
                val kind = it.variableKind
                kind == VariableKind.GLOBAL || kind == VariableKind.CONSTANT || kind == VariableKind.THREADVAR
            }
            .forEach { children.add(PascalStructureViewElement(it)) }

        return children.toTypedArray()
    }

    private fun getTypeChildren(): Array<StructureViewTreeElement> {
        val typeDef = element as PascalTypeDefinition
        val children = mutableListOf<StructureViewTreeElement>()

        if (typeDef.typeKind == TypeKind.ENUM) {
            // Enum elements
            collectEnumElements(typeDef, children)
        } else {
            // Fields
            typeDef.fields.forEach { children.add(PascalStructureViewElement(it)) }
            // Methods (interface declarations only — skip implementation bodies)
            typeDef.methods
                .filter { !it.isImplementation }
                .forEach { children.add(PascalStructureViewElement(it)) }
            // Properties
            typeDef.properties.forEach { children.add(PascalStructureViewElement(it)) }
        }

        return children.toTypedArray()
    }

    private fun collectEnumElements(typeDef: PascalTypeDefinition, children: MutableList<StructureViewTreeElement>) {
        // ENUM_ELEMENT nodes are nested inside ENUM_TYPE, not direct children
        PsiTreeUtil.processElements(typeDef) { el ->
            if (el.node?.elementType == PascalElementTypes.ENUM_ELEMENT) {
                children.add(PascalStructureViewElement(el))
            }
            true
        }
    }
}

private class PascalItemPresentation(private val element: PsiElement) : ItemPresentation {

    override fun getPresentableText(): String? {
        return when (element) {
            is PsiFile -> {
                val name = element.name
                val dot = name.lastIndexOf('.')
                if (dot > 0) name.substring(0, dot) else name
            }
            is PascalTypeDefinition -> {
                val params = element.typeParameters
                if (params.isNotEmpty()) {
                    "${element.name}<${params.joinToString(", ")}>"
                } else {
                    element.name
                }
            }
            is PascalRoutine -> element.name
            is PascalProperty -> element.name
            is PascalVariableDefinition -> element.name
            else -> {
                // Enum elements — find the IDENTIFIER child
                val node = element.node
                if (node?.elementType == PascalElementTypes.ENUM_ELEMENT) {
                    var child = node.firstChildNode
                    while (child != null) {
                        if (child.elementType == PascalTokenTypes.IDENTIFIER) {
                            return child.text
                        }
                        child = child.treeNext
                    }
                }
                element.text?.take(50)
            }
        }
    }

    override fun getLocationString(): String? {
        return when (element) {
            is PascalRoutine -> buildRoutineSignature(element)
            is PascalProperty -> element.typeName?.let { ": $it" }
            is PascalVariableDefinition -> {
                if (element.variableKind == VariableKind.CONSTANT) {
                    // For constants, try to show the value
                    element.typeName?.let { ": $it" }
                } else {
                    element.typeName?.let { ": $it" }
                }
            }
            is PascalTypeDefinition -> {
                val superClass = element.superClassName
                when {
                    superClass != null -> "($superClass)"
                    else -> null
                }
            }
            else -> null
        }
    }

    override fun getIcon(unused: Boolean): Icon? {
        return when (element) {
            is PascalTypeDefinition -> getTypeIcon(element)
            is PascalRoutine -> getRoutineIcon(element)
            is PascalProperty -> AllIcons.Nodes.Property
            is PascalVariableDefinition -> getVariableIcon(element)
            else -> {
                if (element.node?.elementType == PascalElementTypes.ENUM_ELEMENT) {
                    AllIcons.Nodes.Enum
                } else {
                    null
                }
            }
        }
    }

    private fun getTypeIcon(typeDef: PascalTypeDefinition): Icon {
        return when (typeDef.typeKind) {
            TypeKind.CLASS -> AllIcons.Nodes.Class
            TypeKind.RECORD -> AllIcons.Nodes.Record
            TypeKind.INTERFACE -> AllIcons.Nodes.Interface
            TypeKind.ENUM -> AllIcons.Nodes.Enum
            TypeKind.ALIAS -> AllIcons.Nodes.Type
            TypeKind.PROCEDURAL -> AllIcons.Nodes.Lambda
            TypeKind.UNKNOWN -> AllIcons.Nodes.Type
        }
    }

    private fun getRoutineIcon(routine: PascalRoutine): Icon {
        val node = routine.node ?: return AllIcons.Nodes.Function
        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                PascalTokenTypes.KW_CONSTRUCTOR -> return AllIcons.Nodes.ClassInitializer
                PascalTokenTypes.KW_DESTRUCTOR -> return AllIcons.Nodes.ClassInitializer
                PascalTokenTypes.KW_FUNCTION -> return AllIcons.Nodes.Function
                PascalTokenTypes.KW_PROCEDURE -> return AllIcons.Nodes.Method
            }
            child = child.treeNext
        }
        return AllIcons.Nodes.Function
    }

    private fun getVariableIcon(varDef: PascalVariableDefinition): Icon {
        return when (varDef.variableKind) {
            VariableKind.CONSTANT -> AllIcons.Nodes.Constant
            VariableKind.FIELD -> AllIcons.Nodes.Field
            VariableKind.GLOBAL, VariableKind.THREADVAR -> AllIcons.Nodes.Variable
            else -> AllIcons.Nodes.Variable
        }
    }

    private fun buildRoutineSignature(routine: PascalRoutine): String? {
        val parts = mutableListOf<String>()

        // Build parameter list
        val sig = PsiTreeUtil.findChildOfType(routine, PascalRoutineSignature::class.java)
        if (sig != null) {
            val params = sig.parameters
            if (params.isNotEmpty()) {
                val paramStr = params.joinToString(", ") { p ->
                    val modifier = p.parameterModifier
                    val prefix = when (modifier) {
                        ParameterModifier.VALUE, null -> ""
                        else -> modifier.name.lowercase() + " "
                    }
                    "$prefix${p.name ?: "?"}: ${p.typeName ?: "?"}"
                }
                parts.add("($paramStr)")
            }
        }

        // Return type
        val returnType = routine.returnTypeName
        if (returnType != null) {
            parts.add(": $returnType")
        }

        return parts.joinToString("").ifEmpty { null }
    }
}
