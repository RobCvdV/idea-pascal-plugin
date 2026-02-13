package nl.akiar.pascal.navigation

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalRoutine
import org.junit.Test

/**
 * Tests for declaration↔implementation navigation to ensure routines resolve
 * to the correct class counterpart — not to interfaces or other classes.
 */
class PascalDeclImplNavigationTest : BasePlatformTestCase() {

    private fun resolveAtCaret(code: String, fileName: String = "TestUnit.pas"): List<PsiElement>? {
        val psiFile = myFixture.configureByText(fileName, code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset) ?: return null
        val gotoHandler = PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor) ?: return null
        return targets.filterNotNull()
    }

    // ---- Declaration→Implementation: same class ----

    @Test
    fun testDeclarationToImplementationSameClass() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                procedure Do<caret>Work;
              end;
            implementation
            procedure TMyClass.DoWork;
            begin
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find implementation target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine, got ${target.javaClass.simpleName}", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertTrue("Should navigate to implementation", routine.isImplementation())
        assertEquals("DoWork", routine.name)
        assertEquals("TMyClass", routine.containingClassName)
    }

    @Test
    fun testImplementationToDeclarationSameClass() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                procedure DoWork;
              end;
            implementation
            procedure TMyClass.Do<caret>Work;
            begin
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find declaration target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertFalse("Should navigate to declaration, not implementation", routine.isImplementation())
        assertEquals("DoWork", routine.name)
        assertEquals("TMyClass", routine.containingClassName)
    }

    // ---- Multiple classes with same method name ----

    @Test
    fun testDeclarationToImplementationWithMultipleClasses() {
        // TFirst and TSecond both have Execute. Clicking on TSecond.Execute
        // declaration should navigate to TSecond.Execute implementation, not TFirst.Execute.
        val code = """
            unit TestUnit;
            interface
            type
              TFirst = class
              public
                procedure Execute;
              end;
              TSecond = class
              public
                procedure Exe<caret>cute;
              end;
            implementation
            procedure TFirst.Execute;
            begin
            end;
            procedure TSecond.Execute;
            begin
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find implementation target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertTrue("Should navigate to implementation", routine.isImplementation())
        assertEquals("Execute", routine.name)
        assertEquals("TSecond", routine.containingClassName)
    }

    @Test
    fun testImplementationToDeclarationWithMultipleClasses() {
        val code = """
            unit TestUnit;
            interface
            type
              TFirst = class
              public
                procedure Execute;
              end;
              TSecond = class
              public
                procedure Execute;
              end;
            implementation
            procedure TFirst.Execute;
            begin
            end;
            procedure TSecond.Exe<caret>cute;
            begin
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find declaration target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertFalse("Should navigate to declaration, not implementation", routine.isImplementation())
        assertEquals("Execute", routine.name)
        assertEquals("TSecond", routine.containingClassName)
    }

    // ---- Class implementing interface with same method name ----

    @Test
    fun testDeclarationToImplementationNotToInterface() {
        // TWorker implements IDoable. Clicking on TWorker.DoWork declaration
        // should navigate to TWorker.DoWork implementation, NOT IDoable.DoWork.
        val code = """
            unit TestUnit;
            interface
            type
              IDoable = interface
                procedure DoWork;
              end;
              TWorker = class(TInterfacedObject, IDoable)
              public
                procedure Do<caret>Work;
              end;
            implementation
            procedure TWorker.DoWork;
            begin
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find implementation target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertTrue("Should navigate to implementation", routine.isImplementation())
        assertEquals("DoWork", routine.name)
        assertEquals("TWorker", routine.containingClassName)
    }

    @Test
    fun testImplementationToDeclarationNotToInterface() {
        // From TWorker.DoWork implementation, should navigate to TWorker.DoWork declaration,
        // NOT to IDoable.DoWork.
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

        val targets = resolveAtCaret(code)
        assertNotNull("Should find declaration target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertFalse("Should navigate to declaration, not implementation", routine.isImplementation())
        assertEquals("DoWork", routine.name)
        assertEquals("TWorker", routine.containingClassName)
    }

    // ---- Global routines (no class) ----

    @Test
    fun testGlobalRoutineDeclarationToImplementation() {
        val code = """
            unit TestUnit;
            interface
            procedure Global<caret>Proc;
            implementation
            procedure GlobalProc;
            begin
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find implementation target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertTrue("Should navigate to implementation", routine.isImplementation())
        assertEquals("GlobalProc", routine.name)
    }

    @Test
    fun testGlobalRoutineImplementationToDeclaration() {
        val code = """
            unit TestUnit;
            interface
            procedure GlobalProc;
            implementation
            procedure Global<caret>Proc;
            begin
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find declaration target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertFalse("Should navigate to declaration", routine.isImplementation())
        assertEquals("GlobalProc", routine.name)
    }

    // ---- Overloaded methods ----

    @Test
    fun testDeclarationToImplementationOverloaded() {
        // Two overloaded Execute methods in same class.
        // Clicking on the one with (S: String) should navigate to the
        // string-parameter implementation.
        val code = """
            unit TestUnit;
            interface
            type
              TRunner = class
              public
                procedure Execute(I: Integer); overload;
                procedure Exe<caret>cute(S: String); overload;
              end;
            implementation
            procedure TRunner.Execute(I: Integer);
            begin
            end;
            procedure TRunner.Execute(S: String);
            begin
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find implementation target for overloaded method", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertTrue("Should navigate to implementation", routine.isImplementation())
        assertEquals("Execute", routine.name)
        assertEquals("TRunner", routine.containingClassName)
    }

    // ---- Cross-unit: class implementing interface from another unit ----

    @Test
    fun testCrossUnitInterfaceDeclarationToImplementation() {
        // Interface defined in a separate unit. Clicking on the class method declaration
        // should navigate to the class implementation, NOT the interface method.
        myFixture.addFileToProject("Interfaces.pas", """
            unit Interfaces;
            interface
            type
              IDoable = interface
                procedure DoWork;
                function GetName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val code = """
            unit Worker;
            interface
            uses Interfaces;
            type
              TWorker = class(TInterfacedObject, IDoable)
              public
                procedure Do<caret>Work;
                function GetName: String;
              end;
            implementation
            procedure TWorker.DoWork;
            begin
            end;
            function TWorker.GetName: String;
            begin
              Result := 'Worker';
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Worker.pas", code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset)
        assertNotNull("No element at caret", elementAtCaret)
        val gotoHandler = PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret!!, offset, myFixture.editor)
            ?.filterNotNull() ?: emptyList()

        assertTrue("Should find at least one target", targets.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine, got ${target.javaClass.simpleName}", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertTrue("Should navigate to implementation, not interface", routine.isImplementation())
        assertEquals("DoWork", routine.name)
        assertEquals("TWorker", routine.containingClassName)
    }

    // ---- Attributed class (decorators) ----

    @Test
    fun testDeclarationToImplementationWithAttributes() {
        // Class with attribute decorators before the class declaration.
        // The attribute name (BaseUrl) must NOT be confused with the class name.
        val code = """
            unit TestUnit;
            interface
            type
              [BaseUrl('/rides')]
              TRideResource = class
              public
                function A<caret>ll: String;
              end;
            implementation
            function TRideResource.All: String;
            begin
              Result := 'all';
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find implementation target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine, got ${target.javaClass.simpleName}", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertTrue("Should navigate to implementation", routine.isImplementation())
        assertEquals("All", routine.name)
        assertEquals("TRideResource", routine.containingClassName)
    }

    @Test
    fun testImplementationToDeclarationWithAttributes() {
        val code = """
            unit TestUnit;
            interface
            type
              [BaseUrl('/rides')]
              TRideResource = class
              public
                function All: String;
              end;
            implementation
            function TRideResource.A<caret>ll: String;
            begin
              Result := 'all';
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find declaration target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertFalse("Should navigate to declaration, not implementation", routine.isImplementation())
        assertEquals("All", routine.name)
        assertEquals("TRideResource", routine.containingClassName)
    }

    @Test
    fun testAttributedClassWithInterfaceDoesNotJumpToInterface() {
        // Class with attributes implementing an interface with same method name.
        // Should NOT jump to the interface method.
        val code = """
            unit TestUnit;
            interface
            type
              IRideRepository = interface
                function All: String;
              end;
              [BaseUrl('/rides')]
              TRideResource = class(TInterfacedObject, IRideRepository)
              public
                function A<caret>ll: String;
              end;
            implementation
            function TRideResource.All: String;
            begin
              Result := 'all';
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find implementation target", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue("Target should be a PascalRoutine, got ${target.javaClass.simpleName}", target is PascalRoutine)

        val routine = target as PascalRoutine
        assertTrue("Should navigate to implementation", routine.isImplementation())
        assertEquals("All", routine.name)
        assertEquals("TRideResource", routine.containingClassName)
    }
}
