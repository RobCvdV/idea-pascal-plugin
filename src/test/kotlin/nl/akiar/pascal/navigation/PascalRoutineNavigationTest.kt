package nl.akiar.pascal.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalVariableDefinition
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

    @Test
    fun testNavigationFromPropertyGetterToMethod() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                function GetValue: Integer;
              public
                property Value: Integer read Get<caret>Value;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("TestUnit.pas", code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset)

        val gotoHandler = PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor)

        assertNotNull("Goto declaration should find targets for property getter", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Should resolve to a PascalRoutine", target is PascalRoutine)
        assertEquals("GetValue", (target as PascalRoutine).name)
    }

    @Test
    fun testNavigationFromPropertySetterToMethod() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                procedure SetValue(AValue: Integer);
              public
                property Value: Integer write Set<caret>Value;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("TestUnit.pas", code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset)

        val gotoHandler = PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor)

        assertNotNull("Goto declaration should find targets for property setter", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Should resolve to a PascalRoutine", target is PascalRoutine)
        assertEquals("SetValue", (target as PascalRoutine).name)
    }

    @Test
    fun testNavigationWithMultipleClassesSameMethodName() {
        val code = """
            unit TestUnit;
            interface
            type
              TBase = class
              public
                procedure Execute;
              end;
              TChild = class(TBase)
              public
                procedure Execute;
              end;
            implementation
            procedure TBase.Execute;
            begin
            end;
            procedure TChild.Exe<caret>cute;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("TestUnit.pas", code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset)

        val gotoHandler = PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor)

        assertNotNull("Goto declaration should find targets", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Should resolve to a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertFalse("Should resolve to the declaration, not the implementation", routine.isImplementation())
        assertEquals("Execute", routine.name)
        assertEquals("TChild", routine.containingClassName)
    }

    @Test
    fun testNavigationPrefersClassOverInterface() {
        val code = """
            unit TestUnit;
            interface
            type
              IDoable = interface
                procedure DoWork;
              end;
              TWorker = class(TInterfacedObject, IDoable)
              public
                procedure DoWork;
              end;
            implementation
            procedure TWorker.Do<caret>Work;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("TestUnit.pas", code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset)

        val gotoHandler = PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor)

        assertNotNull("Goto declaration should find targets", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Should resolve to a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertFalse("Should resolve to the declaration, not the implementation", routine.isImplementation())
        assertEquals("DoWork", routine.name)
        assertEquals("TWorker", routine.containingClassName)
    }

    @Test
    fun testNavigationFromPropertyReadToField() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
              public
                property Value: Integer read F<caret>Value;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("TestUnit.pas", code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset)

        val gotoHandler = PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor)

        assertNotNull("Goto declaration should find targets for property field", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Should resolve to a PascalVariableDefinition", target is PascalVariableDefinition)
        assertEquals("FValue", (target as PascalVariableDefinition).name)
    }
}