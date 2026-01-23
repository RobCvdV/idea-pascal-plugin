package nl.akiar.pascal.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.documentation.PascalDocumentationProvider
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.PascalTypeDefinition
import com.intellij.psi.util.PsiTreeUtil
import org.junit.jupiter.api.Test

class PascalDocumentationProviderTest : BasePlatformTestCase() {

    fun testParameterDocumentationDoesNotMatchGlobalType() {
        val text = """
            unit TestUnit;
            interface
            type
              Connection = class
              end;
            procedure Action(Connection: Integer);
            implementation
            procedure Action(Connection: Integer);
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        val provider = PascalDocumentationProvider()

        // Find the 'Connection' parameter in the procedure declaration (interface)
        val connectionParam = PsiTreeUtil.findChildrenOfType(psiFile, PascalVariableDefinition::class.java)
            .first { it.name == "Connection" && it.getVariableKind() == nl.akiar.pascal.psi.VariableKind.PARAMETER }

        val identifier = connectionParam.nameIdentifier!!
        
        // This is what the IDE calls when hovering over the identifier in the declaration
        val element = provider.getCustomDocumentationElement(myFixture.editor, psiFile, identifier, identifier.textOffset)

        assertNotNull("Should find a documentation element", element)
        assertTrue("Element should be a PascalVariableDefinition (the parameter), but was ${element!!::class.java.simpleName}", 
            element is PascalVariableDefinition)
        
        val varDef = element as PascalVariableDefinition
        assertEquals("Parameter", varDef.getVariableKind().name.capitalize())
        assertEquals("Connection", varDef.name)
    }

    fun testLocalVariableDocumentationDoesNotMatchGlobalType() {
        val text = """
            unit TestUnit;
            interface
            type
              MyVar = class
              end;
            procedure Action;
            implementation
            procedure Action;
            var
              MyVar: Integer;
            begin
              MyVar := 1;
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        val provider = PascalDocumentationProvider()

        // Find the 'MyVar' local variable in the implementation
        val myVarLocal = PsiTreeUtil.findChildrenOfType(psiFile, PascalVariableDefinition::class.java)
            .first { it.name == "MyVar" && it.getVariableKind() == nl.akiar.pascal.psi.VariableKind.LOCAL }

        val identifier = myVarLocal.nameIdentifier!!
        val element = provider.getCustomDocumentationElement(myFixture.editor, psiFile, identifier, identifier.textOffset)

        assertNotNull("Should find a documentation element", element)
        assertTrue("Element should be a PascalVariableDefinition (the local var), but was ${element!!::class.java.simpleName}", 
            element is PascalVariableDefinition)
        
        val varDef = element as PascalVariableDefinition
        assertEquals("Local", varDef.getVariableKind().name.capitalize())
        assertEquals("MyVar", varDef.name)
    }

    fun testTypeDocumentationInDeclaration() {
        val text = """
            unit TestUnit;
            interface
            type
              Connection = class
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", text)
        val provider = PascalDocumentationProvider()

        val typeDef = PsiTreeUtil.findChildrenOfType(psiFile, PascalTypeDefinition::class.java)
            .first { it.name == "Connection" }

        val identifier = typeDef.nameIdentifier!!
        val element = provider.getCustomDocumentationElement(myFixture.editor, psiFile, identifier, identifier.textOffset)

        assertNotNull("Should find a documentation element", element)
        assertTrue("Element should be a PascalTypeDefinition, but was ${element!!::class.java.simpleName}", 
            element is PascalTypeDefinition)
        assertEquals("Connection", (element as PascalTypeDefinition).name)
    }

    private fun String.capitalize() = this.lowercase().replaceFirstChar { it.uppercase() }
}
