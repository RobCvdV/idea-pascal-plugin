package nl.akiar.pascal.resolution

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.stubs.PascalTypeIndex
import org.junit.Test

/**
 * Tests that type disambiguation follows Delphi's "last wins" rule:
 * when multiple units define the same type name, the last unit in the
 * uses clause takes precedence.
 */
class TypeDisambiguationTest : BasePlatformTestCase() {

    @Test
    fun testLastWins_ChainResolvesToLastUnit() {
        // UnitA defines TItem with FieldA
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type
              TItem = class
              public
                FieldA: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        // UnitB defines TItem with FieldB
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            type
              TItem = class
              public
                FieldB: String;
              end;
            implementation
            end.
        """.trimIndent())

        // Main uses UnitA, UnitB — UnitB is last, so TItem should resolve to UnitB's
        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UnitA, UnitB;
            implementation
            procedure Test;
            var
              Item: TItem;
            begin
              Item.FieldB<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find element at caret", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertNotNull("Item should resolve (index 0)", result.resolvedElements.getOrNull(0))
        assertNotNull("FieldB should resolve (index 1) — from UnitB (last in uses)", result.resolvedElements.getOrNull(1))
    }

    @Test
    fun testLastWins_TypeLookupReturnsCorrectOrder() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type
              TItem = class
              public
                FieldA: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            type
              TItem = class
              public
                FieldB: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UnitA, UnitB;
            implementation
            end.
        """.trimIndent())

        val result = PascalTypeIndex.findTypesWithUsesValidation("TItem", mainFile, 0)
        assertTrue("Should find in-scope types", result.inScopeTypes.isNotEmpty())

        // First in-scope type should be from UnitB (last in uses = highest priority)
        val first = result.inScopeTypes.first()
        assertEquals("First in-scope type should be from UnitB (last wins)", "unitb", first.unitName.lowercase())
    }

    @Test
    fun testSameFileWins_OverUsesClause() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type
              TItem = class
              public
                ExternalField: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        // Main defines its own TItem and uses UnitA — same-file should win
        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UnitA;
            type
              TItem = class
              public
                LocalField: String;
              end;
            implementation
            procedure Test;
            var
              Item: TItem;
            begin
              Item.LocalField<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull(element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertNotNull("Item should resolve", result.resolvedElements.getOrNull(0))
        assertNotNull("LocalField should resolve (same-file type wins)", result.resolvedElements.getOrNull(1))
    }

    @Test
    fun testLastWins_TransitiveDepTypeLookup() {
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type
              TItem = class
              public
                FieldA: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            type
              TItem = class
              public
                FieldB: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UnitA, UnitB;
            implementation
            end.
        """.trimIndent())

        val result = PascalTypeIndex.findTypeWithTransitiveDeps("TItem", mainFile, 0)
        assertTrue("Should find in-scope types", result.inScopeTypes.isNotEmpty())

        val first = result.inScopeTypes.first()
        assertEquals("First type should be from UnitB (last wins)", "unitb", first.unitName.lowercase())
    }

    @Test
    fun testLastWins_ReversedUsesOrder() {
        // Same as testLastWins but with reversed uses order — UnitA should now win
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            type
              TItem = class
              public
                FieldA: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            type
              TItem = class
              public
                FieldB: String;
              end;
            implementation
            end.
        """.trimIndent())

        // Uses UnitB first, then UnitA — UnitA is last, should win
        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UnitB, UnitA;
            implementation
            procedure Test;
            var
              Item: TItem;
            begin
              Item.FieldA<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull(element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertNotNull("Item should resolve", result.resolvedElements.getOrNull(0))
        assertNotNull("FieldA should resolve (UnitA is last in uses)", result.resolvedElements.getOrNull(1))
    }

    private fun findIdentifierAtCaret(file: com.intellij.psi.PsiFile): PsiElement? {
        val caretOffset = myFixture.caretOffset
        for (offset in 0..3) {
            if (caretOffset - offset >= 0) {
                val el = file.findElementAt(caretOffset - offset)
                if (el != null && el.node.elementType == PascalTokenTypes.IDENTIFIER) return el
            }
        }
        return null
    }
}
