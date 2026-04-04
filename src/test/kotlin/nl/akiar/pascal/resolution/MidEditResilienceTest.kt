package nl.akiar.pascal.resolution

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.*
import nl.akiar.pascal.psi.PsiUtil
import org.junit.Test

/**
 * Tests for fixes in the mid-edit-resilience branch:
 *
 * 1. Enum values with ordinal assignments (e.g., `modeAlways = 2`)
 * 2. Constructor return types (`TMyClass.Create` → TMyClass)
 * 3. Generic list indexer (`LRides[i].Tasks`)
 * 4. Escaped identifiers (`&Of`, `&In`)
 * 5. Generic substitution in documentation signatures
 */
class MidEditResilienceTest : BasePlatformTestCase() {

    // ==================== Issue 1: Enum Ordinal Stripping ====================

    @Test
    fun testEnumValueWithOrdinalAssignment_ChainResolution() {
        myFixture.configureByText("EnumTypes.pas", """
            unit EnumTypes;
            interface
            type
              TMileageMode = (askForMileageMode_Never = 0, askForMileageMode_Always = 1, askForMileageMode_Prompt = 2);
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses EnumTypes;
            implementation
            procedure Test;
            var
              LMode: TMileageMode;
            begin
              LMode := TMileageMode.askForMileageMode_Always<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find askForMileageMode_Always identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertNotNull("Second element should be resolved", result.resolvedElements.last())
    }

    @Test
    fun testEnumValueWithOrdinalAssignment_BareIdentifier() {
        myFixture.configureByText("EnumTypes.pas", """
            unit EnumTypes;
            interface
            type
              TAlign = (taLeft = 0, taCenter = 1, taRight = 2);
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses EnumTypes;
            implementation
            procedure Test;
            var
              X: TAlign;
            begin
              X := taCenter<caret>;
            end;
            end.
        """.trimIndent())

        // PascalIdentifierReference should find the enum value via contributed references
        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find taCenter identifier", element)

        // References come from the reference provider (ReferenceProvidersRegistry), not getReferences()
        val contributed = com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
            .getReferencesFromProviders(element!!)
        val allRefs = element.references.toList() + contributed.toList()
        var resolved: PsiElement? = null
        for (ref in allRefs) {
            resolved = ref.resolve()
            if (resolved != null) break
        }
        assertNotNull("taCenter should resolve to enum element", resolved)
    }

    // ==================== Issue 2: Constructor Return Types ====================

    @Test
    fun testConstructorChainResolution() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                constructor Create;
                Title: String;
              end;
            implementation
            constructor TMyClass.Create;
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
              S: String;
            begin
              S := TMyClass.Create.Title<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find Title identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 3 elements", 3, result.chainElements.size)

        assertNotNull("TMyClass should resolve", result.resolvedElements[0])
        assertTrue("TMyClass should be PascalTypeDefinition", result.resolvedElements[0] is PascalTypeDefinition)
        assertNotNull("Create should resolve", result.resolvedElements[1])
        assertTrue("Create should be PascalRoutine", result.resolvedElements[1] is PascalRoutine)
        assertNotNull("Title should resolve (through constructor return type)", result.resolvedElements[2])
    }

    @Test
    fun testConstructorTypeInference() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                constructor Create;
                Title: String;
              end;
            implementation
            constructor TMyClass.Create;
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
              S: String;
            begin
              var LObj := TMyClass.Create;
              S := LObj.Title<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find Title identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertNotNull("Title should be resolved via constructor type inference", result.resolvedElements.last())
    }

    @Test
    fun testGenericConstructorTypeInference() {
        myFixture.configureByText("GenericTypes.pas", """
            unit GenericTypes;
            interface
            type
              TEntityList<T> = class
              public
                constructor Create;
                function GetItem(Index: Integer): T;
              end;
            implementation
            constructor TEntityList<T>.Create;
            begin
            end;
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericTypes, Rides;
            implementation
            procedure Test;
            begin
              var LRides := TEntityList<TRide>.Create;
              LRides.GetItem<caret>(0);
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find GetItem identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertNotNull("GetItem should resolve", result.resolvedElements.last())
        assertTrue("GetItem should be PascalRoutine", result.resolvedElements.last() is PascalRoutine)
    }

    // ==================== Issue 3: Bracket Indexer Resolution ====================

    @Test
    fun testBracketIndexerResolution() {
        myFixture.configureByText("GenericList.pas", """
            unit GenericList;
            interface
            type
              TEntityList<T> = class
              public
                property Items[Index: Integer]: T;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
                Tasks: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericList, Rides;
            implementation
            procedure Test;
            var
              LRides: TEntityList<TRide>;
              i: Integer;
            begin
              LRides[i].RideName<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find RideName identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 2 elements (LRides, RideName)", 2, result.chainElements.size)
        assertNotNull("LRides should resolve", result.resolvedElements[0])
        assertNotNull("RideName should resolve through bracket indexer", result.resolvedElements[1])
        assertTrue("RideName should be PascalVariableDefinition (field)",
            result.resolvedElements[1] is PascalVariableDefinition)
    }

    @Test
    fun testBracketIndexerWithInferredType() {
        myFixture.configureByText("GenericList.pas", """
            unit GenericList;
            interface
            type
              TEntityList<T> = class
              public
                constructor Create;
                property Items[Index: Integer]: T;
              end;
            implementation
            constructor TEntityList<T>.Create;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Tasks.pas", """
            unit Tasks;
            interface
            type
              TTask = class
              public
                TaskName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericList, Tasks;
            implementation
            procedure Test;
            begin
              var LTasks := TEntityList<TTask>.Create;
              LTasks[0].TaskName<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find TaskName identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertNotNull("TaskName should resolve through indexer on inferred-type list",
            result.resolvedElements.last())
    }

    // ==================== Issue 5: Escaped Identifiers ====================

    @Test
    fun testEscapedIdentifierChainResolution() {
        myFixture.configureByText("Planning.pas", """
            unit Planning;
            interface
            type
              TPlanning = class
              public
                class function &Of(ATasks: Integer): TPlanning;
              end;
            implementation
            class function TPlanning.&Of(ATasks: Integer): TPlanning;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Planning;
            implementation
            procedure Test;
            var
              LResult: TPlanning;
            begin
              LResult := TPlanning.&Of<caret>(42);
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find &Of identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertNotNull("TPlanning should resolve", result.resolvedElements[0])
        assertNotNull("&Of should resolve (escaped identifier)", result.resolvedElements[1])
        assertTrue("&Of should resolve to PascalRoutine", result.resolvedElements[1] is PascalRoutine)
    }

    @Test
    fun testEscapedIdentifierAsFirstElement() {
        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Test;
            var
              &Type: Integer;
            begin
              &Type<caret> := 42;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find &Type identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertNotNull("&Type should resolve as variable", result.resolvedElements[0])
        assertTrue("&Type should resolve to PascalVariableDefinition",
            result.resolvedElements[0] is PascalVariableDefinition)
    }

    // ==================== Generic Substitution in Chain Resolution ====================

    @Test
    fun testGenericSubstitutionForMemberChain() {
        myFixture.configureByText("GenericTypes.pas", """
            unit GenericTypes;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericTypes, Rides;
            implementation
            procedure Test;
            var
              LRides: TEntityList<TRide>;
            begin
              LRides.GetItem(0).RideName<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find RideName identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 3 elements", 3, result.chainElements.size)
        assertNotNull("RideName should resolve after generic substitution (T -> TRide)",
            result.resolvedElements[2])
    }

    @Test
    fun testGenericTypeArgMapForDocumentation() {
        myFixture.configureByText("GenericTypes.pas", """
            unit GenericTypes;
            interface
            type
              TEntityList<T> = class
              public
                function Add(const Value: T): Integer;
              end;
            implementation
            function TEntityList<T>.Add(const Value: T): Integer;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Tasks.pas", """
            unit Tasks;
            interface
            type
              TTask = class
              public
                TaskName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericTypes, Tasks;
            implementation
            procedure Test;
            var
              LTasks: TEntityList<TTask>;
            begin
              LTasks.Add<caret>(nil);
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find Add identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        val addIndex = result.chainElements.indexOfFirst { it.text == "Add" }
        assertTrue("Add should be found in chain", addIndex >= 0)

        // The type arg map for Add should map T → TTask
        val typeArgMap = result.getTypeArgMapForIndex(addIndex)
        assertNotNull("TypeArgMap should be set for Add", typeArgMap)
        assertTrue("TypeArgMap should contain T mapping", typeArgMap.containsKey("T"))
        assertEquals("T should map to TTask", "TTask", typeArgMap["T"])
    }

    // ==================== Documentation: Inferred Type Fallback ====================

    @Test
    fun testInferredTypeForDocumentation() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                constructor Create;
                Title: String;
              end;
            implementation
            constructor TMyClass.Create;
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
            begin
              var LObj := TMyClass.Create;
              LObj<caret>.Title;
            end;
            end.
        """.trimIndent())

        // Find the LObj variable definition and verify inferred type
        val vars = PsiTreeUtil.findChildrenOfType(mainFile, PascalVariableDefinition::class.java)
        val lObj = vars.firstOrNull { it.name == "LObj" }
        assertNotNull("Should find LObj variable", lObj)
        assertNull("LObj should have no explicit type", lObj!!.typeName)

        val inferredType = MemberChainResolver.getInferredTypeOf(lObj, mainFile)
        assertNotNull("Should infer type TMyClass from constructor", inferredType)
        assertEquals("Inferred type should be TMyClass", "TMyClass", inferredType!!.name)
    }

    // ==================== Helper Methods ====================

    private fun isIdentifierLike(element: PsiElement): Boolean {
        val type = element.node.elementType
        if (type == PascalTokenTypes.IDENTIFIER) return true
        for (idType in PsiUtil.IDENTIFIER_LIKE_TYPES) {
            if (type == idType) return true
        }
        return false
    }

    private fun findIdentifierAtCaret(file: PsiFile): PsiElement? {
        val caretOffset = myFixture.caretOffset

        // Try to find element directly at caret
        var element = file.findElementAt(caretOffset)
        if (element != null && isIdentifierLike(element)) {
            return element
        }

        // If caret is right after identifier, look backwards
        for (offset in 1..3) {
            if (caretOffset - offset >= 0) {
                element = file.findElementAt(caretOffset - offset)
                if (element != null && isIdentifierLike(element)) {
                    return element
                }
            }
        }

        return null
    }
}
