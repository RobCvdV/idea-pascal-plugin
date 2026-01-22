package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

class PascalSonarParserTest : BasePlatformTestCase() {

    @BeforeEach
    fun setup() {
        setUp()
    }

    @AfterEach
    fun tearDownTest() {
        tearDown()
    }

    @Test
    fun testBasicParsing() {
        val text = """
            unit TestUnit;
            interface
            implementation
            end.
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("Test.pas", text)
        assertNotNull(psiFile)
        assertEquals(text, psiFile.text)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        
        assertTrue("Should contain INTERFACE_SECTION", debugInfo.contains("INTERFACE_SECTION"))
        assertTrue("Should contain IMPLEMENTATION_SECTION", debugInfo.contains("IMPLEMENTATION_SECTION"))
        assertTrue("Should contain UNIT_DECL_SECTION", debugInfo.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testProgramParsing() {
        val text = """
            program TestProgram;
            begin
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.dpr", text)
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        assertTrue("Should contain PROGRAM_DECL_SECTION", debugInfo.contains("PROGRAM_DECL_SECTION"))
    }

    @Test
    fun testLibraryParsing() {
        val text = """
            library TestLibrary;
            begin
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        assertTrue("Should contain LIBRARY_DECL_SECTION", debugInfo.contains("LIBRARY_DECL_SECTION"))
    }

    @Test
    fun testTypeParsing() {
        val text = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              end;
              TMyRecord = record
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        println("DEBUG TREE:\n" + debugInfo)
        
        // Count PascalTypeDefinition occurrences
        val occurrences = debugInfo.split("PascalTypeDefinition").size - 1
        assertEquals("Should have 2 PascalTypeDefinition nodes", 2, occurrences)
    }

    @Test
    fun testVariableParsing() {
        val text = """
            unit TestUnit;
            interface
            var
              GVariable: Integer;
            implementation
            procedure Test(AParam: Integer);
            var
              LVariable: string;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        println("DEBUG TREE:\n" + debugInfo)

        // GVariable, AParam, LVariable should be PascalVariableDefinition
        val occurrences = debugInfo.split("PascalVariableDefinition").size - 1
        assertEquals("Should have 3 PascalVariableDefinition nodes", 3, occurrences)
    }

    @Test
    fun testFieldParsing() {
        val text = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                FField: Integer;
              public
                FPublicField: string;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        println("DEBUG TREE:\n" + debugInfo)

        // FField and FPublicField should be PascalVariableDefinition
        val occurrences = debugInfo.split("PascalVariableDefinition").size - 1
        assertEquals("Should have 2 PascalVariableDefinition nodes", 2, occurrences)
    }

    @Test
    fun testComplexParsing() {
        val text = """
            unit ComplexUnit;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
              public
                property Value: Integer read FValue write FValue;
              end;
            implementation
            end.
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("Complex.pas", text)
        assertNotNull(psiFile)
        assertEquals(text, psiFile.text)
    }

    @Test
    fun testEmptyFile() {
        val text = ""
        val psiFile = myFixture.configureByText("Empty.pas", text)
        assertNotNull(psiFile)
        assertEquals(text, psiFile.text)
    }

    @Test
    fun testVariableKindResolution() {
        val text = """
            unit TestUnit;
            interface
            var
              GVar: Integer;
            type
              TMyClass = class
              private
                FField: Integer;
              public
                procedure Test(AParam: Integer);
              end;
            implementation
            procedure TMyClass.Test(AParam: Integer);
            var
              LVar: Integer;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        
        com.intellij.openapi.application.runReadAction {
            val varDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
            val kinds = varDefs.associate { it.name to it.variableKind }
            
            assertEquals(nl.akiar.pascal.psi.VariableKind.GLOBAL, kinds["GVar"])
            assertEquals(nl.akiar.pascal.psi.VariableKind.FIELD, kinds["FField"])
            assertEquals(nl.akiar.pascal.psi.VariableKind.PARAMETER, kinds["AParam"])
            assertEquals(nl.akiar.pascal.psi.VariableKind.LOCAL, kinds["LVar"])
        }
    }

