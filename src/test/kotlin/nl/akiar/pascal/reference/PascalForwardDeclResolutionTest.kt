package nl.akiar.pascal.reference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalTypeDefinition

/**
 * Verifies that when a type has both a forward declaration (`TFoo = class;`)
 * and a full definition (`TFoo = class ... end;`), references to that type
 * resolve to the full definition, not the forward.
 */
class PascalForwardDeclResolutionTest : BasePlatformTestCase() {

    fun testTypeReferenceInVarDeclPrefersFullDefinition() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TFoo = class;

              TFoo = class
              public
                procedure DoWork;
              end;

            procedure Use(AFoo: T<caret>Foo);
            implementation
            procedure Use(AFoo: TFoo); begin end;
            procedure TFoo.DoWork; begin end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret()
        assertNotNull("Should find TFoo identifier at caret", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("TFoo reference should resolve", resolved)
        assertTrue("Should resolve to PascalTypeDefinition, got ${resolved!!.javaClass.simpleName}",
            resolved is PascalTypeDefinition)

        val typeDef = resolved as PascalTypeDefinition
        assertEquals("TFoo", typeDef.name)
        assertFalse("Should resolve to full definition, not forward declaration",
            typeDef.isForwardDeclaration)
    }

    fun testTypeReferenceFallsBackToForwardWhenNoFullDefinition() {
        // If only a forward decl exists, resolve must still return it so the user
        // can navigate somewhere rather than getting "unresolved".
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TFoo = class;

            procedure Use(AFoo: T<caret>Foo);
            implementation
            procedure Use(AFoo: TFoo); begin end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret()
        assertNotNull("Should find TFoo identifier at caret", element)

        val resolved = resolveViaReferences(element!!)
        assertNotNull("Forward-only TFoo reference should still resolve", resolved)
        assertTrue(resolved is PascalTypeDefinition)
        assertTrue("Should resolve to the forward declaration (no full def exists)",
            (resolved as PascalTypeDefinition).isForwardDeclaration)
    }

    private fun findIdentifierAtCaret(): PsiElement? {
        val caretOffset = myFixture.caretOffset
        val file = myFixture.file
        var element = file.findElementAt(caretOffset)
        if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) return element
        for (offset in 1..3) {
            if (caretOffset - offset >= 0) {
                element = file.findElementAt(caretOffset - offset)
                if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) return element
            }
        }
        return null
    }

    private fun resolveViaReferences(element: PsiElement): PsiElement? {
        var refs: Array<PsiReference> = element.references
        if (refs.isEmpty()) {
            refs = ReferenceProvidersRegistry.getReferencesFromProviders(element)
        }
        for (ref in refs) {
            val resolved = ref.resolve()
            if (resolved != null) return resolved
        }
        return null
    }
}
