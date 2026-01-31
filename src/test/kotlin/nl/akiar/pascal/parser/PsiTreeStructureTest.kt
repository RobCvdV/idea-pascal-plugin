package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests to verify that the PSI tree contains specific element types
 * for different Pascal constructs.
 */
class PsiTreeStructureTest : BasePlatformTestCase() {

    private fun findElementTypes(code: String): Set<String> {
        val psiFile = myFixture.configureByText("test.pas", code)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        return debugInfo.lines()
            .filter { it.contains("Pascal") }
            .mapNotNull { line ->
                // Extract element type from lines like "PascalPsiElement(PascalTokenType.ENUM_ELEMENT)"
                val tokenMatch = Regex("""PascalTokenType\.(\w+)""").find(line)
                if (tokenMatch != null) {
                    tokenMatch.groupValues[1]
                } else {
                    // Also check for stub-based types like "PascalVariableDefinition(...)"
                    // These map to: VARIABLE_DEFINITION, TYPE_DEFINITION, ROUTINE_DECLARATION, PROPERTY_DEFINITION
                    when {
                        line.contains("PascalVariableDefinition") -> "VARIABLE_DEFINITION"
                        line.contains("PascalTypeDefinition") -> "TYPE_DEFINITION"
                        line.contains("PascalRoutine") -> "ROUTINE_DECLARATION"
                        line.contains("PascalProperty") -> "PROPERTY_DEFINITION"
                        else -> null
                    }
                }
            }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun countElementType(code: String, elementType: String): Int {
        val psiFile = myFixture.configureByText("test.pas", code)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        return debugInfo.lines().count { it.contains(elementType) }
    }

    // ============================================================================
    // Enum Element Tests
    // ============================================================================

    @Test
    fun testEnumElementsHaveCorrectType() {
        val code = """
            unit TestUnit;
            interface
            type
              TAlignment = (taLeftJustify, taRightJustify, taCenter);
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        assertTrue("Should contain ENUM_TYPE for enum definition", types.contains("ENUM_TYPE"))
        assertTrue("Should contain ENUM_ELEMENT for enum values", types.contains("ENUM_ELEMENT"))

        // Should have 3 enum elements
        val enumElementCount = countElementType(code, "ENUM_ELEMENT")
        assertEquals("Should have 3 ENUM_ELEMENT entries", 3, enumElementCount)
    }

    @Test
    fun testMultipleEnumsHaveCorrectType() {
        val code = """
            unit TestUnit;
            interface
            type
              TColor = (clRed, clGreen, clBlue);
              TSize = (szSmall, szMedium, szLarge);
            implementation
            end.
        """.trimIndent()

        val enumElementCount = countElementType(code, "ENUM_ELEMENT")
        assertEquals("Should have 6 ENUM_ELEMENT entries (3+3)", 6, enumElementCount)
    }

    // ============================================================================
    // Field Definition Tests
    // ============================================================================

    @Test
    fun testRecordFieldsHaveCorrectType() {
        val code = """
            unit TestUnit;
            interface
            type
              TPoint = record
                X: Integer;
                Y: Integer;
              end;
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        assertTrue("Should contain RECORD_TYPE", types.contains("RECORD_TYPE"))
        // Fields use VARIABLE_DEFINITION (stub-based) with variableKind=FIELD
        assertTrue("Should contain VARIABLE_DEFINITION for record fields", types.contains("VARIABLE_DEFINITION"))
    }

    @Test
    fun testClassFieldsHaveCorrectType() {
        val code = """
            unit TestUnit;
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

        val types = findElementTypes(code)
        assertTrue("Should contain CLASS_TYPE", types.contains("CLASS_TYPE"))
        // Fields use VARIABLE_DEFINITION (stub-based) with variableKind=FIELD
        assertTrue("Should contain VARIABLE_DEFINITION for class fields", types.contains("VARIABLE_DEFINITION"))
        assertTrue("Should contain VISIBILITY_SECTION", types.contains("VISIBILITY_SECTION"))
    }

    // ============================================================================
    // Constant Definition Tests
    // ============================================================================

    @Test
    fun testConstantsHaveCorrectType() {
        val code = """
            unit TestUnit;
            interface
            const
              MaxSize = 100;
              DefaultName = 'Test';
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        // Constants use VARIABLE_DEFINITION (stub-based) with variableKind=CONSTANT
        assertTrue("Should contain VARIABLE_DEFINITION for constants", types.contains("VARIABLE_DEFINITION"))

        // Count constant declarations (PascalVariableDefinition)
        val constCount = countElementType(code, "PascalVariableDefinition")
        assertEquals("Should have 2 constant entries", 2, constCount)
    }

    // ============================================================================
    // Routine Tests
    // ============================================================================
    // Note: All routines use stub-based ROUTINE_DECLARATION for proper indexing.
    // The PascalRoutine implementation determines the specific routine type.

    @Test
    fun testStandaloneRoutineType() {
        val code = """
            unit TestUnit;
            interface
            procedure DoSomething;
            function Calculate(X: Integer): Integer;
            implementation
            procedure DoSomething;
            begin
            end;
            function Calculate(X: Integer): Integer;
            begin
              Result := X * 2;
            end;
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        // All routines use stub-based ROUTINE_DECLARATION
        assertTrue("Should contain ROUTINE_DECLARATION for routines",
                   types.contains("ROUTINE_DECLARATION"))
    }

    @Test
    fun testMethodDeclarationType() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                procedure DoSomething;
                function GetValue: Integer;
              end;
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        // All routines (including methods) use stub-based ROUTINE_DECLARATION
        assertTrue("Should contain ROUTINE_DECLARATION for methods",
                   types.contains("ROUTINE_DECLARATION"))
    }

    @Test
    fun testConstructorDestructorTypes() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                constructor Create;
                destructor Destroy; override;
              end;
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        // Constructors and destructors use stub-based ROUTINE_DECLARATION
        assertTrue("Should contain ROUTINE_DECLARATION for constructors/destructors",
                   types.contains("ROUTINE_DECLARATION"))
    }

    @Test
    fun testClassMethodType() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                class function GetInstance: TMyClass;
                class procedure Initialize;
              end;
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        // Class methods use stub-based ROUTINE_DECLARATION
        assertTrue("Should contain ROUTINE_DECLARATION for class methods",
                   types.contains("ROUTINE_DECLARATION"))
    }

    // ============================================================================
    // Scope/Section Tests
    // ============================================================================

    @Test
    fun testRoutineBodyScope() {
        val code = """
            unit TestUnit;
            interface
            implementation
            procedure Test;
            var
              LocalVar: Integer;
            begin
              LocalVar := 1;
            end;
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        assertTrue("Should contain ROUTINE_BODY for procedure body", types.contains("ROUTINE_BODY"))
        // Local vars are still VARIABLE_DEFINITION (stub-based) with variableKind=LOCAL
        assertTrue("Should contain VARIABLE_DEFINITION for local vars", types.contains("VARIABLE_DEFINITION"))
    }

    @Test
    fun testLocalVariablesVsGlobalVariables() {
        val code = """
            unit TestUnit;
            interface
            var
              GlobalVar: Integer;
            implementation
            procedure Test;
            var
              LocalVar: Integer;
            begin
            end;
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        // Both global and local vars use VARIABLE_DEFINITION (stub-based)
        // The variableKind property distinguishes GLOBAL vs LOCAL
        assertTrue("Should contain VARIABLE_DEFINITION for variables",
                   types.contains("VARIABLE_DEFINITION"))
    }

    @Test
    fun testVisibilitySections() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                FPrivate: Integer;
              protected
                FProtected: Integer;
              public
                FPublic: Integer;
              end;
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        assertTrue("Should contain VISIBILITY_SECTION", types.contains("VISIBILITY_SECTION"))

        val visCount = countElementType(code, "VISIBILITY_SECTION")
        assertTrue("Should have multiple VISIBILITY_SECTION entries", visCount >= 3)
    }

    // ============================================================================
    // Type Definition Specificity Tests
    // ============================================================================

    @Test
    fun testClassTypeDefinition() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              end;
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        assertTrue("Should contain CLASS_TYPE", types.contains("CLASS_TYPE"))
    }

    @Test
    fun testRecordTypeDefinition() {
        val code = """
            unit TestUnit;
            interface
            type
              TMyRecord = record
                Field: Integer;
              end;
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        assertTrue("Should contain RECORD_TYPE", types.contains("RECORD_TYPE"))
    }

    @Test
    fun testInterfaceTypeDefinition() {
        val code = """
            unit TestUnit;
            interface
            type
              IMyInterface = interface
                procedure DoSomething;
              end;
            implementation
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        assertTrue("Should contain INTERFACE_TYPE", types.contains("INTERFACE_TYPE"))
    }

    // ============================================================================
    // Unit Structure Tests
    // ============================================================================

    @Test
    fun testUnitSections() {
        val code = """
            unit TestUnit;
            interface
            uses
              System.SysUtils;
            type
              TTest = class
              end;
            implementation
            uses
              System.Classes;
            end.
        """.trimIndent()

        val types = findElementTypes(code)
        assertTrue("Should contain INTERFACE_SECTION", types.contains("INTERFACE_SECTION"))
        assertTrue("Should contain IMPLEMENTATION_SECTION", types.contains("IMPLEMENTATION_SECTION"))
        assertTrue("Should contain USES_SECTION", types.contains("USES_SECTION"))
        assertTrue("Should contain UNIT_DECL_SECTION", types.contains("UNIT_DECL_SECTION"))
    }
}