    @Test
    fun testConstantKindResolution() {
        val text = """
            unit TestUnit;
            interface
            const
              GConst = 123;
            implementation
            procedure Test;
            const
              LConst = 'abc';
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)

        com.intellij.openapi.application.runReadAction {
            val varDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
            val kinds = varDefs.associate { it.name to it.variableKind }

            assertEquals(nl.akiar.pascal.psi.VariableKind.CONSTANT, kinds["GConst"])
            assertEquals(nl.akiar.pascal.psi.VariableKind.CONSTANT, kinds["LConst"])
        }
    }

    @Test
    fun testVisibilityResolution() {
        val text = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              strict private
                FStrictPrivate: Integer;
              private
                FPrivate: Integer;
              protected
                FProtected: Integer;
              public
                FPublic: Integer;
              published
                FPublished: Integer;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        
        com.intellij.openapi.application.runReadAction {
            val varDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.impl.PascalVariableDefinitionImpl::class.java)
            val visibilities = varDefs.associate { it.name to it.visibility }
            
            assertEquals("strict private", visibilities["FStrictPrivate"])
            assertEquals("private", visibilities["FPrivate"])
            assertEquals("protected", visibilities["FProtected"])
            assertEquals("public", visibilities["FPublic"])
            assertEquals("published", visibilities["FPublished"])
        }
    }

    @Test
    fun testGenericParameterResolution() {
        val text = """
            unit TestUnit;
            interface
            type
              TMyList<T, K> = class
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        
        com.intellij.openapi.application.runReadAction {
            val typeDef = com.intellij.psi.util.PsiTreeUtil.findChildOfType(psiFile, nl.akiar.pascal.psi.PascalTypeDefinition::class.java)
            assertNotNull(typeDef)
            val params = typeDef!!.typeParameters
            assertEquals(2, params.size)
            assertEquals("T", params[0])
            assertEquals("K", params[1])
        }
    }

    @Test
    fun testFlawedRecognitionReproduction() {
        val text = """
            unit UdlgRestore;
            interface
            procedure AConExecuteComplete(const AConnection: TADOConnection; AError: Error);
            implementation
            procedure AConExecuteComplete(const AConnection: TADOConnection; AError: Error);
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("UdlgRestore.pas", text)
        
        com.intellij.openapi.application.runReadAction {
            val varDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)

            val parameters = varDefs.filter { it.variableKind == nl.akiar.pascal.psi.VariableKind.PARAMETER }
            assertEquals("Should have 4 parameters (2 in interface, 2 in implementation)", 4, parameters.size)

            val aParam1 = parameters.first { it.name == "AConnection" }
            assertEquals("TADOConnection", aParam1.typeName)

            val aParam2 = parameters.first { it.name == "AError" }
            assertEquals("Error", aParam2.typeName)
        }
    }
    @Test
    fun testCombinedParameterParsing() {
        val text = """
            unit TestUnit;
            interface
            procedure Action(const AParam1, AParam2, AParam3: string; var AOtherParam: Integer);
            implementation
            procedure Action(const AParam1, AParam2, AParam3: string; var AOtherParam: Integer);
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        
        com.intellij.openapi.application.runReadAction {
            val varDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
            val names = varDefs.map { it.name }
            
            // Should find all 4 parameters twice (once in interface, once in implementation)
            // Total 8 VARIABLE_DEFINITIONs
            assertTrue("Should contain AParam1", names.contains("AParam1"))
            assertTrue("Should contain AParam2", names.contains("AParam2"))
            assertTrue("Should contain AParam3", names.contains("AParam3"))
            assertTrue("Should contain AOtherParam", names.contains("AOtherParam"))
            
            val parameters = varDefs.filter { it.variableKind == nl.akiar.pascal.psi.VariableKind.PARAMETER }
            assertEquals("Should have 8 parameters (4 in interface, 4 in implementation)", 8, parameters.size)
            
            val aParam1 = parameters.first { it.name == "AParam1" }
            assertEquals("string", aParam1.typeName)
            
            val aOtherParam = parameters.first { it.name == "AOtherParam" }
            assertEquals("Integer", aOtherParam.typeName)
        }
    }
}
