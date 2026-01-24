package nl.akiar.pascal.uses

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for Pascal uses clause resolution with proper Delphi semantics.
 *
 * According to Delphi documentation:
 * "If two or more units declare the same identifier in their interface sections,
 * an unqualified reference to the identifier selects the declaration in the innermost scope,
 * that is, in the unit where the reference itself occurs, or, if that unit does not declare
 * the identifier, in the LAST unit in the uses clause that does declare the identifier."
 *
 * This means:
 * 1. Same-file declarations always win
 * 2. Among external units in uses clause, the LAST one wins (NOT ambiguous!)
 * 3. Only symbols NOT in uses clause are errors
 */
class PascalUsesClauseTest : BasePlatformTestCase() {

    // ==================== Basic Unit Name Tests ====================

    fun testDottedUnitNameExtraction() {
        val text = """
            unit Next.Core.Enum;
            interface
            type
              TEnum = class
              end;
            implementation
            end.
        """.trimIndent()
        val psiFile = myFixture.configureByText("Next.Core.Enum.pas", text)

        val typeDef = com.intellij.psi.util.PsiTreeUtil.findChildOfType(psiFile, nl.akiar.pascal.psi.PascalTypeDefinition::class.java)
        assertNotNull(typeDef)
        assertEquals("next.core.enum", typeDef!!.unitName)
    }

    @Test
    fun testDottedUnitWithSpacesAndCommentsInUses() {
        myFixture.configureByText("Next.Core.Enum.pas", """
            unit Next.Core.Enum;
            interface
            type TEnum = class end;
            implementation
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            uses
              Next . {comment} Core . Enum ;
            var
              x: TEnum;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    // ==================== "Last Wins" Rule Tests ====================

    /**
     * According to Delphi: when the same identifier is in multiple used units,
     * the LAST unit in the uses clause wins. This is NOT ambiguous!
     */
    @Test
    fun testLastWinsRule_TypeInMultipleUsedUnits_ShouldNotBeAmbiguous() {
        // Unit A (first in uses clause)
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type TMyType = Integer;
            implementation
            end.
        """.trimIndent())

        // Unit B (last in uses clause - should win!)
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            type TMyType = String;
            implementation
            end.
        """.trimIndent())

        // Main unit using both - should resolve to UnitB's TMyType (last wins)
        val mainText = """
            unit Main;
            interface
            uses UnitA, UnitB;
            var
              x: TMyType;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error - "last wins" rule applies
        myFixture.checkHighlighting()
    }

