package nl.akiar.pascal.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration tests for autocompletion through the real IDE completion system.
 *
 * These tests verify that the completion popup shows correct items by using
 * myFixture.complete(CompletionType.BASIC) and checking lookupElementStrings.
 *
 * NOTE: Variable-based member completion (Obj.<caret> where Obj is a local variable)
 * requires the variable to be in the stub index. In the test framework, the completion
 * copy file may not be indexed. Tests that exercise this path use global variables
 * from separate units or document the gap.
 */
class PascalCompletionIntegrationTest : BasePlatformTestCase() {

    // ==================== Static Member Access Completion ====================

    fun testStaticAccessCompletionOffersClassMethods() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                class function Create: TMyClass;
                procedure DoWork;
                function Calculate: Integer;
              end;
            implementation
            class function TMyClass.Create: TMyClass; begin Result := nil; end;
            procedure TMyClass.DoWork; begin end;
            function TMyClass.Calculate: Integer; begin Result := 0; end;
            end.
        """.trimIndent())

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              TMyClass.<caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Completion should return results", names)
        assertTrue("Should offer 'Create'", names!!.any { it.equals("Create", ignoreCase = true) })
        assertTrue("Should offer 'DoWork'", names.any { it.equals("DoWork", ignoreCase = true) })
        assertTrue("Should offer 'Calculate'", names.any { it.equals("Calculate", ignoreCase = true) })
    }

    fun testStaticAccessOffersProperties() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              private
                FCaption: String;
              public
                property Caption: String read FCaption;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              TMyClass.<caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Completion should return results", names)
        assertTrue("Should offer 'Caption' property", names!!.any { it.equals("Caption", ignoreCase = true) })
    }

    fun testStaticAccessOffersFields() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                Value: Integer;
                Description: String;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              TMyClass.<caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Completion should return results", names)
        assertTrue("Should offer 'Value' field", names!!.any { it.equals("Value", ignoreCase = true) })
        assertTrue("Should offer 'Description' field", names.any { it.equals("Description", ignoreCase = true) })
    }

    // ==================== Inherited Member Completion ====================

    fun testStaticAccessOffersInheritedMembers() {
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

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              TDerived.<caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Completion should return results", names)
        assertTrue("Should offer 'DerivedMethod'", names!!.any { it.equals("DerivedMethod", ignoreCase = true) })
        assertTrue("Should offer inherited 'BaseMethod'", names.any { it.equals("BaseMethod", ignoreCase = true) })
    }

    // ==================== Enum Value Completion ====================

    fun testEnumTypeGetMembersIncludesValues() {
        myFixture.configureByText("EnumTypes.pas", """
            unit EnumTypes;
            interface
            type
              TAlignment = (taLeftJustify, taRightJustify, taCenter);
            implementation
            end.
        """.trimIndent())

        val typeDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            myFixture.file, nl.akiar.pascal.psi.PascalTypeDefinition::class.java)
        val alignment = typeDefs.find { it.name.equals("TAlignment", ignoreCase = true) }
        assertNotNull("Should find TAlignment type", alignment)
        assertEquals("Should be ENUM kind", nl.akiar.pascal.psi.TypeKind.ENUM, alignment!!.typeKind)

        val members = alignment.getMembers(false)
        val memberNames = members.map { it.text }
        assertTrue("getMembers() should include taLeftJustify, got: $memberNames",
            memberNames.any { it.equals("taLeftJustify", ignoreCase = true) })
        assertTrue("getMembers() should include taCenter, got: $memberNames",
            memberNames.any { it.equals("taCenter", ignoreCase = true) })
        assertEquals("Should have exactly 3 enum values", 3, members.size)
    }

    fun testEnumValueCompletionAfterDot() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TAlignment = (taLeftJustify, taRightJustify, taCenter);
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              TAlignment.<caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Enum completion should return results", names)
        assertTrue("Should offer 'taLeftJustify', got: $names",
            names!!.any { it.equals("taLeftJustify", ignoreCase = true) })
        assertTrue("Should offer 'taCenter'",
            names.any { it.equals("taCenter", ignoreCase = true) })
    }

    // ==================== Function Return Type Completion ====================

    fun testFunctionReturnTypeCompletion() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TConfig = class
              public
                Title: String;
                Version: Integer;
              end;
            function GetConfig: TConfig;
            implementation
            function GetConfig: TConfig;
            begin
              Result := nil;
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              GetConfig.<caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Function return type completion should return results", names)
        assertTrue("Should offer 'Title'", names!!.any { it.equals("Title", ignoreCase = true) })
        assertTrue("Should offer 'Version'", names.any { it.equals("Version", ignoreCase = true) })
    }

    // ==================== Deep Chain Completion ====================

    fun testDeepChainCompletionViaStaticAccess() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TInner = class
              public
                Value: String;
              end;
              TOuter = class
              public
                class function GetInner: TInner;
              end;
            implementation
            class function TOuter.GetInner: TInner; begin Result := nil; end;
            end.
        """.trimIndent())

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            begin
              TOuter.GetInner.<caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Deep chain completion should return results", names)
        assertTrue("Should offer 'Value' from TInner", names!!.any { it.equals("Value", ignoreCase = true) })
    }

    // ==================== Unit-Qualified Completion ====================

    fun testUnitQualifiedCompletion() {
        myFixture.configureByText("MyUnit.pas", """
            unit MyUnit;
            interface
            type
              TMyType = class
              end;
            procedure MyProc;
            implementation
            procedure MyProc; begin end;
            end.
        """.trimIndent())

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses MyUnit;
            implementation
            procedure Test;
            begin
              MyUnit.<caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Unit-qualified completion should return results", names)
        assertTrue("Should offer 'TMyType'", names!!.any { it.equals("TMyType", ignoreCase = true) })
        assertTrue("Should offer 'MyProc'", names.any { it.equals("MyProc", ignoreCase = true) })
    }

    // ==================== Identifier Completion ====================

    fun testIdentifierCompletionOffersKeywords() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Test;
            begin
              <caret>
            end;
            end.
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings

        assertNotNull("Identifier completion should return results", names)
        // Should at least offer keywords in a routine body
        assertTrue("Should offer 'if' keyword", names!!.any { it.equals("if", ignoreCase = true) })
        assertTrue("Should offer 'begin' keyword", names.any { it.equals("begin", ignoreCase = true) })
    }
}
