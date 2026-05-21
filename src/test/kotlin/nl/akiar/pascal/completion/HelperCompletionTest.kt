package nl.akiar.pascal.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Code completion after `obj.` must include methods/properties declared on any
 * helper that targets the object's type (or one of its ancestors) and whose
 * declaring unit is in scope.
 */
class HelperCompletionTest : BasePlatformTestCase() {

    fun testHelperMembersAppearInCompletion() {
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
                function GetSomething: Integer;
              end;
            implementation
            procedure TRideHelper.FillEoRide(AStuff: Integer); begin end;
            function TRideHelper.GetSomething: Integer; begin Result := 0; end;
            end.
        """.trimIndent())

        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Rides, RideHelpers;
            implementation
            procedure DoTest;
            var
              LRide: TRide;
            begin
              LRide.<caret>
            end;
            end.
        """.trimIndent())

        myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings
        assertNotNull("Completion should return results", names)
        assertTrue("Should include 'Mileage' (own member)",
            names!!.any { it.equals("Mileage", ignoreCase = true) })
        assertTrue("Should include helper method 'FillEoRide'",
            names.any { it.equals("FillEoRide", ignoreCase = true) })
        assertTrue("Should include helper function 'GetSomething'",
            names.any { it.equals("GetSomething", ignoreCase = true) })
    }

    fun testHelperMembersNotInCompletionWhenUnitNotImported() {
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

        // Main does NOT import RideHelpers
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Rides;
            implementation
            procedure DoTest;
            var
              LRide: TRide;
            begin
              LRide.<caret>
            end;
            end.
        """.trimIndent())

        myFixture.complete(CompletionType.BASIC)
        val names = myFixture.lookupElementStrings
        assertTrue("Should still include own member",
            names != null && names.any { it.equals("Mileage", ignoreCase = true) })
        assertFalse("Should NOT include helper method when its unit is not imported",
            names!!.any { it.equals("FillEoRide", ignoreCase = true) })
    }
}
