package nl.akiar.pascal.resolution

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalVariableDefinition

/**
 * Inside `TFoo = class helper for TBar`, `Self` is semantically an instance of
 * `TBar`, so `Self.X` must resolve `X` against `TBar`'s members.
 */
class HelperSelfResolutionTest : BasePlatformTestCase() {

    fun testSelfInClassHelperResolvesToHelpedTypeMember() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TBar = class
              public
                RideStart: Integer;
                procedure DoBar;
              end;

              TBarHelper = class helper for TBar
              public
                procedure UseRideStart;
              end;
            implementation
            procedure TBar.DoBar; begin end;
            procedure TBarHelper.UseRideStart;
            begin
              Self.Ride<caret>Start;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)!!
        val resolved = resolveViaReferences(element)
        assertNotNull("Self.RideStart should resolve via helper Self redirect", resolved)
        assertTrue("Should resolve to a field/property, got ${resolved!!.javaClass.simpleName}",
            resolved is PascalVariableDefinition || resolved is PascalProperty)
    }

    fun testSelfInClassHelperResolvesToHelpedTypeMethod() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TBar = class
              public
                procedure DoBar;
              end;

              TBarHelper = class helper for TBar
              public
                procedure UseDoBar;
              end;
            implementation
            procedure TBar.DoBar; begin end;
            procedure TBarHelper.UseDoBar;
            begin
              Self.Do<caret>Bar;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)!!
        val resolved = resolveViaReferences(element)
        assertNotNull("Self.DoBar should resolve to TBar.DoBar via helper Self redirect", resolved)
        assertTrue(resolved is PascalRoutine)
        assertEquals("DoBar", (resolved as PascalRoutine).name)
    }

    private fun findIdentifierAtCaret(file: com.intellij.psi.PsiFile): PsiElement? {
        val caretOffset = myFixture.caretOffset
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
