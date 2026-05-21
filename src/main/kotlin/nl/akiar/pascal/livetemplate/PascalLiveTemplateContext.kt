package nl.akiar.pascal.livetemplate

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import nl.akiar.pascal.PascalLanguage
import nl.akiar.pascal.PascalFile
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition

/**
 * Live-template contexts for Object Pascal.
 *
 * Generic: any caret position inside a .pas/.dpr/.dpk file.
 * Statement: inside the body of a routine (between begin..end).
 * Declaration: outside any routine body, in interface/implementation/type sections.
 * Top-level: empty file or at file scope, where a full `unit` skeleton makes sense.
 */
open class PascalLiveTemplateContext(
    id: String,
    presentableName: String,
    baseContextType: Class<out TemplateContextType>? = Generic::class.java
) : TemplateContextType(presentableName) {

    override fun isInContext(context: TemplateActionContext): Boolean {
        val file = context.file
        if (file !is PascalFile && file.language != PascalLanguage.INSTANCE) return false
        return isApplicable(context)
    }

    protected open fun isApplicable(context: TemplateActionContext): Boolean = true

    class Generic : PascalLiveTemplateContext("PASCAL", "Object Pascal", null) {
        override fun isApplicable(context: TemplateActionContext): Boolean = true
    }

    class Statement : PascalLiveTemplateContext("PASCAL_STATEMENT", "Statement") {
        override fun isApplicable(context: TemplateActionContext): Boolean {
            val file = context.file
            val element = file.findElementAt(context.startOffset) ?: return false
            if (element is PsiComment) return false
            // Walk to the nearest routine. If the caret is inside its body, allow.
            val routine = PsiTreeUtil.getParentOfType(element, PascalRoutine::class.java)
            return routine != null && routine.isImplementation
        }
    }

    class Declaration : PascalLiveTemplateContext("PASCAL_DECLARATION", "Declaration") {
        override fun isApplicable(context: TemplateActionContext): Boolean {
            val file = context.file
            val element = file.findElementAt(context.startOffset) ?: return false
            if (element is PsiComment) return false
            // Outside any routine body, but inside the file.
            val routine = PsiTreeUtil.getParentOfType(element, PascalRoutine::class.java)
            if (routine != null && routine.isImplementation) return false
            return true
        }
    }

    class TopLevel : PascalLiveTemplateContext("PASCAL_TOP_LEVEL", "Top level (unit skeleton)") {
        override fun isApplicable(context: TemplateActionContext): Boolean {
            val file = context.file
            // Empty file or whitespace-only content.
            val text = file.text
            return text.isBlank() || text.trim().isEmpty() ||
                (PsiTreeUtil.findChildOfAnyType(file, PascalRoutine::class.java, PascalTypeDefinition::class.java) == null &&
                    !text.lowercase().contains("unit "))
        }
    }
}
