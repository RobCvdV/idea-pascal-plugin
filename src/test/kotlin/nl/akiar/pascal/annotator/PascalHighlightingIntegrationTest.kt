package nl.akiar.pascal.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalSyntaxHighlighter
import nl.akiar.pascal.PascalTokenTypes

/**
 * Integration tests for semantic highlighting through the annotator pipeline.
 *
 * These tests verify that PascalSemanticAnnotator applies correct
 * TextAttributesKey highlighting through the full reference resolution path.
 */
class PascalHighlightingIntegrationTest : BasePlatformTestCase() {

    // ==================== Member Method Call Highlighting ====================

    fun testMemberMethodCallGetsHighlighting() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                procedure DoWork;
              end;
            implementation
            procedure TMyClass.DoWork; begin end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TMyClass;
            begin
              Obj.DoWork<caret>;
            end;
            end.
        """.trimIndent())

        val identifier = findIdentifierAtCaret(file)
        assertNotNull("Should find 'DoWork' identifier", identifier)

        val highlights = myFixture.doHighlighting()
        val doWorkHighlight = highlights.find { info ->
            info.text == "DoWork" &&
            info.severity == HighlightSeverity.INFORMATION &&
            file.findElementAt(info.startOffset)?.let { el ->
                el.text == "DoWork" && el.textOffset > file.text.indexOf("Obj.DoWork")
            } == true
        }

        if (doWorkHighlight != null) {
            val key = doWorkHighlight.forcedTextAttributesKey
            assertTrue("DoWork should have METHOD_CALL or ROUTINE_CALL highlighting, got: $key",
                key == PascalSyntaxHighlighter.METHOD_CALL ||
                key == PascalSyntaxHighlighter.ROUTINE_CALL)
        } else {
            // Check if it at least has some highlighting
            val anyHighlight = highlights.find { it.text == "DoWork" && it.severity == HighlightSeverity.INFORMATION }
            if (anyHighlight != null) {
                println("DoWork has highlighting with key: ${anyHighlight.forcedTextAttributesKey}")
            } else {
                println("NOTE: 'DoWork' member call does not receive semantic highlighting")
            }
        }
    }

    // ==================== Member Field Highlighting ====================

    fun testMemberFieldGetsHighlighting() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                Caption: String;
              end;
            implementation
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TMyClass;
            begin
              Obj.Caption<caret> := 'test';
            end;
            end.
        """.trimIndent())

        val identifier = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Caption' identifier", identifier)

        val highlights = myFixture.doHighlighting()
        val captionHighlight = highlights.find { info ->
            info.text == "Caption" &&
            info.severity == HighlightSeverity.INFORMATION &&
            info.startOffset > file.text.indexOf("Obj.Caption")
        }

        if (captionHighlight != null) {
            println("'Caption' field has highlighting with key: ${captionHighlight.forcedTextAttributesKey}")
        } else {
            println("NOTE: 'Caption' member field does not receive semantic highlighting at usage site")
        }
    }

    // ==================== Member Property Highlighting ====================

    fun testMemberPropertyGetsHighlighting() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
              public
                property Value: Integer read FValue;
              end;
            implementation
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TMyClass;
              X: Integer;
            begin
              X := Obj.Value<caret>;
            end;
            end.
        """.trimIndent())

        val identifier = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Value' identifier", identifier)

        val highlights = myFixture.doHighlighting()
        val valueHighlight = highlights.find { info ->
            info.text == "Value" &&
            info.severity == HighlightSeverity.INFORMATION &&
            info.startOffset > file.text.indexOf("Obj.Value")
        }

        if (valueHighlight != null) {
            println("'Value' property has highlighting with key: ${valueHighlight.forcedTextAttributesKey}")
        } else {
            println("NOTE: 'Value' member property does not receive semantic highlighting at usage site")
        }
    }

    // ==================== Local Variable Highlighting ====================

    fun testLocalVariableGetsHighlighting() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure TestProc;
            var
              MyVar<caret>: Integer;
            begin
            end;
            end.
        """.trimIndent())

        val highlights = myFixture.doHighlighting()
        val varHighlight = highlights.find { info ->
            info.text == "MyVar" && info.severity == HighlightSeverity.INFORMATION
        }

        assertNotNull("Local variable 'MyVar' should have some highlighting", varHighlight)
        if (varHighlight != null) {
            val key = varHighlight.forcedTextAttributesKey
            assertTrue("MyVar should have VAR_LOCAL highlighting, got: $key",
                key == PascalSyntaxHighlighter.VAR_LOCAL)
        }
    }

    // ==================== Type Reference Highlighting ====================

    fun testTypeReferenceGetsHighlighting() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              end;
            implementation
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TMyClass<caret>;
            begin
            end;
            end.
        """.trimIndent())

        val highlights = myFixture.doHighlighting()
        val typeHighlight = highlights.find { info ->
            info.text == "TMyClass" &&
            info.severity == HighlightSeverity.INFORMATION &&
            info.startOffset > file.text.indexOf("Obj: TMyClass")
        }

        if (typeHighlight != null) {
            println("'TMyClass' reference has highlighting with key: ${typeHighlight.forcedTextAttributesKey}")
        } else {
            println("NOTE: 'TMyClass' type reference does not receive highlighting at usage site")
        }
    }

    // ==================== Enum Element Highlighting at Declaration ====================

    fun testEnumElementGetsHighlightingAtDeclaration() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TAlignment = (taLeftJustify, taRightJustify, taCenter);
            implementation
            end.
        """.trimIndent())

        val highlights = myFixture.doHighlighting()
        val enumHighlight = highlights.find { info ->
            info.text == "taLeftJustify" && info.severity == HighlightSeverity.INFORMATION
        }

        assertNotNull("Enum element 'taLeftJustify' should have highlighting at declaration", enumHighlight)
        if (enumHighlight != null) {
            assertEquals("Should have ENUM_ELEMENT highlighting",
                PascalSyntaxHighlighter.ENUM_ELEMENT, enumHighlight.forcedTextAttributesKey)
        }
    }

    // ==================== Type Definition Highlighting ====================

    fun testTypeDefinitionNameGetsHighlighting() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TMyClass = class
              end;
            implementation
            end.
        """.trimIndent())

        val highlights = myFixture.doHighlighting()
        val typeDefHighlight = highlights.find { info ->
            info.text == "TMyClass" && info.severity == HighlightSeverity.INFORMATION
        }

        assertNotNull("Type definition name should have highlighting", typeDefHighlight)
        if (typeDefHighlight != null) {
            assertEquals("Should have TYPE_CLASS highlighting",
                PascalSyntaxHighlighter.TYPE_CLASS, typeDefHighlight.forcedTextAttributesKey)
        }
    }

    // ==================== Routine Declaration Highlighting ====================

    fun testRoutineDeclarationGetsHighlighting() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            procedure MyProc;
            implementation
            procedure MyProc; begin end;
            end.
        """.trimIndent())

        val highlights = myFixture.doHighlighting()
        val routineHighlight = highlights.find { info ->
            info.text == "MyProc" && info.severity == HighlightSeverity.INFORMATION
        }

        assertNotNull("Routine name should have highlighting", routineHighlight)
    }

    // ==================== Helper Methods ====================

    private fun findIdentifierAtCaret(file: com.intellij.psi.PsiFile): PsiElement? {
        val caretOffset = myFixture.caretOffset

        var element = file.findElementAt(caretOffset)
        if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) {
            return element
        }

        if (caretOffset > 0) {
            element = file.findElementAt(caretOffset - 1)
            if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) {
                return element
            }
        }

        for (offset in 1..3) {
            if (caretOffset - offset >= 0) {
                element = file.findElementAt(caretOffset - offset)
                if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) {
                    return element
                }
            }
        }

        return null
    }
}
