package nl.akiar.pascal.annotator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for PascalScopeAnnotator — uses a conservative hybrid strategy:
 * - Unit references: always checked
 * - Type references in declarations (interface + implementation): checked
 * - Routine bodies: skipped entirely (too many local-scope identifiers)
 */
class PascalScopeAnnotatorTest : BasePlatformTestCase() {

    // ==================== Unit reference errors ====================

    @Test
    fun testUnknownUnitInUsesClause() {
        val mainText = """
            unit Main;
            interface
            uses <error descr="Unknown unit `FakeUnit`, please add unit and location to the project file">FakeUnit</error>;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    // ==================== Type reference errors ====================

    @Test
    fun testTypeNotInUsesClause_InterfaceSection() {
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
    fun testTypeNotInScope_ExistsInMultipleUnits() {
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

    @Test
    fun testTypeNotInUsesClause_ImplementationDeclaration() {
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
            var
              x: <error descr="Unit 'unita' is not in uses clause. Add it to uses clause.">TMyType</error>;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    // ==================== No-error cases ====================

    @Test
    fun testTypeInUsesClause_NoError() {
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
            uses UnitA;
            var
              x: TMyType;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testLastWinsRule() {
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
        myFixture.checkHighlighting()
    }

    @Test
    fun testSameFileType_NoError() {
        val mainText = """
            unit Main;
            interface
            type
              TMyType = String;
            var
              x: TMyType;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testImplementationUsesInImplementation_NoError() {
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
        myFixture.checkHighlighting()
    }

    @Test
    fun testBuiltInType_NoError() {
        val mainText = """
            unit Main;
            interface
            var
              S: String;
              I: Integer;
              B: Boolean;
              D: Double;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testTObjectBuiltIn_NoError() {
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
        myFixture.checkHighlighting()
    }

    @Test
    fun testEnumElement_NoError() {
        val mainText = """
            unit Main;
            interface
            type
              TColor = (Red, Green, Blue);
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testGenericParameter_NoError() {
        val mainText = """
            unit Main;
            interface
            type
              TMyList<T> = class
                function GetItem: T;
              end;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testPropertySpecifier_NoError() {
        val mainText = """
            unit Main;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
              public
                property Value: Integer read FValue write FValue;
              end;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testMemberChainNotFlaggedAsScope() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
                procedure DoWork;
              end;
            implementation
            procedure TMyClass.DoWork; begin end;
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var Obj: TMyClass;
            begin
              Obj.DoWork;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    // ==================== Routine body is skipped ====================

    @Test
    fun testRoutineBody_IdentifiersNotChecked() {
        // Identifiers inside routine bodies should NOT be checked
        // (local vars, fields, params, exception vars, etc.)
        val mainText = """
            unit Main;
            interface
            implementation
            procedure Test;
            var
              MyLocal: Integer;
            begin
              MyLocal := 42;
              UnknownThing := 1;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testRoutineBody_ExceptionHandler_NoError() {
        val mainText = """
            unit Main;
            interface
            implementation
            procedure Test;
            begin
              try
                DoSomething;
              except
                on E: Exception do
                  Writeln(E.Message);
              end;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testRoutineBody_ForLoop_NoError() {
        val mainText = """
            unit Main;
            interface
            implementation
            procedure Test;
            var I: Integer;
            begin
              for I := 0 to 10 do
                Writeln(I);
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testRoutineBody_ClassFieldAccess_NoError() {
        val mainText = """
            unit Main;
            interface
            type
              TMyClass = class
              private
                FName: String;
              public
                procedure SetName(AName: String);
              end;
            implementation
            procedure TMyClass.SetName(AName: String);
            begin
              FName := AName;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testClassBody_TypeReferences_InScope() {
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
            uses UnitA;
            type
              TMyClass = class
                FValue: TMyType;
              end;
            implementation
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testRoutineDeclaration_ParamTypes_Checked() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type TMyParam = Integer;
            implementation
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            uses UnitA;
            procedure DoWork(A: TMyParam);
            implementation
            procedure DoWork(A: TMyParam);
            begin
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }
}
