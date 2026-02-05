package nl.akiar.pascal.parser

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalSyntaxHighlighter
import nl.akiar.pascal.psi.PascalElementTypes
import org.junit.Test

class AttributeHighlightingTest : BasePlatformTestCase() {

    @Test
    fun testAttributeOnFieldHighlighted() {
        val text = """
            unit test;
            interface
            type
              TMyClass = class
              private
                [MyAttr] FName: string;
                [MyAttr][AnotherAttr(42)] FValue: Integer;
              end;
            implementation
            end.
        """.trimIndent()
        val file = myFixture.configureByText("test.pas", text)
        val highlights = myFixture.doHighlighting()

        val attrNameOffset = text.indexOf("MyAttr")
        val anotherAttrOffset = text.indexOf("AnotherAttr")

        val attrName = highlights.find { it.startOffset == attrNameOffset }
        val anotherAttr = highlights.find { it.startOffset == anotherAttrOffset }

        assertNotNull("MyAttr should be annotated", attrName)
        assertNotNull("AnotherAttr should be annotated", anotherAttr)

        // Verify PSI contains ATTRIBUTE_DEFINITION nodes
        val attrs = PsiTreeUtil.findChildrenOfType(file, com.intellij.psi.PsiElement::class.java)
            .filter { it.node?.elementType == PascalElementTypes.ATTRIBUTE_DEFINITION }
        assertTrue("Should contain at least 2 ATTRIBUTE_DEFINITION nodes", attrs.size >= 2)
    }

    @Test
    fun testAttributeOnRoutineHighlighted() {
        val text = """
            unit test;
            interface
            type
              TMyClass = class
              public
                [Attr] procedure DoSomething;
              end;
            implementation
            end.
        """.trimIndent()
        val file = myFixture.configureByText("test.pas", text)
        val highlights = myFixture.doHighlighting()

        val attrOffset = text.indexOf("Attr")
        val attrHl = highlights.find { it.startOffset == attrOffset }
        assertNotNull("Attr should be annotated", attrHl)

        val attrs = PsiTreeUtil.findChildrenOfType(file, com.intellij.psi.PsiElement::class.java)
            .filter { it.node?.elementType == PascalElementTypes.ATTRIBUTE_DEFINITION }
        assertTrue("Should contain ATTRIBUTE_DEFINITION node for routine", attrs.isNotEmpty())
    }
}
