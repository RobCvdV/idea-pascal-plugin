package nl.akiar.pascal.surround

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class PascalSurroundTest : BasePlatformTestCase() {

    private fun selectRange(start: Int, end: Int) {
        myFixture.editor.selectionModel.setSelection(start, end)
    }

    private fun surround(surrounder: com.intellij.lang.surroundWith.Surrounder) {
        val descriptor = PascalSurroundDescriptor()
        val elements = descriptor.getElementsToSurround(
            myFixture.file,
            myFixture.editor.selectionModel.selectionStart,
            myFixture.editor.selectionModel.selectionEnd
        )
        WriteCommandAction.runWriteCommandAction(project) {
            surrounder.surroundElements(project, myFixture.editor, elements)
        }
    }

    @Test
    fun testSurroundBeginEnd() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Run;
            begin
              X := 1;
              Y := 2;
            end;
            end.
        """.trimIndent())
        val text = myFixture.file.text
        val start = text.indexOf("X := 1;")
        val end = text.indexOf("Y := 2;") + "Y := 2;".length
        selectRange(start, end)
        surround(PascalBeginEndSurrounder())
        val newText = myFixture.file.text
        // Selection extends to whole lines and uses the original 2-space indent
        // as the base, so the wrapper sits at 2 spaces and the inner content at 4.
        assertTrue("Expected nested begin/end at 2-space indent, got:\n$newText",
            newText.contains("  begin\n    X := 1;\n    Y := 2;\n  end;"))
    }

    @Test
    fun testSurroundTryFinally() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Run;
            begin
              Stuff;
            end;
            end.
        """.trimIndent())
        val text = myFixture.file.text
        val start = text.indexOf("Stuff;")
        val end = start + "Stuff;".length
        selectRange(start, end)
        surround(PascalTryFinallySurrounder())
        val newText = myFixture.file.text
        assertTrue("Expected try/finally wrapper:\n$newText",
            newText.contains("try\n") &&
            newText.contains("finally\n") &&
            newText.contains("Stuff;") &&
            newText.contains("end;"))
    }

    @Test
    fun testSurroundTryExcept() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Run;
            begin
              DoIt;
            end;
            end.
        """.trimIndent())
        val text = myFixture.file.text
        val start = text.indexOf("DoIt;")
        val end = start + "DoIt;".length
        selectRange(start, end)
        surround(PascalTryExceptSurrounder())
        val newText = myFixture.file.text
        assertTrue("Expected try/except wrapper:\n$newText",
            newText.contains("try\n") && newText.contains("except\n") && newText.contains("DoIt;"))
    }

    @Test
    fun testSurroundWithDo() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Run;
            begin
              A := 1;
            end;
            end.
        """.trimIndent())
        val text = myFixture.file.text
        val start = text.indexOf("A := 1;")
        val end = start + "A := 1;".length
        selectRange(start, end)
        surround(PascalWithDoSurrounder())
        val newText = myFixture.file.text
        assertTrue("Expected with..do wrapper:\n$newText",
            newText.contains("with Obj do") && newText.contains("begin\n") && newText.contains("A := 1;"))
    }
}
