package nl.akiar.pascal.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalTypeDefinition
import com.intellij.psi.util.PsiTreeUtil

class PascalForwardDeclarationDocTest : BasePlatformTestCase() {

    fun testHoverForwardDeclarationShowsDocFromFull() {
        System.setProperty("pascal.test.debug", "true")
        val code = """
            unit TestUnit;
            interface
            type
              TMyClass = class;
              
              { This is the documentation for TMyClass }
              TMyClass = class(TBaseClass, IImplements)
              public
                procedure DoSomething;
              end;
            implementation
            end.
        """.trimIndent()
        
        myFixture.configureByText("TestUnit.pas", code)
        val provider = PascalDocumentationProvider()
        
        val types = PsiTreeUtil.findChildrenOfType(myFixture.file, PascalTypeDefinition::class.java)
        val forwardDecl = types.minByOrNull { it.textOffset }!!
        val nameIdentifier = forwardDecl.nameIdentifier!!
        
        // Simulate hover over forward declaration
        val element = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, nameIdentifier, nameIdentifier.textOffset)
        assertNotNull("Should find documentation element", element)
        
        val doc = provider.generateDoc(element, null)
        println("[DEBUG_LOG] Doc (hover forward): $doc")
        assertNotNull("Documentation should not be null", doc)
        
        // Verify that it shows the full signature, not just the forward one
        assertTrue("Doc should contain the full signature with TBaseClass", 
            doc!!.contains("TBaseClass"))
        assertTrue("Doc should contain the full signature with IImplements", 
            doc!!.contains("IImplements"))
        assertTrue("Doc should contain the comment from the full declaration", 
            doc!!.contains("This is the documentation for TMyClass"))
    }

    fun testHoverFullDeclarationShowsDocFromForward() {
        val code = """
            unit TestUnit;
            interface
            type
              { This doc is on the forward declaration }
              TMyClass = class;
              
              TMyClass = class
              public
                procedure DoSomething;
              end;
            implementation
            end.
        """.trimIndent()
        
        myFixture.configureByText("TestUnit.pas", code)
        val provider = PascalDocumentationProvider()
        
        val types = PsiTreeUtil.findChildrenOfType(myFixture.file, PascalTypeDefinition::class.java)
        val fullDecl = types.maxByOrNull { it.textOffset }!!
        val nameIdentifier = fullDecl.nameIdentifier!!

        // Simulate hover over full declaration
        val element = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, nameIdentifier, nameIdentifier.textOffset)
        assertNotNull("Should find documentation element", element)
        
        val doc = provider.generateDoc(element, null)
        println("[DEBUG_LOG] Doc (hover full): $doc")
        assertNotNull("Documentation should not be null", doc)
        assertTrue("Doc should contain the comment from the forward declaration", 
            doc!!.contains("This doc is on the forward declaration"))
        
        // Even when hovering full decl, it should show the full signature (header)
        assertTrue("Doc should contain the full signature header", 
            doc!!.contains("TMyClass") && doc!!.contains("class"))
    }
}
