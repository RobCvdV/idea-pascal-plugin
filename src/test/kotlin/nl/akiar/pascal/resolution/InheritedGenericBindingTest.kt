package nl.akiar.pascal.resolution

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalVariableDefinition

/**
 * When a class inherits from a generic ancestor with concrete type arguments
 * (e.g., `TEoOrderMxList = class(TListGeneric<TEoOrderMx>)`), members inherited
 * from that ancestor must have their generic return type substituted according
 * to the inheritance binding, not left as the raw type-parameter name.
 *
 * `LOrder := LOrders.ItemsById[1]` should infer `LOrder: TEoOrderMx`, not
 * `LOrder: TGenericClassOfItem` (the raw type parameter declared on TListGeneric).
 */
class InheritedGenericBindingTest : BasePlatformTestCase() {

    fun testInferredTypeAppliesAncestorGenericBinding() {
        myFixture.configureByText("UEoBase.pas", """
            unit UEoBase;
            interface
            type
              TEoOrderMx = class
              public
                Id: Integer;
              end;

              TListGeneric<TGenericClassOfItem> = class
              public
                function GetItem(AId: Integer): TGenericClassOfItem;
                property ItemsById[AId: Integer]: TGenericClassOfItem read GetItem;
              end;

              TEoOrderMxList = class(TListGeneric<TEoOrderMx>)
              end;
            implementation
            function TListGeneric<TGenericClassOfItem>.GetItem(AId: Integer): TGenericClassOfItem; begin end;
            end.
        """.trimIndent())

        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses UEoBase;
            implementation
            procedure DoTest;
            var
              LOrders: TEoOrderMxList;
            begin
              var LOrder := LOrders.ItemsById[1];
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(main, PascalVariableDefinition::class.java)
        val lOrder = vars.firstOrNull { it.name == "LOrder" }
        assertNotNull("LOrder inline var should be found", lOrder)

        val inferred = MemberChainResolver.getInferredTypeName(lOrder!!, main)
        assertEquals(
            "LOrder should infer to TEoOrderMx (substituted from TGenericClassOfItem via " +
                "TEoOrderMxList's inheritance of TListGeneric<TEoOrderMx>)",
            "TEoOrderMx",
            inferred
        )
    }
}
