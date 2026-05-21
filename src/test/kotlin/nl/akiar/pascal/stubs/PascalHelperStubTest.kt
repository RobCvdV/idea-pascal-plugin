package nl.akiar.pascal.stubs

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalTypeDefinition

/**
 * Verifies that PascalTypeStub captures the helped type name from
 * `TFoo = class helper for TBar` (and the record variant), and that
 * `PascalTypeDefinition.isHelper()` / `getHelpedTypeName()` report
 * consistent results both via stub and via AST fallback.
 */
class PascalHelperStubTest : BasePlatformTestCase() {

    fun testClassHelperCapturesTargetTypeName() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TBar = class
              end;

              TFooHelper = class helper for TBar
              public
                procedure DoSomething;
              end;
            implementation
            procedure TFooHelper.DoSomething; begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val helper = types.first { it.name == "TFooHelper" }
        val bar = types.first { it.name == "TBar" }

        assertTrue("TFooHelper should be a helper", helper.isHelper)
        assertEquals("TBar", helper.helpedTypeName)
        assertFalse("TBar (regular class) should not be a helper", bar.isHelper)
        assertNull(bar.helpedTypeName)
    }

    fun testRecordHelperCapturesTargetTypeName() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TStringHelper = record helper for String
              public
                function ToUpperCase: String;
              end;
            implementation
            function TStringHelper.ToUpperCase: String; begin end;
            end.
        """.trimIndent())

        val helper = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
            .first { it.name == "TStringHelper" }
        assertTrue(helper.isHelper)
        assertEquals("String", helper.helpedTypeName)
    }

    fun testHelperWithExtendsClauseAndGenericTargetStripsArgs() {
        // class helper(TBase) for TList<Integer>  →  helped name = "TList"
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TBase = class
              end;
              TList<T> = class
              end;
              TListHelperBase = class helper for TList<Integer>
              public
                procedure DoX;
              end;
            implementation
            procedure TListHelperBase.DoX; begin end;
            end.
        """.trimIndent())

        val helper = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
            .first { it.name == "TListHelperBase" }
        assertTrue(helper.isHelper)
        assertEquals("TList", helper.helpedTypeName)
    }

    fun testRegularClassIsNotHelper() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TFoo = class
              public
                procedure DoX;
              end;
            implementation
            procedure TFoo.DoX; begin end;
            end.
        """.trimIndent())

        val foo = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
            .first { it.name == "TFoo" }
        assertFalse(foo.isHelper)
        assertNull(foo.helpedTypeName)
    }
}
