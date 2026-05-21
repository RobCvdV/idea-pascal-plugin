package nl.akiar.pascal.resolution

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalRoutine

/**
 * At a use site `LRide.FillEoRide(...)` where FillEoRide is declared on a
 * `class helper for TRide` and the helper unit is in `uses`, the call must
 * resolve to the helper's method. Negative case: without `uses`, it must NOT.
 */
class HelperMemberLookupTest : BasePlatformTestCase() {

    fun testHelperMethodResolvesAtCallSiteWhenImported() {
        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                Mileage: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("RideHelpers.pas", """
            unit RideHelpers;
            interface
            uses Rides;
            type
              TRideHelper = class helper for TRide
              public
                procedure FillEoRide(AStuff: Integer);
              end;
            implementation
            procedure TRideHelper.FillEoRide(AStuff: Integer); begin end;
            end.
        """.trimIndent())

        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Rides, RideHelpers;
            implementation
            procedure DoTest;
            var
              LRide: TRide;
            begin
              LRide.FillEo<caret>Ride(42);
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(main)!!
        val resolved = resolveViaReferences(element)
        assertNotNull("LRide.FillEoRide should resolve to TRideHelper.FillEoRide", resolved)
        assertTrue(resolved is PascalRoutine)
        assertEquals("FillEoRide", (resolved as PascalRoutine).name)
        assertEquals("TRideHelper", resolved.containingClassName)
    }

    fun testHelperMethodOnAncestorAppliesToDescendant() {
        // helper for TBase should also resolve on TDerived
        myFixture.configureByText("Base.pas", """
            unit Base;
            interface
            type
              TBase = class
              end;
              TDerived = class(TBase)
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("BaseHelpers.pas", """
            unit BaseHelpers;
            interface
            uses Base;
            type
              TBaseHelper = class helper for TBase
              public
                procedure DescendantsSeeMe;
              end;
            implementation
            procedure TBaseHelper.DescendantsSeeMe; begin end;
            end.
        """.trimIndent())

        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Base, BaseHelpers;
            implementation
            procedure DoTest;
            var
              LDer: TDerived;
            begin
              LDer.Descendants<caret>SeeMe;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(main)!!
        val resolved = resolveViaReferences(element)
        assertNotNull("Helper for TBase should resolve on TDerived (ancestor helper)", resolved)
        assertTrue(resolved is PascalRoutine)
        assertEquals("DescendantsSeeMe", (resolved as PascalRoutine).name)
    }

    fun testHelperMethodNotResolvedWhenUnitMissing() {
        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("RideHelpers.pas", """
            unit RideHelpers;
            interface
            uses Rides;
            type
              TRideHelper = class helper for TRide
              public
                procedure FillEoRide(AStuff: Integer);
              end;
            implementation
            procedure TRideHelper.FillEoRide(AStuff: Integer); begin end;
            end.
        """.trimIndent())

        // Note: Main does NOT use RideHelpers.
        val main = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Rides;
            implementation
            procedure DoTest;
            var
              LRide: TRide;
            begin
              LRide.FillEo<caret>Ride(42);
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(main)!!
        val resolved = resolveViaReferences(element)
        assertNull("FillEoRide should not resolve when RideHelpers is not in uses, got " +
            "${resolved?.javaClass?.simpleName}", resolved)
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
