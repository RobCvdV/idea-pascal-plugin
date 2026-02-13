package nl.akiar.pascal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalVariableDefinition
import org.junit.Test

/**
 * Tests for method/routine navigation to ensure identifiers resolve to the
 * correct target — not to same-named parameters or variables in other scopes.
 */
class PascalMethodNavigationTest : BasePlatformTestCase() {

    private fun resolveAtCaret(code: String, fileName: String = "TestUnit.pas"): List<PsiElement>? {
        val psiFile = myFixture.configureByText(fileName, code)
        val offset = myFixture.caretOffset
        val elementAtCaret = psiFile.findElementAt(offset) ?: return null
        val gotoHandler = PascalGotoDeclarationHandler()
        val targets = gotoHandler.getGotoDeclarationTargets(elementAtCaret, offset, myFixture.editor) ?: return null
        return targets.filterNotNull()
    }

    // ---- Method call should not resolve to a same-named parameter ----

    @Test
    fun testMethodCallDoesNotResolveToParameterWithSameName() {
        // A function named "Count" exists in the interface, plus there's a parameter
        // named "Count" in another function. Clicking on the Count() call should
        // navigate to the function declaration, not the parameter.
        val code = """
            unit TestUnit;
            interface
            function Count: Integer;
            procedure DoWork(Count: Integer);
            implementation
            function Count: Integer;
            begin
              Result := 0;
            end;
            procedure DoWork(Count: Integer);
            begin
            end;
            procedure Caller;
            begin
              Cou<caret>nt();
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find navigation targets for Count()", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue(
            "Count() should resolve to a PascalRoutine, not ${target.javaClass.simpleName}",
            target is PascalRoutine
        )
        assertEquals("Count", (target as PascalRoutine).name)
    }

    @Test
    fun testMethodCallWithoutParensDoesNotResolveToParameter() {
        // Pascal allows calling procedures without parentheses.
        // "DoWork" call without parens should still resolve to the procedure,
        // not to a parameter named DoWork in another function.
        val code = """
            unit TestUnit;
            interface
            procedure DoWork;
            procedure Other(DoWork: Integer);
            implementation
            procedure DoWork;
            begin
            end;
            procedure Other(DoWork: Integer);
            begin
            end;
            procedure Caller;
            begin
              Do<caret>Work;
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find navigation targets for DoWork", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue(
            "DoWork should resolve to a PascalRoutine, not ${target.javaClass.simpleName}",
            target is PascalRoutine
        )
    }

    // ---- Implicit Self.Method calls ----

    @Test
    fun testImplicitSelfMethodCallResolvesToClassMethod() {
        // Inside a method body, calling another method of the same class
        // without Self. prefix should resolve to that class method.
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                procedure Initialize;
                function GetValue: Integer;
              end;
            implementation
            procedure TMyClass.Initialize;
            begin
              Get<caret>Value();
            end;
            function TMyClass.GetValue: Integer;
            begin
              Result := 42;
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find navigation targets for GetValue()", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue(
            "GetValue() should resolve to a PascalRoutine, not ${target.javaClass.simpleName}",
            target is PascalRoutine
        )
        assertEquals("GetValue", (target as PascalRoutine).name)
    }

    // ---- Variable navigation still works ----

    @Test
    fun testLocalVariableNavigationStillWorks() {
        val code = """
            unit TestUnit;
            interface
            implementation
            procedure DoWork;
            var
              MyVar: Integer;
            begin
              My<caret>Var := 10;
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find navigation targets for MyVar", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue(
            "MyVar should resolve to a PascalVariableDefinition, not ${target.javaClass.simpleName}",
            target is PascalVariableDefinition
        )
        assertEquals("MyVar", (target as PascalVariableDefinition).name)
    }

    @Test
    fun testParameterNavigationStillWorks() {
        // A parameter usage in the same function should still resolve to that parameter.
        val code = """
            unit TestUnit;
            interface
            implementation
            procedure DoWork(Value: Integer);
            begin
              WriteLn(Val<caret>ue);
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find navigation targets for Value", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue(
            "Value should resolve to a PascalVariableDefinition, not ${target.javaClass.simpleName}",
            target is PascalVariableDefinition
        )
        assertEquals("Value", (target as PascalVariableDefinition).name)
    }

    // ---- Same-name method and variable: variable wins when no parens ----

    @Test
    fun testLocalVariableShadowsGlobalRoutine() {
        // If a local variable has the same name as a global routine,
        // the local variable should win (no parens = variable reference).
        val code = """
            unit TestUnit;
            interface
            function Count: Integer;
            implementation
            function Count: Integer;
            begin
              Result := 0;
            end;
            procedure Caller;
            var
              Count: Integer;
            begin
              Cou<caret>nt := 5;
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find navigation targets for Count", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue(
            "Count (no parens, local var exists) should resolve to variable, not ${target.javaClass.simpleName}",
            target is PascalVariableDefinition
        )
    }

    // ---- Cross-unit function navigation ----

    @Test
    fun testCrossUnitFunctionNavigation() {
        // Set up a helper unit with a function, then navigate to it from the main unit.
        myFixture.addFileToProject("HelperUnit.pas", """
            unit HelperUnit;
            interface
            function HelperFunc: Integer;
            implementation
            function HelperFunc: Integer;
            begin
              Result := 42;
            end;
            end.
        """.trimIndent())

        val code = """
            unit TestUnit;
            interface
            uses HelperUnit;
            implementation
            procedure Caller;
            begin
              Helper<caret>Func();
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find navigation targets for HelperFunc()", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue(
            "HelperFunc() should resolve to a PascalRoutine, not ${target.javaClass.simpleName}",
            target is PascalRoutine
        )
        assertEquals("HelperFunc", (target as PascalRoutine).name)
    }

    @Test
    fun testCrossUnitFunctionDoesNotResolveToForeignParameter() {
        // A helper unit has a function with a parameter that has the same name
        // as an identifier used in the main unit. The identifier should NOT resolve
        // to the foreign parameter.
        myFixture.addFileToProject("HelperUnit.pas", """
            unit HelperUnit;
            interface
            procedure Setup(Config: Integer);
            implementation
            procedure Setup(Config: Integer);
            begin
            end;
            end.
        """.trimIndent())

        val code = """
            unit TestUnit;
            interface
            uses HelperUnit;
            implementation
            procedure Caller;
            var
              Config: Integer;
            begin
              Con<caret>fig := 10;
            end;
            end.
        """.trimIndent()

        val targets = resolveAtCaret(code)
        assertNotNull("Should find navigation targets for Config", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())

        val target = targets.first()
        assertTrue(
            "Config should resolve to local variable, not ${target.javaClass.simpleName}",
            target is PascalVariableDefinition
        )
        // Should be the local var, not the parameter from HelperUnit
        assertEquals("TestUnit.pas", target.containingFile.name)
    }
}
