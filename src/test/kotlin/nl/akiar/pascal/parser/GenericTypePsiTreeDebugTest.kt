package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Debug test to inspect actual PSI tree structure for generic types.
 */
class GenericTypePsiTreeDebugTest : BasePlatformTestCase() {

    @Test
    fun testDebugGenericPsiTree() {
        val code = """
            unit Test;
            interface
            type
              TMyPromise = IPromise<TEntityList<TRide>>;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)

        System.out.println("=== Full PSI Tree ===")
        printPsiTree(psiFile, 0)

        // Find all TYPE_REFERENCE elements
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)

        System.out.println("\n=== TYPE_REFERENCE Elements ===")
        typeRefs.forEachIndexed { index, ref ->
            System.out.println("[$index] text='${ref.text}' startOffset=${ref.textRange.startOffset} endOffset=${ref.textRange.endOffset}")
            System.out.println("     referencedTypeName='${ref.getReferencedTypeName()}'")
        }

        // Check if any GT tokens are outside TYPE_REFERENCE
        System.out.println("\n=== Checking for orphaned GT tokens ===")
        var current = psiFile.firstChild
        var foundOrphanedGT = false
        while (current != null) {
            checkForOrphanedGT(current, 0)
            current = current.nextSibling
        }

        // This test should fail if there are orphaned GT tokens
        assertFalse("Should not have orphaned GT tokens outside TYPE_REFERENCE", foundOrphanedGT)
    }

    private fun checkForOrphanedGT(element: com.intellij.psi.PsiElement, depth: Int) {
        val elementType = element.node?.elementType?.toString() ?: ""
        val text = element.text.take(20)

        // If we find a GT token that's not inside a TYPE_REFERENCE, report it
        if (elementType == "PascalTokenType.GT") {
            var parent = element.parent
            var insideTypeRef = false
            while (parent != null) {
                if (parent is nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) {
                    insideTypeRef = true
                    break
                }
                parent = parent.parent
            }
            if (!insideTypeRef) {
                System.out.println("  ORPHANED GT at offset ${element.textRange.startOffset}: '$text' (depth=$depth)")
            }
        }

        element.children.forEach { child ->
            checkForOrphanedGT(child, depth + 1)
        }
    }

    private fun printPsiTree(element: com.intellij.psi.PsiElement, indent: Int) {
        val prefix = "  ".repeat(indent)
        val elementType = element.node?.elementType?.toString() ?: element.javaClass.simpleName
        val text = element.text.take(60).replace("\n", "\\n")
        System.out.println("$prefix$elementType: '$text'")

        element.children.forEach { child ->
            printPsiTree(child, indent + 1)
        }
    }
}

