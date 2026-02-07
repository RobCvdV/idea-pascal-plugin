package nl.akiar.pascal.parser

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalElementTypes

/**
 * Tests for correct PSI tree structure of unit references.
 * Ensures no nested UNIT_REFERENCE elements.
 */
class UnitReferencePsiStructureTest : BasePlatformTestCase() {

    fun testSingleUnitReference_NoNesting() {
        val text = """
            unit Main;
            interface
            uses SysUtils;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Main.pas", text)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // Should have UNIT_REFERENCE
        assertTrue("Should contain UNIT_REFERENCE", debugInfo.contains("UNIT_REFERENCE"))

        // Should NOT have nested UNIT_REFERENCE inside UNIT_REFERENCE
        assertFalse("Should not have nested UNIT_REFERENCE",
            debugInfo.contains("UNIT_REFERENCE") &&
            debugInfo.split("UNIT_REFERENCE").size > 3) // More than 2 occurrences suggests nesting

        // Verify structure: UNIT_REFERENCE should directly contain IDENTIFIER
        val unitRefs = PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.PascalPsiElement::class.java)
            .filter { it.node.elementType == PascalElementTypes.UNIT_REFERENCE }

        for (ref in unitRefs) {
            val children = ref.children.filter { it.node.elementType == PascalElementTypes.UNIT_REFERENCE }
            assertEquals("UNIT_REFERENCE should not contain nested UNIT_REFERENCE", 0, children.size)
        }
    }

    fun testDottedUnitReference_NoNesting() {
        val text = """
            unit Main;
            interface
            uses System.SysUtils, System.Classes;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Main.pas", text)

        // Verify structure: each UNIT_REFERENCE should contain IDENTIFIER and DOT tokens, but no nested UNIT_REFERENCE
        val unitRefs = PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.PascalPsiElement::class.java)
            .filter { it.node.elementType == PascalElementTypes.UNIT_REFERENCE }

        assertTrue("Should have at least 2 unit references", unitRefs.size >= 2)

        for (ref in unitRefs) {
            val nestedRefs = ref.children.filter { it.node?.elementType == PascalElementTypes.UNIT_REFERENCE }
            assertEquals("UNIT_REFERENCE '${ref.text}' should not contain nested UNIT_REFERENCE", 0, nestedRefs.size)

            // Should have IDENTIFIER children
            val identifiers = ref.node.getChildren(null).filter {
                it.elementType == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER
            }
            assertTrue("UNIT_REFERENCE '${ref.text}' should contain IDENTIFIER tokens", identifiers.isNotEmpty())
        }
    }

    fun testUnitDeclaration_NoNesting() {
        val text = """
            unit System.Character;
            interface
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("System.Character.pas", text)

        // Verify unit name in declaration has no nested UNIT_REFERENCE
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // Count UNIT_REFERENCE occurrences - for a single unit declaration, should be exactly 1
        val refCount = debugInfo.split("UNIT_REFERENCE").size - 1
        assertEquals("Unit declaration should have exactly 1 UNIT_REFERENCE (not nested)", 1, refCount)
    }
}

