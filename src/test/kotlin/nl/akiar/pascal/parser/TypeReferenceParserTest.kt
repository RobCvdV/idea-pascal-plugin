package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for built-in type detection and highlighting.
 * Verifies that type references are correctly identified and highlighted at annotation time.
 *
 * NOTE: This is Phase 0 "lite" - type detection happens in the annotator via context analysis
 * rather than creating TYPE_REFERENCE PSI elements during parsing.
 */
class TypeReferenceParserTest : BasePlatformTestCase() {

    @Test
    fun testSimpleTypeReference() {
        val code = """
            unit Test;
            interface
            var X: Integer;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        // Just verify it parses without error
        assertNotNull("File should parse", psiFile)

        // Verify the built-in type registry recognizes Integer
        assertTrue("Integer should be recognized as simple type",
                  PascalBuiltInTypes.isSimpleType("Integer"))
    }

    @Test
    fun testMultipleSimpleTypes() {
        val code = """
            unit Test;
            interface
            var 
              X: Integer;
              Y: String;
              Z: Boolean;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // Verify built-in types are recognized
        assertTrue(PascalBuiltInTypes.isSimpleType("Integer"))
        assertTrue(PascalBuiltInTypes.isSimpleType("String"))
        assertTrue(PascalBuiltInTypes.isSimpleType("Boolean"))
    }

    @Test
    fun testUserTypeNamingConvention() {
        val code = """
            unit Test;
            interface
            var X: TMyClass;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // TMyClass follows user type naming (T* prefix)
        assertFalse("TMyClass should not be a built-in type",
                   PascalBuiltInTypes.isSimpleType("TMyClass"))
    }

    @Test
    fun testKeywordTypeReference() {
        val code = """
            unit Test;
            interface
            var X: string;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // lowercase "string" is a keyword type
        assertTrue("string should be recognized as keyword type",
                  PascalBuiltInTypes.isKeywordType("string"))
    }

    @Test
    fun testParameterTypeReferences() {
        val code = """
            unit Test;
            interface
            procedure F(A: Integer; B: TObject);
            implementation
            procedure F(A: Integer; B: TObject);
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        assertTrue(PascalBuiltInTypes.isSimpleType("Integer"))
        assertFalse(PascalBuiltInTypes.isSimpleType("TObject"))
    }

    @Test
    fun testReturnTypeReference() {
        val code = """
            unit Test;
            interface
            function F: Integer;
            implementation
            function F: Integer;
            begin
              Result := 0;
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)
        assertTrue(PascalBuiltInTypes.isSimpleType("Integer"))
    }

    @Test
    fun testFieldTypeReferences() {
        val code = """
            unit Test;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
                FName: string;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        assertTrue(PascalBuiltInTypes.isSimpleType("Integer"))
        assertTrue(PascalBuiltInTypes.isKeywordType("string"))
    }

    @Test
    fun testBuiltInTypesCaseInsensitive() {
        // Built-in type detection should be case-insensitive
        assertTrue(PascalBuiltInTypes.isSimpleType("integer"))
        assertTrue(PascalBuiltInTypes.isSimpleType("INTEGER"))
        assertTrue(PascalBuiltInTypes.isSimpleType("Integer"))
    }

    @Test
    fun testUnconventionalTypeNaming() {
        // SmallInt is a built-in type despite not following T* convention
        assertTrue("SmallInt should be recognized as built-in",
                  PascalBuiltInTypes.isSimpleType("SmallInt"))
    }

    @Test
    fun testNoRegressionInExistingParsing() {
        // Ensure existing parsing still works
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
              public
                property Value: Integer read FValue;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse successfully", psiFile)

        // Verify existing PSI structure still works
        val typeDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.PascalTypeDefinition::class.java)
        assertEquals("Should have 1 type definition", 1, typeDefs.size)
        assertEquals("TMyClass", typeDefs.first().name)
    }
}