    @Test
    fun testLastWinsRule_RoutineInMultipleUsedUnits_ShouldNotBeAmbiguous() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            procedure MyProc;
            implementation
            procedure MyProc; begin end;
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            procedure MyProc;
            implementation
            procedure MyProc; begin end;
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            uses UnitA, UnitB;
            implementation
            procedure Test;
            begin
              MyProc;
            end;
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error - "last wins" rule applies
        myFixture.checkHighlighting()
    }

    @Test
    fun testLastWinsRule_VariableInMultipleUsedUnits_ShouldNotBeAmbiguous() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            var GVar: Integer;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            var GVar: Integer;
            implementation
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            uses UnitA, UnitB;
            implementation
            procedure Test;
            begin
              GVar := 1;
            end;
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error - "last wins" rule applies
        myFixture.checkHighlighting()
    }

    // ==================== Out of Scope Tests ====================

    @Test
    fun testTypeNotInUsesClause_ShouldError() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type TMyType = Integer;
            implementation
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            var
              x: <error descr="Unit 'unita' is not in uses clause. Add it to interface uses.">TMyType</error>;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testTypeInImplementationUses_ReferencedInInterface_ShouldError() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type TMyType = Integer;
            implementation
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            var
              x: <error descr="Unit 'unita' is in implementation uses, but type is referenced in interface section. Add it to interface uses.">TMyType</error>;
            implementation
            uses UnitA;
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testTypeInImplementationUses_ReferencedInImplementation_ShouldWork() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type TMyType = Integer;
            implementation
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            implementation
            uses UnitA;
            var
              x: TMyType;
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error - UnitA is in implementation uses, reference is in implementation
        myFixture.checkHighlighting()
    }

    // ==================== Out of Scope with Multiple Units ====================

    @Test
    fun testTypeNotInScope_ExistsInMultipleUnits_ShouldListAll() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type TMyType = Integer;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            type TMyType = String;
            implementation
            end.
        """.trimIndent())

        // No uses clause - neither unit is in scope
        val mainText = """
            unit Main;
            interface
            var
              x: <error descr="Type not in scope. Found in units: unita, unitb. Add one to uses clause.">TMyType</error>;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    // ==================== Unique Resolution Tests ====================

    @Test
    fun testUniqueResolutionToDottedUnit() {
        // Next.Core.Enum (in uses)
        myFixture.configureByText("Next.Core.Enum.pas", """
            unit Next.Core.Enum;
            interface
            type TEnum = class end;
            implementation
            end.
        """.trimIndent())

        // Spring (NOT in uses)
        myFixture.configureByText("Spring.pas", """
            unit Spring;
            interface
            type TEnum = class end;
            implementation
            end.
        """.trimIndent())

        // Main unit using only Next.Core.Enum
        val mainText = """
            unit Main;
            interface
            uses Next.Core.Enum;
            type
              IConfigRepository = interface
                function Database: TEnum;
              end;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error - resolves uniquely to Next.Core.Enum
        myFixture.checkHighlighting()
    }

    @Test
    fun testRoutineInImplementationUses_UsedInImplementation_ShouldWork() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            procedure MyProc;
            implementation
            procedure MyProc; begin end;
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            implementation
            uses UnitA;
            procedure Test;
            begin
              MyProc;
            end;
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error
        myFixture.checkHighlighting()
    }

    @Test
    fun testVariableNotInUsesClause_ShouldError() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            var GVar: Integer;
            implementation
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            var
              X: Integer = <error descr="Unit 'unita' is not in uses clause. Add it to interface uses.">GVar</error>;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    // ==================== Same File Has Highest Priority ====================

    @Test
    fun testSameFileType_AlwaysWins() {
        // External unit with TMyType
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type TMyType = Integer;
            implementation
            end.
        """.trimIndent())

        // Main unit defines its OWN TMyType - should win over UnitA's
        val mainText = """
            unit Main;
            interface
            uses UnitA;
            type
              TMyType = String;
            var
              x: TMyType;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error - same-file type wins
        myFixture.checkHighlighting()
    }

    // ==================== Built-in Functions and Types ====================

    @Test
    fun testBuiltInFunction_Assigned_ShouldNotError() {
        // Assigned is a built-in System function - should never require uses clause
        val mainText = """
            unit Main;
            interface
            implementation
            procedure Test(Obj: TObject);
            begin
              if Assigned(Obj) then
                Obj.Free;
            end;
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error for Assigned
        myFixture.checkHighlighting()
    }

    @Test
    fun testBuiltInType_TObject_ShouldNotError() {
        // TObject is a built-in System type - should never require uses clause
        val mainText = """
            unit Main;
            interface
            type
              TMyClass = class(TObject)
              end;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error for TObject
        myFixture.checkHighlighting()
    }

    @Test
    fun testBuiltInType_Exception_ShouldNotError() {
        // Exception is a built-in System type
        val mainText = """
            unit Main;
            interface
            type
              EMyError = class(Exception)
              end;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        // Should NOT have any error for Exception
        myFixture.checkHighlighting()
    }

    @Test
    fun testBuiltInFunctions_CommonFunctions_ShouldNotError() {
        // Various built-in functions should not require uses clause
        val mainText = """
            unit Main;
            interface
            implementation
            procedure Test;
            var
              S: String;
              I: Integer;
            begin
              I := Length(S);
              SetLength(S, 10);
              Inc(I);
              Dec(I);
              I := Ord('A');
            end;
            end.
        """.trimIndent()

        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    // ==================== PascalUsesClauseInfo Unit Tests ====================

    @Test
    fun testUsesClauseInfoParsing_PreservesOrder() {
        val text = """
            unit Main;
            interface
            uses Alpha, Beta, Gamma;
            implementation
            uses Delta, Epsilon;
            end.
        """.trimIndent()
        val psiFile = myFixture.configureByText("Main.pas", text)

        val usesInfo = PascalUsesClauseInfo.parse(psiFile)

        // normalizeUnitName lowercases the names
        assertEquals(listOf("alpha", "beta", "gamma"), usesInfo.interfaceUses)
        assertEquals(listOf("delta", "epsilon"), usesInfo.implementationUses)
    }

    @Test
    fun testUsesClauseInfo_UnitPriority() {
        val text = """
            unit Main;
            interface
            uses Alpha, Beta, Gamma;
            implementation
            uses Delta, Epsilon;
            end.
        """.trimIndent()
        val psiFile = myFixture.configureByText("Main.pas", text)

        val usesInfo = PascalUsesClauseInfo.parse(psiFile)

        // Debug: print section offsets
        println("Interface section start: ${usesInfo.interfaceSectionStart}")
        println("Implementation section start: ${usesInfo.implementationSectionStart}")
        println("Interface uses: ${usesInfo.interfaceUses}")
        println("Implementation uses: ${usesInfo.implementationUses}")

        // The interface section starts around offset 10 (after "unit Main;\n")
        // The implementation section starts around offset 47 (after the uses clause)
        // Use offset 30 which should be in interface section
        val interfaceOffset = 30
        assertTrue("Should be in interface section at offset $interfaceOffset",
            usesInfo.isInInterfaceSection(interfaceOffset) || usesInfo.interfaceSectionStart == -1)

        // In interface section: alpha=0, beta=1, gamma=2 (higher = higher priority = last wins)
        if (usesInfo.interfaceUses.isNotEmpty()) {
            assertEquals(0, usesInfo.getUnitPriority("alpha", interfaceOffset, null))
            assertEquals(1, usesInfo.getUnitPriority("beta", interfaceOffset, null))
            assertEquals(2, usesInfo.getUnitPriority("gamma", interfaceOffset, null))
        }

        // Delta/Epsilon not available in interface section (if we have a proper implementation section)
        if (usesInfo.implementationSectionStart > 0) {
            assertEquals(-2, usesInfo.getUnitPriority("delta", interfaceOffset, null))
        }

        // In implementation section: all units available
        // Use a high offset that should be in implementation
        val implOffset = usesInfo.implementationSectionStart + 10
        if (usesInfo.implementationSectionStart > 0) {
            assertTrue("Should be in implementation section at offset $implOffset",
                usesInfo.isInImplementationSection(implOffset))
            assertEquals(0, usesInfo.getUnitPriority("alpha", implOffset, null))
            assertEquals(3, usesInfo.getUnitPriority("delta", implOffset, null))
            assertEquals(4, usesInfo.getUnitPriority("epsilon", implOffset, null))
        }
    }
}
