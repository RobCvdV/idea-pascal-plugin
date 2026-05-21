package nl.akiar.pascal.quickfix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class AddUnitToUsesClauseFixTest : BasePlatformTestCase() {

    /** Helper: pull just the intention texts for inspection. */
    private fun availableIntentions(): List<String> =
        myFixture.availableIntentions.map { it.text }

    @Test
    fun testAnnotatorProducesErrorForUnresolvedType() {
        // Sanity: does the existing scope annotator even fire on TWidget when
        // it's defined in another unit that isn't in the uses clause?
        myFixture.configureByText("OtherUnit.pas", """
            unit OtherUnit;
            interface
            type
              TWidget = class
              end;
            implementation
            end.
        """.trimIndent())
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TFoo = class
                FW: T<caret>Widget;
              end;
            implementation
            end.
        """.trimIndent())
        val info = myFixture.doHighlighting()
        val errors = info.filter { it.severity.myVal >= com.intellij.lang.annotation.HighlightSeverity.ERROR.myVal }
        // Print whatever we see for diagnostics.
        println("HIGHLIGHTS=${info.map { "${it.severity.name}: ${it.description}" }}")
        assertTrue("Expected an ERROR-level annotation. All highlights: ${info.map { it.severity.name to it.description }}",
            errors.isNotEmpty())
    }

    @Test
    fun testQuickFixOffered_ForUnresolvedTypeWithKnownUnit() {
        myFixture.configureByText("OtherUnit.pas", """
            unit OtherUnit;
            interface
            type
              TWidget = class
              end;
            implementation
            end.
        """.trimIndent())
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TFoo = class
                FW: T<caret>Widget;
              end;
            implementation
            end.
        """.trimIndent())
        val info = myFixture.doHighlighting()
        val intentions = availableIntentions()
        assertTrue("Expected 'Add unit OtherUnit' fix.\n  highlights=${info.map { "${it.severity}:${it.description}" }}\n  intentions=$intentions",
            intentions.any { it.contains("OtherUnit", ignoreCase = true) && it.contains("uses", ignoreCase = true) })
    }

    @Test
    fun testApplyingFix_InsertsIntoImplementationSection() {
        myFixture.configureByText("OtherUnit.pas", """
            unit OtherUnit;
            interface
            type
              TWidget = class
              end;
            implementation
            end.
        """.trimIndent())
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            type
              TInternal = class
                FW: T<caret>Widget;
              end;
            end.
        """.trimIndent())
        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions
            .firstOrNull { it.text.contains("OtherUnit", ignoreCase = true) }
        assertNotNull("Quick-fix not found among available intentions", intention)
        myFixture.launchAction(intention!!)
        val newText = myFixture.file.text
        assertTrue("Expected uses clause containing OtherUnit, got:\n$newText",
            newText.contains(Regex("uses\\s+OtherUnit", RegexOption.IGNORE_CASE)) ||
            newText.contains(Regex("uses[^;]*,\\s*OtherUnit", RegexOption.IGNORE_CASE)))
    }

    @Test
    fun testApplyingFix_AppendsToExistingUses() {
        myFixture.configureByText("OtherUnit.pas", """
            unit OtherUnit;
            interface
            type
              TWidget = class
              end;
            implementation
            end.
        """.trimIndent())
        myFixture.configureByText("Already.pas", """
            unit Already;
            interface
            implementation
            end.
        """.trimIndent())
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses
              Already;
            type
              TFoo = class
                FW: T<caret>Widget;
              end;
            implementation
            end.
        """.trimIndent())
        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions
            .firstOrNull { it.text.contains("OtherUnit", ignoreCase = true) }
        assertNotNull(intention)
        myFixture.launchAction(intention!!)
        val newText = myFixture.file.text
        assertTrue("Expected appended uses, got:\n$newText",
            newText.contains(Regex("uses\\s+Already\\s*,\\s*OtherUnit", RegexOption.IGNORE_CASE)))
    }

    @Test
    fun testApplyingFix_InsertsIntoInterfaceSection_WhenReferencedThere() {
        myFixture.configureByText("OtherUnit.pas", """
            unit OtherUnit;
            interface
            type
              TWidget = class
              end;
            implementation
            end.
        """.trimIndent())
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TUseful = class
                FW: <caret>TWidget;
              end;
            implementation
            end.
        """.trimIndent())
        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions
            .firstOrNull { it.text.contains("OtherUnit", ignoreCase = true) }
        assertNotNull(intention)
        myFixture.launchAction(intention!!)
        val newText = myFixture.file.text
        // The new uses must come BEFORE 'implementation' (interface section).
        val lower = newText.lowercase()
        val usesIdx = lower.indexOf("uses")
        val implIdx = lower.indexOf("implementation")
        assertTrue("Expected interface-section uses, got:\n$newText",
            usesIdx in 0 until implIdx)
        assertTrue("Expected unit name in text:\n$newText",
            lower.contains("otherunit"))
    }

    @Test
    fun testNoFix_WhenUnitOnlyExistsInBuiltinSystem() {
        // The builtin System.pas ships TObject etc. We should NOT offer to add it.
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure Run;
            var
              O: <caret>TObject;
            begin
            end;
            end.
        """.trimIndent())
        myFixture.doHighlighting()
        val intentions = availableIntentions()
        assertTrue("Should not offer to add System; got: $intentions",
            intentions.none { it.contains("System", ignoreCase = true) && it.contains("uses", ignoreCase = true) })
    }
}
