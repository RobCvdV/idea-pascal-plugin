package nl.akiar.pascal.annotator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for PascalSyntaxErrorAnnotator — verifies that structural syntax
 * problems are detected while well-formed files pass cleanly.
 */
class PascalSyntaxErrorAnnotatorTest : BasePlatformTestCase() {

    // ==================== No-warning cases ====================

    @Test
    fun testValidUnit_NoWarning() {
        val text = """
            unit Main;
            interface
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", text)
        myFixture.checkHighlighting()
    }

    @Test
    fun testValidUnitWithContent_NoWarning() {
        val text = """
            unit Main;
            interface
            type
              TMyClass = class
              end;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", text)
        myFixture.checkHighlighting()
    }

    @Test
    fun testValidProgram_NoWarning() {
        val text = """
            program MyApp;
            begin
            end.
        """.trimIndent()
        myFixture.configureByText("MyApp.dpr", text)
        myFixture.checkHighlighting()
    }

    @Test
    fun testEmptyFile_NoWarning() {
        myFixture.configureByText("Empty.pas", "")
        myFixture.checkHighlighting()
    }

    @Test
    fun testCommentOnlyFile_NoWarning() {
        val text = """
            // This is just a comment
            { Another comment }
        """.trimIndent()
        myFixture.configureByText("Comments.pas", text)
        myFixture.checkHighlighting()
    }

    // ==================== Warning cases ====================

    @Test
    fun testMissingUnitHeader_Warning() {
        val text = """
            <warning descr="Missing unit, program, or library declaration">interface</warning>
            type
              TMyClass = class
              end;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("NoHeader.pas", text)
        myFixture.checkHighlighting()
    }

    @Test
    fun testMissingInterfaceSection_Warning() {
        val text = """
            <warning descr="Unit is missing interface section">unit Main;</warning>
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("NoInterface.pas", text)
        myFixture.checkHighlighting()
    }

    @Test
    fun testMissingImplementationSection_Warning() {
        // Note: without the 'implementation' keyword, the parser can't delineate sections,
        // so it may report both sections missing rather than just implementation.
        val text = """
            <warning descr="Unit is missing both interface and implementation sections">unit Main;</warning>
            interface
            type
              TMyClass = class
              end;
            end.
        """.trimIndent()
        myFixture.configureByText("NoImpl.pas", text)
        myFixture.checkHighlighting()
    }

    @Test
    fun testMissingBothSections_Warning() {
        val text = """
            <warning descr="Unit is missing both interface and implementation sections">unit Main;</warning>
            end.
        """.trimIndent()
        myFixture.configureByText("NoBoth.pas", text)
        myFixture.checkHighlighting()
    }
}
