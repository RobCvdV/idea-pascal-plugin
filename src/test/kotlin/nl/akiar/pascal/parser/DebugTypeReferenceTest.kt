package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Debug test to inspect PSI tree structure and see what elements are created.
 */
class DebugTypeReferenceTest : BasePlatformTestCase() {

    @Test
    fun testDebugMultipleTypes() {
        val code = """
            unit Test;
            interface
            var 
              X: Integer;
              Y: string;
              Z: Boolean;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)

        // Find all TYPE_REFERENCE elements
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)

        System.out.println("Found ${typeRefs.size} TYPE_REFERENCE elements")
        typeRefs.forEachIndexed { index, ref ->
            System.out.println("  [$index] ${ref.getReferencedTypeName()} - kind: ${ref.getKind()}")
        }

        val typeNames = typeRefs.map { it.getReferencedTypeName() }.toSet()
        System.out.println("Unique type names: $typeNames")

        // Print PSI tree summary - count element types
        val elementCounts = mutableMapOf<String, Int>()
        countElements(psiFile, elementCounts)
        System.out.println("\nElement type counts:")
        elementCounts.entries.sortedByDescending { it.value }.forEach { (type, count) ->
            System.out.println("  $type: $count")
        }

        // Check if test passes
        // Note: lowercase "string" is a keyword type and doesn't get TYPE_REFERENCE from parser
        assertTrue("Should have at least 2 TYPE_REFERENCE elements", typeRefs.size >= 2)
    }

    private fun countElements(element: com.intellij.psi.PsiElement, counts: MutableMap<String, Int>) {
        val elementType = element.node?.elementType?.toString() ?: element.javaClass.simpleName
        counts[elementType] = counts.getOrDefault(elementType, 0) + 1

        element.children.forEach { child ->
            countElements(child, counts)
        }
    }
}

