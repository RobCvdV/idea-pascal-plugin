package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for TYPE_REFERENCE PSI element creation and type detection.
 * Verifies that the parser creates TYPE_REFERENCE PSI elements for type references
 * in variable declarations, parameters, return types, fields, etc.
 *
 * This tests the full parser-level implementation where TypeReferenceNode from
 * sonar-delphi AST is mapped to PascalElementTypes.TYPE_REFERENCE, creating
 * PascalTypeReferenceElement instances with proper kind detection (SIMPLE_TYPE,
 * USER_TYPE, KEYWORD_TYPE).
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
        assertNotNull("File should parse", psiFile)

        // Verify TYPE_REFERENCE PSI element is created
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)
        assertTrue("Should have at least one TYPE_REFERENCE element", typeRefs.isNotEmpty())

        // Verify it references "Integer"
        val integerRef = typeRefs.firstOrNull { it.getReferencedTypeName() == "Integer" }
        assertNotNull("Should find TYPE_REFERENCE for Integer", integerRef)

        // Verify kind is SIMPLE_TYPE
        assertEquals("Integer should be SIMPLE_TYPE kind",
                    nl.akiar.pascal.psi.TypeReferenceKind.SIMPLE_TYPE,
                    integerRef?.getKind())
    }

    @Test
    fun testMultipleSimpleTypes() {
        val code = """
            unit Test;
            interface
            var 
              X: Integer;
              Y: Cardinal;
              Z: Boolean;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // Verify TYPE_REFERENCE elements are created for each type
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)
        assertTrue("Should have at least 3 TYPE_REFERENCE elements", typeRefs.size >= 3)

        // Verify all three types are present
        val typeNames = typeRefs.map { it.getReferencedTypeName() }.toSet()
        assertTrue("Should have Integer", typeNames.contains("Integer"))
        assertTrue("Should have Cardinal", typeNames.contains("Cardinal"))
        assertTrue("Should have Boolean", typeNames.contains("Boolean"))
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

        // Verify TYPE_REFERENCE element is created for user type
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)
        val tmyClassRef = typeRefs.firstOrNull { it.getReferencedTypeName() == "TMyClass" }
        assertNotNull("Should find TYPE_REFERENCE for TMyClass", tmyClassRef)

        // Verify kind is USER_TYPE (not SIMPLE_TYPE)
        assertEquals("TMyClass should be USER_TYPE kind",
                    nl.akiar.pascal.psi.TypeReferenceKind.USER_TYPE,
                    tmyClassRef?.getKind())
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

        // NOTE: Keyword types like "string", "array", "set", "file" do NOT get TYPE_REFERENCE
        // elements from the parser because sonar-delphi treats them as grammar keywords
        // rather than type identifiers. They are handled by the context-based fallback in
        // the annotator (isTypeReferenceContext + annotateTypeReferenceIdentifier).

        // Verify the built-in type registry recognizes it
        assertTrue("string should be recognized as keyword type",
                  PascalBuiltInTypes.isKeywordType("string"))

        // The TYPE_REFERENCE count will be 0 for keyword-only code
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)
        assertEquals("Keyword types don't get TYPE_REFERENCE from parser", 0, typeRefs.size)
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

        // Verify TYPE_REFERENCE elements are created for parameter types
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)
        val typeNames = typeRefs.map { it.getReferencedTypeName() }.toSet()

        assertTrue("Should have Integer type reference", typeNames.contains("Integer"))
        assertTrue("Should have TObject type reference", typeNames.contains("TObject"))

        // Verify at least one Integer reference is SIMPLE_TYPE
        val integerRefs = typeRefs.filter { it.getReferencedTypeName() == "Integer" }
        assertTrue("Should have Integer references", integerRefs.isNotEmpty())
        assertTrue("Integer should be SIMPLE_TYPE",
                  integerRefs.any { it.getKind() == nl.akiar.pascal.psi.TypeReferenceKind.SIMPLE_TYPE })
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

        // Verify TYPE_REFERENCE element is created for return type
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)
        val integerRefs = typeRefs.filter { it.getReferencedTypeName() == "Integer" }
        assertTrue("Should have Integer type references for return types", integerRefs.isNotEmpty())
        assertTrue("Integer should be SIMPLE_TYPE",
                  integerRefs.all { it.getKind() == nl.akiar.pascal.psi.TypeReferenceKind.SIMPLE_TYPE })
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
                FName: AnsiString;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        // Verify TYPE_REFERENCE elements are created for field types
        val typeRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       nl.akiar.pascal.psi.impl.PascalTypeReferenceElement::class.java)
        val typeNames = typeRefs.map { it.getReferencedTypeName() }.toSet()

        assertTrue("Should have Integer type reference", typeNames.contains("Integer"))
        assertTrue("Should have AnsiString type reference", typeNames.contains("AnsiString"))

        // Verify kinds
        val integerRef = typeRefs.firstOrNull { it.getReferencedTypeName() == "Integer" }
        assertEquals("Integer should be SIMPLE_TYPE",
                    nl.akiar.pascal.psi.TypeReferenceKind.SIMPLE_TYPE,
                    integerRef?.getKind())

        val ansiStringRef = typeRefs.firstOrNull { it.getReferencedTypeName() == "AnsiString" }
        assertEquals("AnsiString should be SIMPLE_TYPE",
                    nl.akiar.pascal.psi.TypeReferenceKind.SIMPLE_TYPE,
                    ansiStringRef?.getKind())
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
