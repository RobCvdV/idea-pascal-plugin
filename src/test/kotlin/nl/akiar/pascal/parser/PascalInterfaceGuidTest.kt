package nl.akiar.pascal.parser

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalAttribute
import nl.akiar.pascal.psi.PascalInterfaceGuid
import nl.akiar.pascal.psi.PascalTypeDefinition

/**
 * Tests for interface GUID parsing to ensure GUIDs are parsed as INTERFACE_GUID elements,
 * not as ATTRIBUTE_DEFINITION elements.
 */
class PascalInterfaceGuidTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/testData"
    }

    fun testInterfaceGuidIsParsedAsGuidElement() {
        val code = """
            unit TestUnit;
            interface
            type
              IDriverApiRequest = interface
                ['{7D9971AC-ED96-4F0A-AC90-9CF0B0F10550}']
                function GetSessionId: Int64;
                function GetContext: IDriverApiContext;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.pas", code)

        // Check that we have INTERFACE_GUID elements, not ATTRIBUTE elements
        val guids = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalInterfaceGuid::class.java)
        }
        assertEquals("Should have 1 INTERFACE_GUID element", 1, guids.size)

        // Check that there are NO PascalAttribute elements (the GUID should not be an attribute)
        val attributes = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalAttribute::class.java)
        }
        assertEquals("Should have 0 PascalAttribute elements (GUID should not be an attribute)", 0, attributes.size)

        // Verify the GUID value is correctly extracted
        val guid = guids.first()
        assertEquals("Should extract correct GUID value",
                     "7D9971AC-ED96-4F0A-AC90-9CF0B0F10550",
                     guid.guidValue)

        // STRICT CHECK: Verify GUID is a direct child of the interface type definition, NOT in an ATTRIBUTE_LIST
        val interfaceType = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalTypeDefinition::class.java)
                .firstOrNull { it.name == "IDriverApiRequest" }
        }
        assertNotNull("Should find IDriverApiRequest interface", interfaceType)

        // The GUID must be a direct child of the interface type, not wrapped in ATTRIBUTE_LIST
        val guidParent = com.intellij.openapi.application.runReadAction { guid.parent }
        assertEquals("GUID parent should be the interface type definition itself",
                     interfaceType, guidParent.parent)

        // Verify there's NO ATTRIBUTE_LIST in the interface at all
        val hasAttributeList = com.intellij.openapi.application.runReadAction {
            var child = interfaceType!!.firstChild
            var found = false
            while (child != null) {
                if (child.node?.elementType == nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_LIST) {
                    found = true
                    break
                }
                child = child.nextSibling
            }
            found
        }
        assertFalse("Interface should have NO ATTRIBUTE_LIST elements", hasAttributeList)

        // CRITICAL: Verify the GUID is NOT inside any routine
        val routines = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(interfaceType, nl.akiar.pascal.psi.PascalRoutine::class.java)
        }
        assertTrue("Interface should have routines", routines.size > 0)

        for (routine in routines) {
            val guidsInRoutine = com.intellij.openapi.application.runReadAction {
                PsiTreeUtil.findChildrenOfType(routine, PascalInterfaceGuid::class.java)
            }
            assertEquals("Routine '${routine.name}' should NOT contain any GUID elements",
                         0, guidsInRoutine.size)
        }
    }

    fun testInterfaceGuidWithTypeDefinitionGetGUID() {
        val code = """
            unit TestUnit;
            interface
            type
              IMyInterface = interface
                ['{285DEA8A-B865-11D1-AAA7-00C04FB17A72}']
                procedure DoSomething;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.pas", code)

        val typeDefinition = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalTypeDefinition::class.java)
                .firstOrNull { it.name == "IMyInterface" }
        }

        assertNotNull("Should find IMyInterface type definition", typeDefinition)
        assertEquals("TypeDefinition.getGUID() should return correct GUID",
                     "285DEA8A-B865-11D1-AAA7-00C04FB17A72",
                     typeDefinition!!.guid)
    }

    fun testMultipleInterfacesWithGUIDs() {
        val code = """
            unit TestUnit;
            interface
            type
              IInterface1 = interface
                ['{11111111-1111-1111-1111-111111111111}']
                procedure Method1;
              end;
              
              IInterface2 = interface
                ['{22222222-2222-2222-2222-222222222222}']
                procedure Method2;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.pas", code)

        val guids = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalInterfaceGuid::class.java)
        }
        assertEquals("Should have 2 INTERFACE_GUID elements", 2, guids.size)

        val guidValues = guids.map { it.guidValue }.toSet()
        assertTrue("Should contain first GUID",
                   guidValues.contains("11111111-1111-1111-1111-111111111111"))
        assertTrue("Should contain second GUID",
                   guidValues.contains("22222222-2222-2222-2222-222222222222"))
    }

    fun testInterfaceWithBothGuidAndRealAttributes() {
        val code = """
            unit TestUnit;
            interface
            type
              [MyAttribute]
              IMyInterface = interface
                ['{285DEA8A-B865-11D1-AAA7-00C04FB17A72}']
                [MethodAttribute]
                procedure DoSomething;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.pas", code)

        val guids = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalInterfaceGuid::class.java)
        }
        assertEquals("Should have 1 INTERFACE_GUID element", 1, guids.size)

        val attributes = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalAttribute::class.java)
        }
        assertEquals("Should have 2 real attributes (not counting the GUID)", 2, attributes.size)

        val attrNames = attributes.map { it.name }.toSet()
        assertTrue("Should contain MyAttribute", attrNames.contains("MyAttribute"))
        assertTrue("Should contain MethodAttribute", attrNames.contains("MethodAttribute"))
    }

    fun testGuidFullTextFormat() {
        val code = """
            unit TestUnit;
            interface
            type
              IMyInterface = interface
                ['{7D9971AC-ED96-4F0A-AC90-9CF0B0F10550}']
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.pas", code)

        val guid = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalInterfaceGuid::class.java).firstOrNull()
        }

        assertNotNull("Should find GUID element", guid)
        val fullText = guid!!.fullText
        assertTrue("Full text should contain the GUID string",
                   fullText.contains("7D9971AC-ED96-4F0A-AC90-9CF0B0F10550"))
        assertTrue("Full text should contain opening quote-brace",
                   fullText.contains("'{"))
        assertTrue("Full text should contain closing brace-quote",
                   fullText.contains("}'"))
    }
}

