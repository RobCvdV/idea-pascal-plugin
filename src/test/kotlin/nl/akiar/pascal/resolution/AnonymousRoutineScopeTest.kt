package nl.akiar.pascal.resolution

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.VariableKind
import nl.akiar.pascal.stubs.PascalVariableIndex
import org.junit.Test

/**
 * Tests for variable and type resolution inside anonymous routines and implementation methods.
 *
 * Verifies that:
 * 1. Variables from enclosing scopes are visible inside anonymous routines
 * 2. Parameters of anonymous routines resolve correctly
 * 3. Types used in anonymous routine parameters resolve via the unit's uses clause
 * 4. Fields are accessible from implementation methods
 * 5. Fields are accessible from anonymous routines inside implementation methods
 */
class AnonymousRoutineScopeTest : BasePlatformTestCase() {

    @Test
    fun testEnclosingScopeVariableVisibleInAnonymousRoutine() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            implementation
            procedure OuterProc;
            var
              LOuterVar: Integer;
            begin
              SomeList.ForEach(
                procedure(AItem: Integer)
                begin
                  LOuterVar<caret> := AItem;
                end
              );
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'LOuterVar' identifier", element)

        val resolved = PascalVariableIndex.findVariableAtPosition("LOuterVar", file, element!!.textOffset)
        assertNotNull("LOuterVar from enclosing scope should be visible inside anonymous routine", resolved)
        assertEquals("Should resolve to the outer variable", "LOuterVar", resolved!!.name)
    }

    @Test
    fun testAnonymousRoutineParameterResolves() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            implementation
            procedure OuterProc;
            begin
              SomeList.ForEach(
                procedure(AItem<caret>: Integer)
                begin
                end
              );
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'AItem' identifier", element)

        val resolved = PascalVariableIndex.findVariableAtPosition("AItem", file, element!!.textOffset)
        assertNotNull("Anonymous routine parameter should resolve", resolved)
        assertEquals("Should resolve to the parameter", "AItem", resolved!!.name)
    }

    @Test
    fun testTypeInAnonymousRoutineParameterResolves() {
        myFixture.configureByText("TypesUnit.pas", """
            unit TypesUnit;
            interface
            type
              TMyItem = class
              public
                Name: string;
              end;
            implementation
            end.
        """.trimIndent())

        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            uses TypesUnit;
            implementation
            procedure OuterProc;
            begin
              SomeList.ForEach(
                function(AItem: TMyItem<caret>): Boolean
                begin
                  Result := True;
                end
              );
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'TMyItem' identifier", element)

        // Verify the type can be found via the uses clause at this offset
        val typeResult = nl.akiar.pascal.stubs.PascalTypeIndex.findTypesWithUsesValidation("TMyItem", file, element!!.textOffset)
        assertFalse("TMyItem should be found in scope via uses clause", typeResult.inScopeTypes.isEmpty())
    }

    @Test
    fun testFieldAccessFromImplementationMethod() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
              public
                procedure DoWork;
              end;
            implementation
            procedure TMyClass.DoWork;
            begin
              FValue<caret> := 42;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'FValue' identifier", element)

        val resolved = PascalVariableIndex.findVariableAtPosition("FValue", file, element!!.textOffset)
        assertNotNull("Private field should be accessible from implementation method of the same class", resolved)
        assertEquals("Should resolve to the field", "FValue", resolved!!.name)
        assertEquals("Should be a field", VariableKind.FIELD, resolved.variableKind)
    }

    @Test
    fun testFieldAccessFromAnonymousRoutineInsideImplementationMethod() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                FRepo: Integer;
              public
                procedure DoWork;
              end;
            implementation
            procedure TMyClass.DoWork;
            begin
              SomeList.ForEach(
                procedure(AItem: Integer)
                begin
                  FRepo<caret> := AItem;
                end
              );
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'FRepo' identifier", element)

        val resolved = PascalVariableIndex.findVariableAtPosition("FRepo", file, element!!.textOffset)
        assertNotNull("Field should be accessible from anonymous routine inside implementation method", resolved)
        assertEquals("Should resolve to the field", "FRepo", resolved!!.name)
        assertEquals("Should be a field", VariableKind.FIELD, resolved.variableKind)
    }

    @Test
    fun testAnonymousRoutinePsiStructure() {
        // Verify that anonymous routines produce proper PascalRoutine PSI elements
        // This catches the AnonymousMethodNode → ROUTINE_DECLARATION mapping
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                FRepo: Integer;
              public
                function Update: Integer;
              end;
            implementation
            function TMyClass.Update: Integer;
            begin
              Result := DoSomething(
                function(const AParam: Integer): Boolean
                begin
                  FRepo<caret> := AParam;
                  Result := True;
                end
              );
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'FRepo' identifier at caret", element)

        // The immediate containing routine should be the anonymous routine
        val containingRoutine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            element, nl.akiar.pascal.psi.PascalRoutine::class.java)
        assertNotNull("Should find containing PascalRoutine (anonymous)", containingRoutine)

        // The anonymous routine's parent routine should be TMyClass.Update
        val outerRoutine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            containingRoutine, nl.akiar.pascal.psi.PascalRoutine::class.java)
        assertNotNull("Anonymous routine should be nested inside outer PascalRoutine", outerRoutine)

        // The outer routine should belong to TMyClass
        val outerClass = outerRoutine!!.getContainingClass()
        assertNotNull("Outer routine should belong to TMyClass", outerClass)
        assertEquals("TMyClass", outerClass!!.name)

        // Field resolution from inside anonymous routine
        val resolved = PascalVariableIndex.findVariableAtPosition("FRepo", file, element!!.textOffset)
        assertNotNull("Field should be accessible from inside anonymous routine", resolved)
        assertEquals("FRepo", resolved!!.name)
        assertEquals(VariableKind.FIELD, resolved.variableKind)
    }

    @Test
    fun testAnonymousRoutineParameterTypeNameIsNotNull() {
        myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            implementation
            procedure OuterProc;
            begin
              DoSomething(
                function(const ADossier<caret>: Integer): Boolean
                begin
                  Result := True;
                end
              );
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(myFixture.file)
        assertNotNull("Should find 'ADossier' identifier", element)

        val varDef = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            element, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
        assertNotNull("Should find PascalVariableDefinition parent", varDef)

        val typeName = varDef!!.typeName
        assertNotNull("getTypeName() should return non-null for anonymous routine parameter", typeName)
        assertEquals("Integer", typeName)
    }

    @Test
    fun testAnonymousRoutineParameterHasCorrectPsiStructure() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                function Update: Integer;
              end;
            implementation
            function TMyClass.Update: Integer;
            begin
              Result := DoSomething(
                function(const AParam<caret>: Integer): Boolean
                begin
                  Result := True;
                end
              );
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull("Should find 'AParam' identifier", element)

        // AParam should be inside a PascalVariableDefinition with PARAMETER kind
        val paramParent = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            element, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
        assertNotNull("AParam should be inside a PascalVariableDefinition", paramParent)
        assertEquals("Should be PARAMETER kind", VariableKind.PARAMETER, paramParent?.variableKind)

        // The containing routine should be the anonymous routine, not the outer method
        val containingRoutine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            element, nl.akiar.pascal.psi.PascalRoutine::class.java)
        assertNotNull("AParam should have a containing PascalRoutine (anonymous)", containingRoutine)

        val resolved = PascalVariableIndex.findVariableAtPosition("AParam", file, element!!.textOffset)
        assertNotNull("Anonymous routine parameter should resolve", resolved)
        assertEquals("AParam", resolved!!.name)
    }

    private fun findIdentifierAtCaret(file: com.intellij.psi.PsiFile): com.intellij.psi.PsiElement? {
        val caretOffset = myFixture.caretOffset
        var element = file.findElementAt(caretOffset)
        if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) return element
        if (caretOffset > 0) {
            element = file.findElementAt(caretOffset - 1)
            if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) return element
        }
        for (offset in 1..3) {
            if (caretOffset - offset >= 0) {
                element = file.findElementAt(caretOffset - offset)
                if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) return element
            }
        }
        return null
    }
}
