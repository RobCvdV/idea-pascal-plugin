package nl.akiar.pascal.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.ParameterModifier
import org.junit.Test

/**
 * Tests for parameter modifier detection in routine parameters.
 */
class ParameterModifierTest : BasePlatformTestCase() {

    @Test
    fun testVarParameter() {
        val code = """
            unit Test;
            interface
            procedure Swap(var A: Integer; var B: Integer);
            implementation
            procedure Swap(var A: Integer; var B: Integer);
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val params = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                     PascalVariableDefinition::class.java)

        val varParams = params.filter { it.variableKind == VariableKind.PARAMETER }
        assertTrue("Should find at least 2 var parameters", varParams.size >= 2)

        for (param in varParams) {
            assertEquals("Parameter ${param.name} should be VAR",
                        ParameterModifier.VAR,
                        param.parameterModifier)
            assertTrue("isVarParameter should return true", param.isVarParameter())
        }
    }

    @Test
    fun testConstParameter() {
        val code = """
            unit Test;
            interface
            procedure Display(const AValue: Integer; const AName: String);
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val params = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                     PascalVariableDefinition::class.java)

        val constParams = params.filter {
            it.variableKind == VariableKind.PARAMETER && it.isConstParameter()
        }
        assertTrue("Should find at least 2 const parameters", constParams.size >= 2)

        for (param in constParams) {
            assertEquals("Parameter ${param.name} should be CONST",
                        ParameterModifier.CONST,
                        param.parameterModifier)
        }
    }

    @Test
    fun testOutParameter() {
        val code = """
            unit Test;
            interface
            procedure GetValues(out AResult: Integer; out AError: String);
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val params = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                     PascalVariableDefinition::class.java)

        val outParams = params.filter {
            it.variableKind == VariableKind.PARAMETER && it.isOutParameter()
        }
        assertTrue("Should find at least 2 out parameters", outParams.size >= 2)

        for (param in outParams) {
            assertEquals("Parameter ${param.name} should be OUT",
                        ParameterModifier.OUT,
                        param.parameterModifier)
        }
    }

    @Test
    fun testNoModifierParameter() {
        val code = """
            unit Test;
            interface
            procedure Calculate(AValue: Integer; AName: String);
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val params = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                     PascalVariableDefinition::class.java)

        val normalParams = params.filter { it.variableKind == VariableKind.PARAMETER }
        assertTrue("Should find at least 2 parameters", normalParams.size >= 2)

        for (param in normalParams) {
            assertEquals("Parameter ${param.name} should have VALUE modifier",
                        ParameterModifier.VALUE,
                        param.parameterModifier)
            assertTrue("isValueParameter should return true", param.isValueParameter())
            assertFalse("isVarParameter should return false", param.isVarParameter())
            assertFalse("isConstParameter should return false", param.isConstParameter())
            assertFalse("isOutParameter should return false", param.isOutParameter())
        }
    }

    @Test
    fun testMultipleModifiers() {
        val code = """
            unit Test;
            interface
            procedure Process(var A: Integer; const B: String; out C: Boolean; D: Double);
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val params = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                     PascalVariableDefinition::class.java)

        val paramList = params.filter { it.variableKind == VariableKind.PARAMETER }.toList()
        assertTrue("Should find at least 4 parameters", paramList.size >= 4)

        // Find specific parameters by name
        val varParam = paramList.find { it.name == "A" }
        val constParam = paramList.find { it.name == "B" }
        val outParam = paramList.find { it.name == "C" }
        val normalParam = paramList.find { it.name == "D" }

        if (varParam != null) {
            assertEquals("A should be VAR", ParameterModifier.VAR, varParam.parameterModifier)
        }
        if (constParam != null) {
            assertEquals("B should be CONST", ParameterModifier.CONST, constParam.parameterModifier)
        }
        if (outParam != null) {
            assertEquals("C should be OUT", ParameterModifier.OUT, outParam.parameterModifier)
        }
        if (normalParam != null) {
            assertEquals("D should be VALUE", ParameterModifier.VALUE, normalParam.parameterModifier)
        }
    }
}

