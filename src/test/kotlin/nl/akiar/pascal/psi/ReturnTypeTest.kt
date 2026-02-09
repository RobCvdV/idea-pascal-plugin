package nl.akiar.pascal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for RETURN_TYPE PSI element.
 */
class ReturnTypeTest : BasePlatformTestCase() {

    @Test
    fun testStandaloneFunction() {
        val code = """
            unit Test;
            interface
            function GetValue: Integer;
            implementation
            function GetValue: Integer;
            begin
              Result := 42;
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val returnTypes = PsiTreeUtil.findChildrenOfType(psiFile, PascalReturnType::class.java)

        assertTrue("Should find at least one return type", returnTypes.isNotEmpty())

        val returnType = returnTypes.first()
        assertEquals("Return type should be Integer", "Integer", returnType.typeName)
        assertTrue("Integer is a built-in type", returnType.isBuiltInType)
    }

    @Test
    fun testProcedureNoReturnType() {
        val code = """
            unit Test;
            interface
            procedure DoWork;
            implementation
            procedure DoWork;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val returnTypes = PsiTreeUtil.findChildrenOfType(psiFile, PascalReturnType::class.java)

        // Procedures should not have return types
        assertTrue("Procedures should have no return type", returnTypes.isEmpty())
    }

    @Test
    fun testFunctionWithStringReturn() {
        val code = """
            unit Test;
            interface
            function GetName: String;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val returnTypes = PsiTreeUtil.findChildrenOfType(psiFile, PascalReturnType::class.java)

        assertTrue("Should find at least one return type", returnTypes.isNotEmpty())

        val returnType = returnTypes.first()
        assertEquals("Return type should be String", "String", returnType.typeName)
        assertTrue("String is a built-in type", returnType.isBuiltInType)
    }

    @Test
    fun testFunctionWithBooleanReturn() {
        val code = """
            unit Test;
            interface
            function IsValid: Boolean;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val returnTypes = PsiTreeUtil.findChildrenOfType(psiFile, PascalReturnType::class.java)

        assertTrue("Should find at least one return type", returnTypes.isNotEmpty())

        val returnType = returnTypes.first()
        assertEquals("Return type should be Boolean", "Boolean", returnType.typeName)
        assertTrue("Boolean is a built-in type", returnType.isBuiltInType)
    }

    @Test
    fun testFunctionWithCustomTypeReturn() {
        val code = """
            unit Test;
            interface
            type
              TMyRecord = record
                Value: Integer;
              end;
            function GetRecord: TMyRecord;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val returnTypes = PsiTreeUtil.findChildrenOfType(psiFile, PascalReturnType::class.java)

        assertTrue("Should find at least one return type", returnTypes.isNotEmpty())

        val returnType = returnTypes.first()
        assertEquals("Return type should be TMyRecord", "TMyRecord", returnType.typeName)
        assertFalse("TMyRecord is not a built-in type", returnType.isBuiltInType)
    }

    @Test
    fun testMultipleFunctions() {
        val code = """
            unit Test;
            interface
            function GetInt: Integer;
            function GetString: String;
            function GetBool: Boolean;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val returnTypes = PsiTreeUtil.findChildrenOfType(psiFile, PascalReturnType::class.java)

        assertTrue("Should find at least 3 return types", returnTypes.size >= 3)

        val typeNames = returnTypes.map { it.typeName }
        assertTrue("Should contain Integer", typeNames.any { it == "Integer" })
        assertTrue("Should contain String", typeNames.any { it == "String" })
        assertTrue("Should contain Boolean", typeNames.any { it == "Boolean" })
    }
}
