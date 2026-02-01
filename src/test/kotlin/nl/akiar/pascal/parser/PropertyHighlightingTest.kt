package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalSyntaxHighlighter
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalVariableDefinition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.annotation.HighlightSeverity
import org.junit.Test

/**
 * Tests for property getter/setter highlighting.
 * Verifies that:
 * 1. Property specifier identifiers (after read/write) don't show error highlighting
 * 2. Getter methods are highlighted as METHOD_CALL
 * 3. Field references are highlighted as VAR_FIELD
 */
class PropertyHighlightingTest : BasePlatformTestCase() {

    private val propertyCode = """
        unit TestUnit;
        interface
        type
          TBits = class
          private
            FSize: Integer;
            function GetBit(Index: Integer): Boolean;
            procedure SetBit(Index: Integer; Value: Boolean);
            procedure SetSize(Value: Integer);
          public
            property Bits[Index: Integer]: Boolean read GetBit write SetBit; default;
            property Size: Integer read FSize write SetSize;
          end;
        implementation
        end.
    """.trimIndent()

    @Test
    fun testPropertiesAreParsed() {
        val psiFile = myFixture.configureByText("TestUnit.pas", propertyCode)

        val properties = PsiTreeUtil.findChildrenOfType(psiFile, PascalProperty::class.java)

        assertEquals("Should find 2 properties", 2, properties.size)

        val propertyNames = properties.mapNotNull { it.name }.map { it.lowercase() }
        assertTrue("Should find 'Bits' property", propertyNames.contains("bits"))
        assertTrue("Should find 'Size' property", propertyNames.contains("size"))
    }

    @Test
    fun testPropertyReadSpecifier() {
        val psiFile = myFixture.configureByText("TestUnit.pas", propertyCode)

        val properties = PsiTreeUtil.findChildrenOfType(psiFile, PascalProperty::class.java)
        val bitsProperty = properties.find { it.name?.equals("Bits", ignoreCase = true) == true }
        val sizeProperty = properties.find { it.name?.equals("Size", ignoreCase = true) == true }

        assertNotNull("Should find Bits property", bitsProperty)
        assertNotNull("Should find Size property", sizeProperty)

        assertEquals("Bits read specifier should be GetBit", "GetBit", bitsProperty!!.readSpecifier)
        assertEquals("Size read specifier should be FSize", "FSize", sizeProperty!!.readSpecifier)
    }

    @Test
    fun testPropertyWriteSpecifier() {
        val psiFile = myFixture.configureByText("TestUnit.pas", propertyCode)

        val properties = PsiTreeUtil.findChildrenOfType(psiFile, PascalProperty::class.java)
        val bitsProperty = properties.find { it.name?.equals("Bits", ignoreCase = true) == true }
        val sizeProperty = properties.find { it.name?.equals("Size", ignoreCase = true) == true }

        assertNotNull("Should find Bits property", bitsProperty)
        assertNotNull("Should find Size property", sizeProperty)

        assertEquals("Bits write specifier should be SetBit", "SetBit", bitsProperty!!.writeSpecifier)
        assertEquals("Size write specifier should be SetSize", "SetSize", sizeProperty!!.writeSpecifier)
    }

    @Test
    fun testNoErrorHighlightingOnPropertySpecifiers() {
        myFixture.configureByText("TestUnit.pas", propertyCode)
        val highlights = myFixture.doHighlighting()

        // Find error-level annotations
        val errors = highlights.filter { it.severity == HighlightSeverity.ERROR }

        // Filter to only errors that mention our property specifiers
        val propertySpecifierErrors = errors.filter { highlight ->
            val text = highlight.text ?: ""
            text.equals("GetBit", ignoreCase = true) ||
            text.equals("SetBit", ignoreCase = true) ||
            text.equals("FSize", ignoreCase = true) ||
            text.equals("SetSize", ignoreCase = true)
        }

        assertTrue(
            "Property specifiers should not have error highlighting, but found: ${propertySpecifierErrors.map { it.text }}",
            propertySpecifierErrors.isEmpty()
        )
    }

    @Test
    fun testGetterHighlightedAsMethodCall() {
        myFixture.configureByText("TestUnit.pas", propertyCode)
        val highlights = myFixture.doHighlighting()

        // Find GetBit in the property declaration line
        val propertyLine = propertyCode.indexOf("read GetBit")
        val getBitOffset = propertyCode.indexOf("GetBit", propertyLine)

        val getBitHighlight = highlights.find {
            it.startOffset == getBitOffset &&
            it.forcedTextAttributesKey == PascalSyntaxHighlighter.METHOD_CALL
        }

        assertNotNull("GetBit should be highlighted as METHOD_CALL", getBitHighlight)
    }

    @Test
    fun testFieldHighlightedAsField() {
        myFixture.configureByText("TestUnit.pas", propertyCode)
        val highlights = myFixture.doHighlighting()

        // Find FSize in the property declaration line
        val propertyLine = propertyCode.indexOf("read FSize")
        val fSizeOffset = propertyCode.indexOf("FSize", propertyLine)

        val fSizeHighlight = highlights.find {
            it.startOffset == fSizeOffset &&
            it.forcedTextAttributesKey == PascalSyntaxHighlighter.VAR_FIELD
        }

        assertNotNull("FSize should be highlighted as VAR_FIELD", fSizeHighlight)
    }

    @Test
    fun testSetterHighlightedAsMethodCall() {
        myFixture.configureByText("TestUnit.pas", propertyCode)
        val highlights = myFixture.doHighlighting()

        // Find SetBit in the property declaration line
        val propertyLine = propertyCode.indexOf("write SetBit")
        val setBitOffset = propertyCode.indexOf("SetBit", propertyLine)

        val setBitHighlight = highlights.find {
            it.startOffset == setBitOffset &&
            it.forcedTextAttributesKey == PascalSyntaxHighlighter.METHOD_CALL
        }

        assertNotNull("SetBit should be highlighted as METHOD_CALL", setBitHighlight)
    }

    @Test
    fun testReadOnlyProperty() {
        val readOnlyCode = """
            unit TestUnit;
            interface
            type
              TTest = class
              private
                FValue: Integer;
              public
                property Value: Integer read FValue;
              end;
            implementation
            end.
        """.trimIndent()

        myFixture.configureByText("TestUnit.pas", readOnlyCode)
        val highlights = myFixture.doHighlighting()

        // Should not have error highlighting
        val errors = highlights.filter { it.severity == HighlightSeverity.ERROR }
        val fValueErrors = errors.filter { (it.text ?: "").equals("FValue", ignoreCase = true) }

        assertTrue(
            "Read-only property field should not have error highlighting",
            fValueErrors.isEmpty()
        )
    }
}
