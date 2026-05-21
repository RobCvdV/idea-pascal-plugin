package nl.akiar.pascal.livetemplate

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class PascalLiveTemplateTest : BasePlatformTestCase() {

    @Test
    fun testTemplatesAreRegistered() {
        val templates = TemplateSettings.getInstance().templates
        val pascalTemplates = templates.filter { it.groupName == "Pascal" }
        assertTrue("Should have Pascal templates registered, got: ${pascalTemplates.map { it.key }}",
            pascalTemplates.isNotEmpty())
        val keys = pascalTemplates.map { it.key }.toSet()
        for (expected in listOf("for", "forin", "try", "tryf", "tryx", "case", "class", "proc", "func", "unit", "ifb")) {
            assertTrue("Template '$expected' should be registered (got $keys)", keys.contains(expected))
        }
    }

    @Test
    fun testStatementContext_InsideRoutineBody() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure DoWork;
            begin
              <caret>
            end;
            end.
        """.trimIndent())
        val ctx = PascalLiveTemplateContext.Statement()
        val tac = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertTrue("Statement context should apply inside routine body", ctx.isInContext(tac))
    }

    @Test
    fun testStatementContext_NotInDeclaration() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TFoo = class
                <caret>
              end;
            implementation
            end.
        """.trimIndent())
        val ctx = PascalLiveTemplateContext.Statement()
        val tac = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertFalse("Statement context should NOT apply inside type declaration", ctx.isInContext(tac))
    }

    @Test
    fun testDeclarationContext_InsideTypeBody() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TFoo = class
                <caret>
              end;
            implementation
            end.
        """.trimIndent())
        val ctx = PascalLiveTemplateContext.Declaration()
        val tac = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertTrue("Declaration context should apply inside type block", ctx.isInContext(tac))
    }

    @Test
    fun testTopLevelContext_EmptyFile() {
        myFixture.configureByText("New.pas", "<caret>")
        val ctx = PascalLiveTemplateContext.TopLevel()
        val tac = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertTrue("Top-level context should apply in empty file", ctx.isInContext(tac))
    }
}
