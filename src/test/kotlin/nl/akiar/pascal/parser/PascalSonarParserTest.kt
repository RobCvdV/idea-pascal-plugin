package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
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

        // GVariable, AParam, LVariable should all be PascalVariableDefinition
        // (stub-based VARIABLE_DEFINITION with variableKind property)
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

    @Test
    fun testMultipleProceduresParameterResolution() {
        val text = """
            unit UdlgRestore;
            interface
            procedure acNextUpdate(Sender: TObject);
            procedure buDbFileDataClick(Sender: TObject);
            procedure pcWzdChanging(Sender: TObject; var AllowChange: Boolean);
            implementation
            procedure acNextUpdate(Sender: TObject); begin end;
            procedure buDbFileDataClick(Sender: TObject); begin end;
            procedure pcWzdChanging(Sender: TObject; var AllowChange: Boolean); begin end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("UdlgRestore.pas", text)
        
        com.intellij.openapi.application.runReadAction {
            val varDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
            
            // Check pcWzdChanging's AllowChange
            val allowChangeParams = varDefs.filter { it.name == "AllowChange" }
            assertEquals(2, allowChangeParams.size) // interface and implementation
            
            val senderParams = varDefs.filter { it.name == "Sender" }
            assertEquals(6, senderParams.size) // 3 procedures * 2 (interface/impl)
            
            // Check the first parameter of acNextUpdate in interface
            val firstSender = senderParams.first { 
                val routine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, nl.akiar.pascal.psi.PascalRoutine::class.java)
                routine != null && routine.name == "acNextUpdate"
            }
            
            val senderText = firstSender.text
            assertFalse("Parameter text should NOT contain '(': ${"$"}{senderText}", senderText.contains("("))
            assertEquals("Sender", senderText)

            // Find AllowChange in interface
            val allowChange = allowChangeParams.first { 
                val routine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, nl.akiar.pascal.psi.PascalRoutine::class.java)
                routine != null && routine.name == "pcWzdChanging"
            }
                
            val offset = allowChange.textOffset
            val elementAtOffset = psiFile.findElementAt(offset)
            assertNotNull(elementAtOffset)
            
            val varDefAtOffset = com.intellij.psi.util.PsiTreeUtil.getParentOfType(elementAtOffset, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
            assertNotNull("Element at offset ${"$"}{offset} should be within a VARIABLE_DEFINITION", varDefAtOffset)
            assertEquals("AllowChange", varDefAtOffset!!.name)
            
            // Verify ranges are correct - no "Sender" from buDbFileDataClick should cover pcWzdChanging
            val buDbSender = senderParams.first { 
                val routine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, nl.akiar.pascal.psi.PascalRoutine::class.java)
                routine?.name == "buDbFileDataClick"
            }
            
            val range = buDbSender.textRange
            assertFalse("Sender from buDbFileDataClick (${"$"}{range}) should NOT cover AllowChange offset (${"$"}{offset})", range.contains(offset))

            // Check for any overlaps among all VARIABLE_DEFINITIONs
            val varDefList = varDefs.toList()
            for (i in varDefList.indices) {
                for (j in i + 1 until varDefList.size) {
                    val v1 = varDefList[i]
                    val v2 = varDefList[j]
                    val r1 = v1.textRange
                    val r2 = v2.textRange
                    if (r1.intersects(r2)) {
                        // Nesting is okay (e.g. if one is inside another for some reason), but they shouldn't just overlap
                        val v1InsideV2 = r2.contains(r1)
                        val v2InsideV1 = r1.contains(r2)
                        assertTrue("Overlapping but not nested VARIABLE_DEFINITIONs found: " +
                                "${"$"}{v1.name} at ${"$"}{r1} and ${"$"}{v2.name} at ${"$"}{r2}",
                                v1InsideV2 || v2InsideV1)
                    }
                }
            }
        }
    }

    @Test
    fun testDocumentationProviderLookups() {
        val text = """
            unit UdlgRestore;
            interface
            procedure acNextUpdate(Sender: TObject);
            procedure buDbFileDataClick(Sender: TObject);
            procedure pcWzdChanging(Sender: TObject; var AllowChange: Boolean);
            implementation
            procedure acNextUpdate(Sender: TObject); begin end;
            procedure buDbFileDataClick(Sender: TObject); begin end;
            procedure pcWzdChanging(Sender: TObject; var AllowChange: Boolean); begin end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("UdlgRestore.pas", text)
        
        com.intellij.openapi.application.runReadAction {
            val varDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
            
            // Find pcWzdChanging.Sender in interface
            val pcWzdChangingSender = varDefs.first { 
                val routine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, nl.akiar.pascal.psi.PascalRoutine::class.java)
                val inInterface = com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, com.intellij.psi.PsiFile::class.java)?.let { _ ->
                    val section = routine?.parent
                    section?.node?.elementType == nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_SECTION
                } ?: false
                
                routine?.name == "pcWzdChanging" && it.name == "Sender" && inInterface
            }
            
            val offset = pcWzdChangingSender.textOffset
            val name = pcWzdChangingSender.name!!
            
            // This is what PascalDocumentationProvider calls:
            val found = nl.akiar.pascal.stubs.PascalVariableIndex.findVariableAtPosition(name, psiFile, offset)
            
            assertNotNull(found)
            val foundRoutine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(found, nl.akiar.pascal.psi.PascalRoutine::class.java)
            val foundIsInterface = found?.let { com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, com.intellij.psi.PsiFile::class.java)?.let { _ ->
                val section = com.intellij.psi.util.PsiTreeUtil.getParentOfType(found, nl.akiar.pascal.psi.PascalRoutine::class.java)?.parent
                section?.node?.elementType == nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_SECTION
            }} ?: false

            assertEquals("Found variable should belong to pcWzdChanging", "pcWzdChanging", foundRoutine?.name)
            assertTrue("Found variable should be in interface section", foundIsInterface)
        }
    }
    @Test
    fun testClassMemberParsing() {
        val text = """
            unit TestUnit;
            interface
            type
              TMyClass = class
              private
                FField: Integer;
                procedure MyMethod(AValue: Integer);
              public
                property Value: Integer read FField write MyMethod;
              end;
            implementation
            procedure TMyClass.MyMethod(AValue: Integer);
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
        
        assertTrue("Should contain PascalProperty", debugInfo.contains("PascalProperty"))
        assertTrue("Should contain MyMethod in interface", debugInfo.contains("PascalRoutine(MyMethod)"))
        
        val varDefs = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.PascalVariableDefinition::class.java)
        }
        assertTrue("Should find FField", varDefs.any { com.intellij.openapi.application.runReadAction { it.name } == "FField" })
        
        val routines = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, nl.akiar.pascal.psi.PascalRoutine::class.java)
        }
        val routineNames = com.intellij.openapi.application.runReadAction { routines.map { it.name } }
        assertTrue("Should contain MyMethod", routineNames.contains("MyMethod"))
    }

    @Test
    fun testMemberAccessParsing() {
        val text = """
            unit TestUnit;
            interface
            implementation
            procedure Test;
            var
              MyObj: TMyClass;
            begin
              MyObj.MyMethod(123);
              MyObj.Value := 456;
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        println("DEBUG TREE:\n" + debugInfo)
    }

    @Test
    fun testUnitHeaderDottedNameIsUnitReference() {
        val text = """
            unit System.Classes;
            interface
            implementation
            end.
        """.trimIndent()
        val psiFile = myFixture.configureByText("System.Classes.pas", text)
        assertNotNull(psiFile)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        assertTrue("Should contain UNIT_DECL_SECTION", debugInfo.contains("UNIT_DECL_SECTION"))
        assertTrue("Header should include UNIT_REFERENCE for dotted name", debugInfo.contains("UNIT_REFERENCE"))
        // Ensure no PascalVariableDefinition or PascalMemberReference created from header
        assertFalse(debugInfo.contains("PascalVariableDefinition"))
    }

    @Test
    fun testUsesClauseDottedNamesAreUnitReferences() {
        val text = """
            unit TestUnit;
            interface
            uses System.SysUtils, System.Classes, Winapi.Windows, Winapi.Messages;
            implementation
            end.
        """.trimIndent()
        val psiFile = myFixture.configureByText("TestUses.pas", text)
        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }
        assertTrue("Should contain USES_SECTION", debugInfo.contains("USES_SECTION"))
        // Expect multiple UNIT_REFERENCE occurrences
        val unitRefCount = debugInfo.split("UNIT_REFERENCE").size - 1
        assertTrue("Should contain at least 4 UNIT_REFERENCE entries", unitRefCount >= 4)
    }
}
