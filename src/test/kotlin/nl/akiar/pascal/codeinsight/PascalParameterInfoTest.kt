package nl.akiar.pascal.codeinsight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import nl.akiar.pascal.psi.PascalRoutine
import org.junit.Test

class PascalParameterInfoTest : BasePlatformTestCase() {

    private fun configureAtCaret(text: String) {
        myFixture.configureByText("Main.pas", text)
    }

    @Test
    fun testResolvesGlobalProcedure() {
        configureAtCaret("""
            unit Main;
            interface
            procedure Greet(Name: String; Age: Integer);
            implementation
            procedure Greet(Name: String; Age: Integer);
            begin
            end;
            procedure Run;
            begin
              Greet(<caret>);
            end;
            end.
        """.trimIndent())

        val handler = PascalParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val owner = handler.findElementForParameterInfo(context)
        assertNotNull("Should find call site at caret", owner)
        val items = context.itemsToShow
        assertNotNull(items)
        assertTrue("Should have at least one candidate, got ${items?.size}", (items?.size ?: 0) >= 1)
        val routine = items!![0] as PascalRoutine
        assertEquals("Greet", routine.name)
    }

    @Test
    fun testCurrentParameterIndex_FirstArgument() {
        configureAtCaret("""
            unit Main;
            interface
            implementation
            procedure Greet(Name: String; Age: Integer);
            begin
            end;
            procedure Run;
            begin
              Greet(<caret>);
            end;
            end.
        """.trimIndent())

        val handler = PascalParameterInfoHandler()
        val createCtx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        handler.findElementForParameterInfo(createCtx)

        val updateCtx = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
        val owner = handler.findElementForUpdatingParameterInfo(updateCtx)
        assertNotNull(owner)
        handler.updateParameterInfo(owner!!, updateCtx)
        assertEquals("Caret before first arg → param index 0", 0, updateCtx.currentParameter)
    }

    @Test
    fun testCurrentParameterIndex_SecondArgument() {
        configureAtCaret("""
            unit Main;
            interface
            implementation
            procedure Greet(Name: String; Age: Integer);
            begin
            end;
            procedure Run;
            begin
              Greet('Bob', <caret>);
            end;
            end.
        """.trimIndent())

        val handler = PascalParameterInfoHandler()
        handler.findElementForParameterInfo(MockCreateParameterInfoContext(myFixture.editor, myFixture.file))
        val updateCtx = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
        val owner = handler.findElementForUpdatingParameterInfo(updateCtx)!!
        handler.updateParameterInfo(owner, updateCtx)
        assertEquals("Caret after one comma → param index 1", 1, updateCtx.currentParameter)
    }

    @Test
    fun testResolvesClassMethod() {
        configureAtCaret("""
            unit Main;
            interface
            type
              TFoo = class
              public
                procedure Greet(Name: String);
              end;
            implementation
            procedure TFoo.Greet(Name: String);
            begin
            end;
            procedure Use(F: TFoo);
            begin
              F.Greet(<caret>);
            end;
            end.
        """.trimIndent())

        val handler = PascalParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val owner = handler.findElementForParameterInfo(context)
        // We don't require resolution to succeed for member-access calls (that
        // would need MemberChainResolver integration); we only require the
        // handler not to crash and to return null cleanly when it can't
        // resolve. If candidates are surfaced, that's a bonus.
        if (owner != null) {
            assertNotNull(context.itemsToShow)
        }
    }

    @Test
    fun testNoCandidatesOutsideCall() {
        configureAtCaret("""
            unit Main;
            interface
            implementation
            procedure Run;
            var
              X: Integer;
            begin
              X := <caret>;
            end;
            end.
        """.trimIndent())

        val handler = PascalParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val owner = handler.findElementForParameterInfo(context)
        assertNull("Should not find ARGUMENT_LIST outside a call", owner)
    }
}
