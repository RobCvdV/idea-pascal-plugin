package nl.akiar.pascal.parser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.VariableKind
import com.intellij.psi.util.PsiTreeUtil
/**
 * Tests for Pascal's & escape prefix for keyword-clashing identifiers.
 * In Pascal/Delphi, you can use & before an identifier to escape keywords,
 * allowing them to be used as method/property/parameter names.
 * For example: &Set, &Index, &Type, &Begin
 */
class EscapedIdentifierTest : BasePlatformTestCase() {
    private val escapedMethodCode = """
        unit TestUnit;
        interface
        type
          TMyClass = class
          public
            procedure &Set(AValue: Integer);
            function &Index: Integer;
            procedure &Type(const AType: string);
          end;
        implementation
        procedure TMyClass.&Set(AValue: Integer);
        begin
        end;
        function TMyClass.&Index: Integer;
        begin
          Result := 0;
        end;
        procedure TMyClass.&Type(const AType: string);
        begin
        end;
        end.
    """.trimIndent()
    private val escapedPropertyCode = """
        unit TestUnit;
        interface
        type
          TMyClass = class
          private
            FIndex: Integer;
          public
            property &Index: Integer read FIndex write FIndex;
            property &Name: string;
          end;
        implementation
        end.
    """.trimIndent()
    private val escapedParameterCode = """
        unit TestUnit;
        interface
        procedure DoSomething(&Type: string; &Index: Integer);
        function Calculate(&Begin, &End: Integer): Integer;
        implementation
        procedure DoSomething(&Type: string; &Index: Integer);
        begin
        end;
        function Calculate(&Begin, &End: Integer): Integer;
        begin
          Result := &End - &Begin;
        end;
        end.
    """.trimIndent()
    fun testEscapedMethodNameSetRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedMethodCode)
        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val setMethods = routines.filter { it.name?.equals("Set", ignoreCase = true) == true }
        assertTrue("Should find method named 'Set' (escaped as &Set)", setMethods.isNotEmpty())
        assertEquals("Method name should be 'Set' without & prefix", "Set", setMethods.first().name)
    }

    fun testEscapedMethodNameIndexRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedMethodCode)
        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val indexMethods = routines.filter { it.name?.equals("Index", ignoreCase = true) == true }
        assertTrue("Should find method named 'Index' (escaped as &Index)", indexMethods.isNotEmpty())
        assertEquals("Method name should be 'Index' without & prefix", "Index", indexMethods.first().name)
    }

    fun testEscapedMethodNameTypeRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedMethodCode)
        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val typeMethods = routines.filter { it.name?.equals("Type", ignoreCase = true) == true }
        assertTrue("Should find method named 'Type' (escaped as &Type)", typeMethods.isNotEmpty())
        assertEquals("Method name should be 'Type' without & prefix", "Type", typeMethods.first().name)
    }

    fun testEscapedMethodsAreRecognizedAsMethods() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedMethodCode)
        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val escapedMethods = routines.filter {
            it.name?.equals("Set", ignoreCase = true) == true ||
            it.name?.equals("Index", ignoreCase = true) == true ||
            it.name?.equals("Type", ignoreCase = true) == true
        }
        assertTrue("Should find escaped methods", escapedMethods.isNotEmpty())
        for (method in escapedMethods) {
            assertTrue("${method.name} should be recognized as a method", method.isMethod)
        }
    }

    fun testEscapedPropertyIndexRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedPropertyCode)
        val properties = PsiTreeUtil.findChildrenOfType(psiFile, PascalProperty::class.java)
        val indexProps = properties.filter { it.name?.equals("Index", ignoreCase = true) == true }
        assertTrue("Should find property named 'Index' (escaped as &Index)", indexProps.isNotEmpty())
        assertEquals("Property name should be 'Index' without & prefix", "Index", indexProps.first().name)
    }

    fun testEscapedPropertyNameRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedPropertyCode)
        val properties = PsiTreeUtil.findChildrenOfType(psiFile, PascalProperty::class.java)
        val nameProps = properties.filter { it.name?.equals("Name", ignoreCase = true) == true }
        assertTrue("Should find property named 'Name' (escaped as &Name)", nameProps.isNotEmpty())
        assertEquals("Property name should be 'Name' without & prefix", "Name", nameProps.first().name)
    }

    fun testEscapedParameterTypeRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedParameterCode)
        val variables = PsiTreeUtil.findChildrenOfType(psiFile, PascalVariableDefinition::class.java)
        val typeParams = variables.filter {
            it.name?.equals("Type", ignoreCase = true) == true &&
            it.variableKind == VariableKind.PARAMETER
        }
        assertTrue("Should find parameter named 'Type' (escaped as &Type)", typeParams.isNotEmpty())
        assertEquals("Parameter name should be 'Type' without & prefix", "Type", typeParams.first().name)
    }

    fun testEscapedParameterIndexRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedParameterCode)
        val variables = PsiTreeUtil.findChildrenOfType(psiFile, PascalVariableDefinition::class.java)
        val indexParams = variables.filter {
            it.name?.equals("Index", ignoreCase = true) == true &&
            it.variableKind == VariableKind.PARAMETER
        }
        assertTrue("Should find parameter named 'Index' (escaped as &Index)", indexParams.isNotEmpty())
        assertEquals("Parameter name should be 'Index' without & prefix", "Index", indexParams.first().name)
    }

    fun testEscapedParameterBeginRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedParameterCode)
        val variables = PsiTreeUtil.findChildrenOfType(psiFile, PascalVariableDefinition::class.java)
        val beginParams = variables.filter {
            it.name?.equals("Begin", ignoreCase = true) == true &&
            it.variableKind == VariableKind.PARAMETER
        }
        assertTrue("Should find parameter named 'Begin' (escaped as &Begin)", beginParams.isNotEmpty())
        assertEquals("Parameter name should be 'Begin' without & prefix", "Begin", beginParams.first().name)
    }

    fun testEscapedParameterEndRecognized() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedParameterCode)
        val variables = PsiTreeUtil.findChildrenOfType(psiFile, PascalVariableDefinition::class.java)
        val endParams = variables.filter {
            it.name?.equals("End", ignoreCase = true) == true &&
            it.variableKind == VariableKind.PARAMETER
        }
        assertTrue("Should find parameter named 'End' (escaped as &End)", endParams.isNotEmpty())
        assertEquals("Parameter name should be 'End' without & prefix", "End", endParams.first().name)
    }

    fun testMultipleEscapedParametersInSameRoutine() {
        val psiFile = myFixture.configureByText("TestUnit.pas", escapedParameterCode)
        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val calculateFunc = routines.find { it.name?.equals("Calculate", ignoreCase = true) == true }
        assertNotNull("Should find Calculate function", calculateFunc)
        val params = PsiTreeUtil.findChildrenOfType(calculateFunc!!, PascalVariableDefinition::class.java)
            .filter { it.variableKind == VariableKind.PARAMETER }
        assertEquals("Calculate should have 2 parameters", 2, params.size)
        val paramNames = params.mapNotNull { it.name }
        assertTrue("Should have 'Begin' parameter", paramNames.any { it.equals("Begin", ignoreCase = true) })
        assertTrue("Should have 'End' parameter", paramNames.any { it.equals("End", ignoreCase = true) })
    }

    fun testEscapedTypeNameRecognized() {
        val typeCode = """
            unit TestUnit;
            interface
            type
              &TSet = class
              public
                procedure DoSomething;
              end;
            implementation
            end.
        """.trimIndent()
        val psiFile = myFixture.configureByText("TestUnit.pas", typeCode)
        val types = PsiTreeUtil.findChildrenOfType(psiFile, PascalTypeDefinition::class.java)
        val setTypes = types.filter { it.name?.equals("TSet", ignoreCase = true) == true }
        assertTrue("Should find type named 'TSet' (escaped as &TSet)", setTypes.isNotEmpty())
        assertEquals("Type name should be 'TSet' without & prefix", "TSet", setTypes.first().name)
    }

    fun testRegularIdentifiersStillWork() {
        val normalCode = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              public
                procedure DoSomething;
                function GetValue: Integer;
                property Name: string;
              end;
            implementation
            end.
        """.trimIndent()
        val psiFile = myFixture.configureByText("TestUnit.pas", normalCode)
        val routines = PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        val properties = PsiTreeUtil.findChildrenOfType(psiFile, PascalProperty::class.java)
        assertEquals("DoSomething", routines.find { it.name == "DoSomething" }?.name)
        assertEquals("GetValue", routines.find { it.name == "GetValue" }?.name)
        assertEquals("Name", properties.find { it.name == "Name" }?.name)
    }
}
