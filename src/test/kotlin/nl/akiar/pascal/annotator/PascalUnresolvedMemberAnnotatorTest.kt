package nl.akiar.pascal.annotator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for PascalUnresolvedMemberAnnotator — verifies that unresolved members
 * in chains are flagged, while valid members, cascading failures, and
 * unit-qualified access are not.
 */
class PascalUnresolvedMemberAnnotatorTest : BasePlatformTestCase() {

    // ==================== Error cases ====================

    @Test
    fun testUnresolvedMember_ShowsWeakWarning() {
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
              Obj.<weak_warning descr="Cannot resolve member 'NonExistent'">NonExistent</weak_warning>;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting(true, false, true)
    }

    @Test
    fun testUnresolvedPropertyInChain() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TInner = class
                function GetValue: Integer;
              end;
              TMyClass = class
                function Inner: TInner;
              end;
            implementation
            function TInner.GetValue: Integer; begin Result := 0; end;
            function TMyClass.Inner: TInner; begin Result := nil; end;
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
              Obj.Inner.<weak_warning descr="Cannot resolve member 'Missing'">Missing</weak_warning>;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting(true, false, true)
    }

    // ==================== No-error cases ====================

    @Test
    fun testValidMemberAccess_NoError() {
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

    @Test
    fun testCascadingFailure_NoError() {
        // When the qualifier itself is unresolved, don't flag the member
        val mainText = """
            unit Main;
            interface
            implementation
            procedure Test;
            begin
              UnknownObj.Anything;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testUnitQualifiedAccess_NoError() {
        myFixture.configureByText("MyUtils.pas", """
            unit MyUtils;
            interface
            procedure DoSomething;
            implementation
            procedure DoSomething; begin end;
            end.
        """.trimIndent())

        val mainText = """
            unit Main;
            interface
            uses MyUtils;
            implementation
            procedure Test;
            begin
              MyUtils.DoSomething;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testValidChainedAccess_NoError() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TInner = class
                function GetValue: Integer;
              end;
              TMyClass = class
                function Inner: TInner;
              end;
            implementation
            function TInner.GetValue: Integer; begin Result := 0; end;
            function TMyClass.Inner: TInner; begin Result := nil; end;
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
              Obj.Inner.GetValue;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }

    @Test
    fun testBuiltInMember_NoError() {
        // Built-in methods like Free (from TObject) should not be flagged
        val mainText = """
            unit Main;
            interface
            implementation
            procedure Test(Obj: TObject);
            begin
              Obj.Free;
            end;
            end.
        """.trimIndent()
        myFixture.configureByText("Main.pas", mainText)
        myFixture.checkHighlighting()
    }
}
