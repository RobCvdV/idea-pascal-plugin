package nl.akiar.pascal.resolution

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalTypeDefinition
import org.junit.Test

/**
 * When a unit forward-declares a class (`TFoo = class;`) and then provides the
 * full definition later, getSuperClass() must return the full definition so
 * ancestor member lookups (chain resolution, inheritance traversal) see the
 * real members. Returning the forward decl dead-ends chain resolution because
 * its member list is empty.
 */
class ForwardDeclSuperClassTest : BasePlatformTestCase() {

    @Test
    fun testGetSuperClassPrefersFullDefSameUnit() {
        val file = myFixture.configureByText("UEoBase.pas", """
            unit UEoBase;
            interface
            type
              // forward declaration appears first
              TListGeneric<T> = class;

              TListGeneric<T> = class
              public
                property ItemsById[AId: Integer]: T read GetItem;
              end;

              TMyList = class(TListGeneric<Integer>)
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val myList = types.first { it.name == "TMyList" }
        val superClass = myList.superClass
        assertNotNull("TMyList.getSuperClass() should resolve", superClass)
        assertEquals("TListGeneric", superClass!!.name)
        assertFalse("getSuperClass() must return the full definition, not the forward decl",
            superClass.isForwardDeclaration)
    }

    @Test
    fun testInheritedPropertyResolvesThroughForwardDeclaredAncestor() {
        // The full bug scenario: ItemsById lives on a generic ancestor that has a
        // forward declaration earlier in the same unit. Before the fix, getSuperClass()
        // returned the empty forward decl and chain resolution dead-ended.
        val file = myFixture.configureByText("UEoBase.pas", """
            unit UEoBase;
            interface
            type
              TListGeneric<T> = class;

              TListGeneric<T> = class
              public
                property ItemsById[AId: Integer]: T read GetItem;
              end;

              TOrderList = class(TListGeneric<Integer>)
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val orderList = types.first { it.name == "TOrderList" }

        // getMembers(true) walks ancestors and must surface ItemsById from the full
        // TListGeneric def. With the bug present, ancestors stop at the empty forward
        // decl and ItemsById is not found.
        val allMembers = orderList.getMembers(true)
        val itemsById = allMembers.filterIsInstance<PascalProperty>().firstOrNull { it.name == "ItemsById" }
        assertNotNull("ItemsById should be reachable through inherited TListGeneric", itemsById)
    }
}
