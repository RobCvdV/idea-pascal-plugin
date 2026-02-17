package nl.akiar.pascal.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.documentation.PascalDocumentationProvider
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalRoutine
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

    fun testRoutineDocumentationScopeValidation() {
        // Unit A with MyRoutine
        myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            { Doc for UnitA.MyRoutine }
            procedure MyRoutine;
            implementation
            procedure MyRoutine; begin end;
            end.
        """.trimIndent())

        // Unit B with MyRoutine
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            { Doc for UnitB.MyRoutine }
            procedure MyRoutine;
            implementation
            procedure MyRoutine; begin end;
            end.
        """.trimIndent())

        // Main unit using only UnitB
        val mainText = """
            unit Main;
            interface
            uses UnitB;
            implementation
            procedure Test;
            begin
              MyRoutine;
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Main.pas", mainText)
        val provider = PascalDocumentationProvider()

        val offset = mainText.indexOf("MyRoutine")
        val identifier = psiFile.findElementAt(offset)!!
        
        val element = provider.getCustomDocumentationElement(myFixture.editor, psiFile, identifier, identifier.textOffset)

        assertNotNull("Should find a documentation element", element)
        assertTrue("Element should be a PascalRoutine, but was ${element!!::class.java.simpleName}", 
            element is PascalRoutine)
        
        val routine = element as PascalRoutine
        println("DEBUG: routine.getUnitName() = '${routine.getUnitName()}'")
        println("DEBUG: routine.containingFile.name = '${routine.containingFile.name}'")
        println("DEBUG: routine.docComment = '${routine.docComment}'")
        assertEquals("unitb", routine.getUnitName())
        assertEquals("Doc for UnitB.MyRoutine", routine.docComment)
    }

    fun testUnresolvedMemberAccessDoesNotFallBackToParentRoutine() {
        // When hovering over an unresolved member (after a DOT), the doc provider
        // should NOT walk up the PSI tree and show the containing procedure.
        // Instead, it should return the raw identifier so generateDoc shows
        // "Could not find member" message.
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                Name: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure TestProc;
            var
              Obj: TMyClass;
            begin
              Obj.NonExistentMember<caret>;
            end;
            end.
        """.trimIndent())

        val provider = PascalDocumentationProvider()
        val offset = myFixture.caretOffset
        // Find the identifier at caret
        var identifier = mainFile.findElementAt(offset)
        if (identifier == null || identifier.node.elementType != nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
            identifier = mainFile.findElementAt(offset - 1)
        }
        assertNotNull("Should find identifier at caret", identifier)
        assertEquals("NonExistentMember", identifier!!.text)

        val element = provider.getCustomDocumentationElement(myFixture.editor, mainFile, identifier, identifier.textOffset)

        // The element should NOT be a PascalRoutine (the containing TestProc)
        // It should be the raw identifier for "member not found" display
        assertNotNull("Should return an element for unresolved member", element)
        assertFalse(
            "Should NOT fall back to containing routine (TestProc), but was ${element!!.javaClass.simpleName}: ${if (element is PascalRoutine) (element as PascalRoutine).name else element.text}",
            element is PascalRoutine && (element as PascalRoutine).name == "TestProc"
        )

        // Verify that generateDoc produces an "unresolved member" message, not a routine doc
        val doc = provider.generateDoc(element, identifier)
        if (doc != null) {
            println("Generated doc: $doc")
            assertFalse("Doc should NOT show containing procedure signature",
                doc.contains("procedure TestProc") || doc.contains("function TestProc"))
        }
    }

    fun testUnresolvedMemberAccessDoesNotFallBackToContainingRoutine() {
        // When hovering over a member access that can't be resolved via chain,
        // reference, or index lookup, the doc provider should return contextElement
        // (showing "unresolved member") instead of calling super.getCustomDocumentationElement()
        // which walks the PSI tree upward and shows the containing procedure.
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                Name: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure ContainingProc;
            var
              Obj: TMyClass;
            begin
              Obj.CompletelyUnknownMember<caret>;
            end;
            end.
        """.trimIndent())

        val provider = PascalDocumentationProvider()
        val offset = myFixture.caretOffset
        var identifier = mainFile.findElementAt(offset)
        if (identifier == null || identifier.node.elementType != nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
            identifier = mainFile.findElementAt(offset - 1)
        }
        assertNotNull("Should find identifier at caret", identifier)
        assertEquals("CompletelyUnknownMember", identifier!!.text)

        val element = provider.getCustomDocumentationElement(myFixture.editor, mainFile, identifier, identifier.textOffset)

        assertNotNull("Should return an element (not null)", element)
        // The key assertion: it should NOT be the containing procedure
        assertFalse(
            "Should NOT fall back to containing routine (ContainingProc), but was ${element!!.javaClass.simpleName}" +
                if (element is PascalRoutine) ": ${(element as PascalRoutine).name}" else ": ${element.text}",
            element is PascalRoutine && (element as PascalRoutine).name == "ContainingProc"
        )
    }

    private fun String.capitalize() = this.lowercase().replaceFirstChar { it.uppercase() }
}
