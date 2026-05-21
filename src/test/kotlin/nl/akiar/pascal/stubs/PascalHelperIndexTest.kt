package nl.akiar.pascal.stubs

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies that helpers are indexed by their helped type name and that the
 * uses-clause filter on findHelpersFor only returns helpers from imported units.
 */
class PascalHelperIndexTest : BasePlatformTestCase() {

    fun testHelperIsIndexedByHelpedTypeNameInSameFile() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TBar = class
              end;
              TBarHelper = class helper for TBar
              public
                procedure DoSomething;
              end;
            implementation
            procedure TBarHelper.DoSomething; begin end;
            end.
        """.trimIndent())

        val helpers = PascalHelperIndex.findHelpersFor("TBar", file, 0)
        assertEquals(1, helpers.size)
        assertEquals("TBarHelper", helpers[0].name)
    }

    fun testHelperFromAnotherUnitVisibleWhenImported() {
        myFixture.configureByText("Helpers.pas", """
            unit Helpers;
            interface
            type
              TFoo = class
              end;
              TFooHelper = class helper for TFoo
              public
                procedure DoX;
              end;
            implementation
            procedure TFooHelper.DoX; begin end;
            end.
        """.trimIndent())

        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Helpers;
            implementation
            end.
        """.trimIndent())

        val helpers = PascalHelperIndex.findHelpersFor("TFoo", main, main.text.length)
        assertEquals(1, helpers.size)
        assertEquals("TFooHelper", helpers[0].name)
    }

    fun testHelperNotVisibleWhenUnitNotImported() {
        myFixture.configureByText("Helpers.pas", """
            unit Helpers;
            interface
            type
              TFoo = class
              end;
              TFooHelper = class helper for TFoo
              public
                procedure DoX;
              end;
            implementation
            procedure TFooHelper.DoX; begin end;
            end.
        """.trimIndent())

        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            end.
        """.trimIndent())

        val helpers = PascalHelperIndex.findHelpersFor("TFoo", main, main.text.length)
        assertTrue("Should not return helpers from unimported units, got ${helpers.map { it.name }}",
            helpers.isEmpty())
    }
}
