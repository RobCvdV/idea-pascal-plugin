package nl.akiar.pascal.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalRoutine
import com.intellij.psi.util.PsiTreeUtil
import org.junit.Test

class PascalRoutineNavigationTest : BasePlatformTestCase() {

    @Test
    fun testNavigationFromImplementationToDeclaration() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                procedure MyMethod;
              end;
            implementation
            procedure TMyClass.MyMe<caret>thod;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("TestUnit.pas", code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset)
        
        // Try goto declaration handler instead of reference
        val gotoHandler = nl.akiar.pascal.navigation.PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor)
        
        assertNotNull("Goto declaration should find targets", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())
        
        val target = targets.first()
        assertTrue("Should resolve to a PascalRoutine", target is PascalRoutine)
        
        val routine = target as PascalRoutine
        assertFalse("Should resolve to the declaration, not the implementation", routine.isImplementation())
        assertEquals("MyMethod", routine.name)
        
        // Check if it's in the interface section
        val section = nl.akiar.pascal.psi.PsiUtil.getSection(routine)
        assertEquals("interface", section)
    }

    @Test
    fun testNavigationForGlobalRoutine() {
        val code = """
            unit TestUnit;
            interface
            procedure GlobalProc;
            implementation
            procedure GlobalPr<caret>oc;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("TestUnit.pas", code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset)
        
        // Try goto declaration handler instead of reference
        val gotoHandler = nl.akiar.pascal.navigation.PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor)
        
        assertNotNull("Goto declaration should find targets for global routine", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())
        
        val target = targets.first()
        assertTrue("Should resolve to a PascalRoutine", target is PascalRoutine)
        
        val routine = target as PascalRoutine
        assertFalse("Should resolve to the declaration, not the implementation", routine.isImplementation())
        assertEquals("GlobalProc", routine.name)
    }
}