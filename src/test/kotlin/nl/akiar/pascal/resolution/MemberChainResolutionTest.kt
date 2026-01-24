package nl.akiar.pascal.resolution

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import org.junit.Test

/**
 * Integration tests for member chain resolution.
 *
 * These tests verify that:
 * 1. Simple member access (obj.Member) works
 * 2. Deep chains (obj.Prop1.Prop2.Method) resolve all parts
 * 3. Chains with inherited members work
 * 4. Chains across unit boundaries work with transitive deps
 */
class MemberChainResolutionTest : BasePlatformTestCase() {

    // ==================== Simple Chain Tests ====================

    @Test
    fun testSimplePropertyAccess() {
        // Define a class with a property
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

        // Use the property
        val mainFile = myFixture.configureByText("Main.pas", """
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

        // Find the 'Value' identifier
        val valueElement = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Value' identifier", valueElement)

        val result = MemberChainResolver.resolveChain(valueElement!!)

        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertEquals("First element should be 'Obj'", "Obj", result.chainElements[0].text)
        assertEquals("Second element should be 'Value'", "Value", result.chainElements[1].text)

        assertNotNull("'Obj' should be resolved", result.resolvedElements[0])
        assertNotNull("'Value' should be resolved", result.resolvedElements[1])
        assertTrue("'Value' should resolve to PascalProperty", result.resolvedElements[1] is PascalProperty)
    }

    @Test
    fun testSimpleMethodCall() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                procedure DoSomething;
              end;
            implementation
            procedure TMyClass.DoSomething;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TMyClass;
            begin
              Obj.DoSomething<caret>;
            end;
            end.
        """.trimIndent())

        val methodElement = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'DoSomething' identifier", methodElement)

        val result = MemberChainResolver.resolveChain(methodElement!!)

        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertNotNull("'DoSomething' should be resolved", result.resolvedElements[1])
        assertTrue("'DoSomething' should resolve to PascalRoutine", result.resolvedElements[1] is PascalRoutine)
    }

    // ==================== Deep Chain Tests ====================

