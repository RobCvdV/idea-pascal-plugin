package nl.akiar.pascal.parser

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalElementTypes
import org.junit.Test

class AttributeEdgeCasesTest : BasePlatformTestCase() {

    @Test
    fun testMultipleAttributesAndArgsOnField() {
        val text = """
            unit test;
            interface
            type
              TRec = record
                [Attr1][Attr2(1, 'x', Ident)] X: Integer;
              end;
            implementation
            end.
        """.trimIndent()
        val file = myFixture.configureByText("test.pas", text)
        val attrs = PsiTreeUtil.findChildrenOfType(file, com.intellij.psi.PsiElement::class.java)
            .filter { it.node?.elementType == PascalElementTypes.ATTRIBUTE_DEFINITION }
        assertTrue("Should contain at least 2 ATTRIBUTE_DEFINITION nodes", attrs.size >= 2)
    }

    @Test
    fun testInterfaceMethodAttribute() {
        val text = """
            unit test;
            interface
            type
              IMy = interface
                [Attr] procedure DoIt;
              end;
            implementation
            end.
        """.trimIndent()
        val file = myFixture.configureByText("test.pas", text)
        val attrs = PsiTreeUtil.findChildrenOfType(file, com.intellij.psi.PsiElement::class.java)
            .filter { it.node?.elementType == PascalElementTypes.ATTRIBUTE_DEFINITION }
        assertTrue("Should contain ATTRIBUTE_DEFINITION for interface method", attrs.isNotEmpty())
    }

    @Test
    fun testNoAttributesNoFalsePositives() {
        val text = """
            unit test;
            interface
            type
              TMyClass = class
              private
                FName: string; // no attributes
              public
                procedure DoSomething; // no attributes
              end;
            implementation
            end.
        """.trimIndent()
        val file = myFixture.configureByText("test.pas", text)
        val attrs = PsiTreeUtil.findChildrenOfType(file, com.intellij.psi.PsiElement::class.java)
            .filter { it.node?.elementType == PascalElementTypes.ATTRIBUTE_DEFINITION }
        assertTrue("Should contain no ATTRIBUTE_DEFINITION nodes", attrs.isEmpty())
    }
}
