package nl.akiar.pascal.resolution

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine

/**
 * Reproduces the user-reported chain `LOrders.Value.ItemsById[...]` where:
 *   - `LOrders` is a local typed as `TShared<TEoOrdersList>` (generic container)
 *   - `Value: T` on `TShared<T>` substitutes to `TEoOrdersList`
 *   - `ItemsById` is an indexed property defined on a generic ANCESTOR `TListGeneric<T>`
 *
 * The doc-provider hover for `Value` shows the substituted type correctly, but the
 * full chain resolution returns null for `ItemsById`, indicating that the
 * substituted type is computed yet not used for the next-step member lookup
 * (or that the ancestor traversal misses the inherited indexed property).
 */
class SharedValueIndexedPropertyChainTest : BasePlatformTestCase() {

    fun testItemsByIdResolvesWhenSubstitutedTypeHasForwardDecl() {
        // Real-world repro: TEoOrderMxList has a forward declaration in the SAME unit before
        // its full definition (UEoMxClasses.pas lines 4955 and 4963). When the chain
        // substitutes T -> "TEoOrderMxList" and then resolves the type via PascalTypeIndex,
        // the forward decl can be returned first, breaking ancestor lookup for ItemsById.
        myFixture.configureByText("UEoBase.pas", """
            unit UEoBase;
            interface
            type
              TEoOrder = class
              public
                Id: Integer;
              end;

              TListGeneric<T> = class
              public
                function GetItem(AId: Integer): T;
                property ItemsById[AId: Integer]: T read GetItem;
              end;

              // forward declaration BEFORE the full one — exact pattern from UEoMxClasses.pas
              TEoOrderMxList = class;

              TEoOrderMxList = class(TListGeneric<TEoOrder>)
              end;

              TShared<T> = record
              private
                fValue: T;
              public
                property Value: T read fValue;
              end;
            implementation
            function TListGeneric<T>.GetItem(AId: Integer): T; begin end;
            end.
        """.trimIndent())

        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UEoBase;
            implementation
            procedure DoTest;
            var
              LOrders: TShared<TEoOrderMxList>;
              LOrder: TEoOrder;
            begin
              LOrder := LOrders.Value.ItemsById[1];
            end;
            end.
        """.trimIndent())

        val itemsByIdLeaf = main.text.indexOf("ItemsById").let { off ->
            main.findElementAt(off + 1)!!
        }
        val chainResult = MemberChainResolver.resolveChain(itemsByIdLeaf)

        val resolved = chainResult.resolvedElements
        val summary = resolved.map { it?.javaClass?.simpleName ?: "<null>" }
        assertNotNull("LOrders must resolve, got $summary", resolved[0])
        assertNotNull("Value must resolve, got $summary", resolved[1])
        assertNotNull(
            "ItemsById must resolve despite TEoOrderMxList having a forward decl in its unit, " +
                "got $summary",
            resolved[2]
        )
    }

    fun testItemsByIdResolvesThroughSharedRecordValueAndGenericAncestor() {
        // Spring4D's TShared<T> is a RECORD (smart pointer), not a class. The previous
        // chain test used a class; this variant matches the real-world scenario.
        myFixture.configureByText("UEoBase.pas", """
            unit UEoBase;
            interface
            type
              TEoOrder = class
              public
                Id: Integer;
              end;

              TListGeneric<T> = class
              public
                function GetItem(AId: Integer): T;
                property ItemsById[AId: Integer]: T read GetItem;
              end;

              TEoOrdersList = class(TListGeneric<TEoOrder>)
              end;

              TShared<T> = record
              private
                fValue: T;
                function GetValue: T;
              public
                property Value: T read GetValue;
              end;
            implementation
            function TListGeneric<T>.GetItem(AId: Integer): T; begin end;
            function TShared<T>.GetValue: T; begin Result := fValue; end;
            end.
        """.trimIndent())

        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UEoBase;
            implementation
            procedure DoTest;
            var
              LOrders: TShared<TEoOrdersList>;
              LOrder: TEoOrder;
            begin
              LOrder := LOrders.Value.ItemsById[1];
            end;
            end.
        """.trimIndent())

        val itemsByIdLeaf = main.text.indexOf("ItemsById").let { off ->
            main.findElementAt(off + 1)!!
        }
        val chainResult = MemberChainResolver.resolveChain(itemsByIdLeaf)

        val texts = chainResult.chainElements.map { it.text }
        assertEquals(listOf("LOrders", "Value", "ItemsById"), texts)

        val resolved = chainResult.resolvedElements
        assertNotNull("LOrders must resolve", resolved[0])
        assertNotNull("Value must resolve on TShared<T> record", resolved[1])
        assertNotNull(
            "ItemsById must resolve via TEoOrdersList -> TListGeneric<TEoOrder> — got " +
                resolved.map { it?.javaClass?.simpleName ?: "<null>" },
            resolved[2]
        )
    }

    fun testItemsByIdResolvesThroughSharedValueAndGenericAncestor() {
        myFixture.configureByText("UEoBase.pas", """
            unit UEoBase;
            interface
            type
              TEoOrder = class
              public
                Id: Integer;
              end;

              TListGeneric<T> = class
              public
                function GetItem(AId: Integer): T;
                property ItemsById[AId: Integer]: T read GetItem;
              end;

              TEoOrdersList = class(TListGeneric<TEoOrder>)
              end;

              TShared<T> = class
              public
                function GetValue: T;
                property Value: T read GetValue;
              end;
            implementation
            function TListGeneric<T>.GetItem(AId: Integer): T; begin end;
            function TShared<T>.GetValue: T; begin end;
            end.
        """.trimIndent())

        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UEoBase;
            implementation
            procedure DoTest;
            var
              LOrders: TShared<TEoOrdersList>;
              LOrder: TEoOrder;
            begin
              LOrder := LOrders.Value.ItemsById[1];
            end;
            end.
        """.trimIndent())

        // Locate the ItemsById identifier in Main.pas and collect+resolve the chain.
        val itemsByIdLeaf = main.text.indexOf("ItemsById").let { off ->
            main.findElementAt(off + 1)!!
        }
        val chainResult = MemberChainResolver.resolveChain(itemsByIdLeaf)

        val texts = chainResult.chainElements.map { it.text }
        assertEquals("chain should be LOrders.Value.ItemsById", listOf("LOrders", "Value", "ItemsById"), texts)

        val resolved = chainResult.resolvedElements
        assertNotNull("LOrders must resolve (variable)", resolved[0])
        assertNotNull("Value must resolve (property on TShared)", resolved[1])
        assertNotNull(
            "ItemsById must resolve on TEoOrdersList (inherited from TListGeneric<T>) — " +
                "got allResolved=${resolved.map { it?.javaClass?.simpleName ?: "<null>" }}",
            resolved[2]
        )
        val itemsByIdResolved = resolved[2]
        assertTrue(
            "ItemsById should resolve to a property, got ${itemsByIdResolved!!.javaClass.simpleName}",
            itemsByIdResolved is PascalProperty || itemsByIdResolved is PascalRoutine
        )
    }
}
