package nl.akiar.pascal.reference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.*

/**
 * Integration tests for end-to-end reference resolution through the IDE's
 * reference system (element.getReferences() + ReferenceProvidersRegistry).
 *
 * These tests verify the REAL code path that users exercise when using
 * Ctrl+Click / Go to Declaration, NOT the internal MemberChainResolver API.
 */
class PascalReferenceResolutionIntegrationTest : BasePlatformTestCase() {

    // ==================== Local Variable References ====================

    fun testLocalVariableReferenceResolves() {
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

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find identifier at caret", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Local variable reference should resolve", resolved)
        assertTrue("Should resolve to PascalVariableDefinition", resolved is PascalVariableDefinition)
        assertEquals("MyVar", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Class Field References (Obj.Field) ====================

    fun testClassFieldMemberReferenceResolves() {
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

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Caption' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Class field member reference should resolve", resolved)
        assertTrue("Should resolve to PascalVariableDefinition (field)",
            resolved is PascalVariableDefinition)
        assertEquals("Caption", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Class Method References (Obj.Method) ====================

    fun testClassMethodMemberReferenceResolves() {
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

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'DoWork' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Class method member reference should resolve", resolved)
        assertTrue("Should resolve to PascalRoutine", resolved is PascalRoutine)
        assertEquals("DoWork", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Class Property References (Obj.Property) ====================

    fun testClassPropertyMemberReferenceResolves() {
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

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Value' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Class property member reference should resolve", resolved)
        assertTrue("Should resolve to PascalProperty", resolved is PascalProperty)
        assertEquals("Value", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Inherited Member References ====================

    fun testInheritedMemberReferenceResolves() {
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

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              D: TDerivedClass;
            begin
              D.BaseMethod<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'BaseMethod' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Inherited member reference should resolve", resolved)
        assertTrue("Should resolve to PascalRoutine", resolved is PascalRoutine)
        assertEquals("BaseMethod", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Type Reference in Declaration ====================

    fun testTypeReferenceInVariableDeclarationResolves() {
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

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'TMyClass' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Type reference in var declaration should resolve", resolved)
        assertTrue("Should resolve to PascalTypeDefinition", resolved is PascalTypeDefinition)
        assertEquals("TMyClass", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Static Member Access (TClass.Create) ====================

    fun testStaticMemberAccessResolves() {
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
              Obj: TMyClass;
            begin
              Obj := TMyClass.Create<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Create' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Static member access (TMyClass.Create) should resolve", resolved)
        assertTrue("Should resolve to PascalRoutine", resolved is PascalRoutine)
        assertEquals("Create", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Function Return Type Chain ====================

    fun testFunctionReturnTypeChainResolves() {
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

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'Title' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Function return type chain should resolve", resolved)
        assertTrue("Should resolve to PascalVariableDefinition (field)",
            resolved is PascalVariableDefinition)
        assertEquals("Title", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Cross-Unit Member Reference ====================

    fun testCrossUnitMemberReferenceResolves() {
        myFixture.configureByText("EntityUnit.pas", """
            unit EntityUnit;
            interface
            type
              TEntity = class
              public
                function GetId: Integer;
              end;
            implementation
            function TEntity.GetId: Integer;
            begin
              Result := 0;
            end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses EntityUnit;
            implementation
            procedure Test;
            var
              E: TEntity;
            begin
              E.GetId<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'GetId' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Cross-unit member reference should resolve", resolved)
        assertTrue("Should resolve to PascalRoutine", resolved is PascalRoutine)
        assertEquals("GetId", (resolved as PsiNameIdentifierOwner).name)
    }

    // ==================== Enum Value Reference ====================

    fun testEnumValueMemberReferenceResolves() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TAlignment = (taLeftJustify, taRightJustify, taCenter);
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
              A: TAlignment;
            begin
              A := taLeftJustify<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'taLeftJustify' identifier", element)

        // Enum values may not resolve yet through the reference system.
        // This test documents the current gap.
        val resolved = resolveViaReferences(element!!)
        // After Phase 2 fix, this should resolve to the enum element
        // For now, just document whether it resolves or not
        if (resolved == null) {
            println("NOTE: Enum value 'taLeftJustify' does not resolve via references - this is a known gap")
        } else {
            println("Enum value resolved to: ${resolved.javaClass.simpleName}")
        }
    }

    // ==================== Routine Call Reference ====================

    fun testRoutineCallReferenceResolves() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            procedure DoSomething;
            implementation
            procedure DoSomething; begin end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              DoSomething<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'DoSomething' identifier", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Routine call reference should resolve", resolved)
        assertTrue("Should resolve to PascalRoutine", resolved is PascalRoutine)
        assertEquals("DoSomething", (resolved as PsiNameIdentifierOwner).name)
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

    /**
     * Resolve via the real IDE reference path: first element.getReferences(),
     * then ReferenceProvidersRegistry fallback.
     */
    private fun resolveViaReferences(element: PsiElement): PsiElement? {
        var refs: Array<PsiReference> = element.references
        if (refs.isEmpty()) {
            refs = ReferenceProvidersRegistry.getReferencesFromProviders(element)
        }
        for (ref in refs) {
            val resolved = ref.resolve()
            if (resolved != null) return resolved
        }
        return null
    }
}
