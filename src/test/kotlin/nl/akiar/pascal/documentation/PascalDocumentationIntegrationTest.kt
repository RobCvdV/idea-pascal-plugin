package nl.akiar.pascal.documentation

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes

/**
 * Integration tests for end-to-end documentation generation.
 *
 * These tests verify that hovering over identifiers produces correct
 * documentation output through the real PascalDocumentationProvider path
 * (getCustomDocumentationElement + generateDoc).
 */
class PascalDocumentationIntegrationTest : BasePlatformTestCase() {

    private val provider = PascalDocumentationProvider()

    // ==================== Local Variable Documentation ====================

    fun testHoverLocalVariableShowsType() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure TestProc;
            var
              MyVar: Integer;
            begin
              MyVar<caret> := 1;
            end;
            end.
        """.trimIndent())

        val identifier = findIdentifierAtCaret(file)
        assertNotNull("Should find identifier at caret", identifier)

        val element = provider.getCustomDocumentationElement(
            myFixture.editor, file, identifier!!, identifier.textOffset
        )
        assertNotNull("Should find documentation element for local variable", element)

        val doc = provider.generateDoc(element!!, identifier)
        assertNotNull("Should generate doc for local variable", doc)
        assertTrue("Doc should contain type 'Integer'", doc!!.contains("Integer"))
    }

    // ==================== Member Field Documentation ====================

    fun testHoverMemberFieldShowsFieldType() {
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

        val element = provider.getCustomDocumentationElement(
            myFixture.editor, file, identifier!!, identifier.textOffset
        )
        assertNotNull("Should find documentation element for member field", element)

        val doc = provider.generateDoc(element!!, identifier)
        assertNotNull("Should generate doc for member field", doc)
        assertTrue("Doc should contain type 'String'", doc!!.contains("String"))
    }

    // ==================== Member Method Documentation ====================

    fun testHoverMemberMethodShowsSignature() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                function Calculate(X: Integer): Integer;
              end;
            implementation
            function TMyClass.Calculate(X: Integer): Integer;
            begin
              Result := X * 2;
            end;
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
              Obj.Calculate<caret>(5);
            end;
            end.
        """.trimIndent())

        val identifier = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Calculate' identifier", identifier)

        val element = provider.getCustomDocumentationElement(
            myFixture.editor, file, identifier!!, identifier.textOffset
        )
        assertNotNull("Should find documentation element for member method", element)

        val doc = provider.generateDoc(element!!, identifier)
        assertNotNull("Should generate doc for member method", doc)
        assertTrue("Doc should contain method name 'Calculate'", doc!!.contains("Calculate"))
    }

    // ==================== Member Property Documentation ====================

    fun testHoverMemberPropertyShowsType() {
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

        val element = provider.getCustomDocumentationElement(
            myFixture.editor, file, identifier!!, identifier.textOffset
        )
        assertNotNull("Should find documentation element for property", element)

        val doc = provider.generateDoc(element!!, identifier)
        assertNotNull("Should generate doc for property", doc)
        assertTrue("Doc should contain type 'Integer'", doc!!.contains("Integer"))
    }

    // ==================== Type Definition Documentation ====================

    fun testHoverTypeNameShowsTypeInfo() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              { My class does things }
              TMyClass = class
              public
                Name: String;
              end;
            implementation
            end.
        """.trimIndent())

        val typeDef = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            file, nl.akiar.pascal.psi.PascalTypeDefinition::class.java
        ).first { it.name == "TMyClass" }

        val identifier = typeDef.nameIdentifier!!
        val element = provider.getCustomDocumentationElement(
            myFixture.editor, file, identifier, identifier.textOffset
        )
        assertNotNull("Should find documentation element for type", element)

        val doc = provider.generateDoc(element!!, identifier)
        assertNotNull("Should generate doc for type definition", doc)
        assertTrue("Doc should contain type name 'TMyClass'", doc!!.contains("TMyClass"))
    }

    // ==================== Inherited Member Documentation ====================

    fun testHoverInheritedMemberShowsInfo() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TBase = class
              public
                procedure BaseMethod;
              end;
              TDerived = class(TBase)
              public
                procedure DerivedMethod;
              end;
            implementation
            procedure TBase.BaseMethod; begin end;
            procedure TDerived.DerivedMethod; begin end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              D: TDerived;
            begin
              D.BaseMethod<caret>;
            end;
            end.
        """.trimIndent())

        val identifier = findIdentifierAtCaret(file)
        assertNotNull("Should find 'BaseMethod' identifier", identifier)

        val element = provider.getCustomDocumentationElement(
            myFixture.editor, file, identifier!!, identifier.textOffset
        )
        assertNotNull("Should find documentation element for inherited member", element)

        val doc = provider.generateDoc(element!!, identifier)
        assertNotNull("Should generate doc for inherited member", doc)
        assertTrue("Doc should contain 'BaseMethod'", doc!!.contains("BaseMethod"))
    }

    // ==================== Function Return Type Chain Documentation ====================

    fun testHoverChainResultShowsInfo() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TConfig = class
              public
                Title: String;
              end;
            function GetConfig: TConfig;
            implementation
            function GetConfig: TConfig;
            begin
              Result := nil;
            end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              S: String;
            begin
              S := GetConfig.Title<caret>;
            end;
            end.
        """.trimIndent())

        val identifier = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Title' identifier", identifier)

        val element = provider.getCustomDocumentationElement(
            myFixture.editor, file, identifier!!, identifier.textOffset
        )
        assertNotNull("Should find documentation element for chain result", element)

        val doc = provider.generateDoc(element!!, identifier)
        assertNotNull("Should generate doc for chain member", doc)
        assertTrue("Doc should contain 'String'", doc!!.contains("String"))
    }

    // ==================== Routine Documentation ====================

    fun testHoverRoutineCallShowsSignature() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            function Add(A, B: Integer): Integer;
            implementation
            function Add(A, B: Integer): Integer;
            begin
              Result := A + B;
            end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              Add<caret>(1, 2);
            end;
            end.
        """.trimIndent())

        val identifier = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Add' identifier", identifier)

        val element = provider.getCustomDocumentationElement(
            myFixture.editor, file, identifier!!, identifier.textOffset
        )
        assertNotNull("Should find documentation element for routine call", element)

        val doc = provider.generateDoc(element!!, identifier)
        assertNotNull("Should generate doc for routine call", doc)
        assertTrue("Doc should contain 'Add'", doc!!.contains("Add"))
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