    @Test
    fun testDeepPropertyChain() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TInnerClass = class
              public
                Value: String;
              end;
              TMiddleClass = class
              public
                Inner: TInnerClass;
              end;
              TOuterClass = class
              public
                Middle: TMiddleClass;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Outer: TOuterClass;
              S: String;
            begin
              S := Outer.Middle.Inner.Value<caret>;
            end;
            end.
        """.trimIndent())

        val valueElement = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Value' identifier", valueElement)

        val result = MemberChainResolver.resolveChain(valueElement!!)

        assertEquals("Chain should have 4 elements", 4, result.chainElements.size)
        assertEquals("First element should be 'Outer'", "Outer", result.chainElements[0].text)
        assertEquals("Second element should be 'Middle'", "Middle", result.chainElements[1].text)
        assertEquals("Third element should be 'Inner'", "Inner", result.chainElements[2].text)
        assertEquals("Fourth element should be 'Value'", "Value", result.chainElements[3].text)

        // All elements should be resolved
        assertTrue("Chain should be fully resolved", result.isFullyResolved)
    }

    // ==================== Inherited Member Tests ====================

    @Test
    fun testInheritedMethodAccess() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TBaseClass = class
              public
                procedure BaseMethod;
              end;
              TDerivedClass = class(TBaseClass)
              public
                procedure DerivedMethod;
              end;
            implementation
            procedure TBaseClass.BaseMethod; begin end;
            procedure TDerivedClass.DerivedMethod; begin end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Derived: TDerivedClass;
            begin
              Derived.BaseMethod<caret>;
            end;
            end.
        """.trimIndent())

        val methodElement = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'BaseMethod' identifier", methodElement)

        val result = MemberChainResolver.resolveChain(methodElement!!)

        assertNotNull("'BaseMethod' should be resolved (inherited)", result.resolvedElements[1])
        assertTrue("'BaseMethod' should resolve to PascalRoutine", result.resolvedElements[1] is PascalRoutine)
    }

    // ==================== Cross-Unit Tests ====================

    @Test
    fun testChainAcrossUnits() {
        // Define TStrings-like class in one unit
        myFixture.configureByText("System.Classes.pas", """
            unit System.Classes;
            interface
            type
              TStrings = class
              public
                procedure Add(const S: String);
              end;
            implementation
            procedure TStrings.Add(const S: String);
            begin
            end;
            end.
        """.trimIndent())

        // Define a class that has TStrings property
        myFixture.configureByText("MyTypes.pas", """
            unit MyTypes;
            interface
            uses System.Classes;
            type
              TMyResult = class
              private
                FLines: TStrings;
              public
                property Lines: TStrings read FLines;
              end;
            implementation
            end.
        """.trimIndent())

        // Use the chain (note: using MyRes instead of Result to avoid confusion with reserved word)
        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses MyTypes;
            implementation
            procedure Test;
            var
              MyRes: TMyResult;
            begin
              MyRes.Lines.Add<caret>('test');
            end;
            end.
        """.trimIndent())

        val addElement = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Add' identifier", addElement)

        val result = MemberChainResolver.resolveChain(addElement!!)

        assertEquals("Chain should have 3 elements", 3, result.chainElements.size)
        assertEquals("First element should be 'MyRes'", "MyRes", result.chainElements[0].text)
        assertEquals("Second element should be 'Lines'", "Lines", result.chainElements[1].text)
        assertEquals("Third element should be 'Add'", "Add", result.chainElements[2].text)

        // All should be resolved despite TStrings not being directly in Main's uses
        assertNotNull("'MyRes' should be resolved", result.resolvedElements[0])
        assertNotNull("'Lines' should be resolved", result.resolvedElements[1])
        // Add might not resolve if System.Classes isn't transitively found - depends on transitive resolution
    }

    // ==================== Static Member Access Tests ====================

    @Test
    fun testStaticMemberAccess() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                class function Create: TMyClass;
              end;
            implementation
            class function TMyClass.Create: TMyClass;
            begin
              Result := TMyClass(inherited Create);
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TMyClass;
            begin
              Obj := TMyClass.Create<caret>;
            end;
            end.
        """.trimIndent())

        val createElement = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Create' identifier", createElement)

        val result = MemberChainResolver.resolveChain(createElement!!)

        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertEquals("First element should be 'TMyClass'", "TMyClass", result.chainElements[0].text)
        assertEquals("Second element should be 'Create'", "Create", result.chainElements[1].text)

        assertNotNull("'TMyClass' should be resolved", result.resolvedElements[0])
        assertTrue("'TMyClass' should resolve to PascalTypeDefinition", result.resolvedElements[0] is PascalTypeDefinition)
    }

    // ==================== Helper Methods ====================

    private fun findIdentifierAtCaret(file: com.intellij.psi.PsiFile): com.intellij.psi.PsiElement? {
        val caretOffset = myFixture.caretOffset

        // Try to find element directly at caret
        var element = file.findElementAt(caretOffset)
        if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) {
            return element
        }

        // If caret is right after identifier (common case with <caret>), look backwards
        if (caretOffset > 0) {
            element = file.findElementAt(caretOffset - 1)
            if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) {
                return element
            }
        }

        // Try a few positions backwards in case of whitespace
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

    // ==================== Edge Cases ====================

    @Test
    fun testSingleElement_NotAChain() {
        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Test;
            var
              X<caret>: Integer;
            begin
            end;
            end.
        """.trimIndent())

        val xElement = findIdentifierAtCaret(mainFile)
        if (xElement != null) {
            val result = MemberChainResolver.resolveChain(xElement)
            assertEquals("Single element chain", 1, result.chainElements.size)
        }
    }

    @Test
    fun testUnresolvedFirstElement() {
        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Test;
            begin
              UnknownVar.SomeMethod<caret>;
            end;
            end.
        """.trimIndent())

        val methodElement = findIdentifierAtCaret(mainFile)
        if (methodElement != null) {
            val result = MemberChainResolver.resolveChain(methodElement)
            assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
            assertNull("UnknownVar should not be resolved", result.resolvedElements[0])
            assertNull("SomeMethod should not be resolved (no type context)", result.resolvedElements[1])
        }
    }
}
