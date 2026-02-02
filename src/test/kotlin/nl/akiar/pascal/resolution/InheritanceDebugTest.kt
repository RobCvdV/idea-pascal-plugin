package nl.akiar.pascal.resolution

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalTypeDefinition
import org.junit.Test

class InheritanceDebugTest : BasePlatformTestCase() {

    @Test
    fun testSuperClassNameExtraction() {
        val file = myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TBaseClass = class
              public
                procedure BaseMethod;
              end;
              TDerivedClass = class(TBaseClass)
              public
                procedure DerivedMethod;
              end;
            implementation
            procedure TBaseClass.BaseMethod; begin end;
            procedure TDerivedClass.DerivedMethod; begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        println("Found ${types.size} types")

        val derivedClass = types.firstOrNull { it.name.equals("TDerivedClass", true) }
        assertNotNull("TDerivedClass should be found", derivedClass)

        // Dump AST structure for TDerivedClass
        println("\n=== AST Structure for TDerivedClass ===")
        dumpAst(derivedClass!!.node, 0)

        // Test getSuperClassName
        val superClassName = derivedClass.superClassName
        println("\nTDerivedClass.getSuperClassName() = $superClassName")
        assertEquals("TBaseClass", superClassName)

        // Test getSuperClass resolution
        val superClass = derivedClass.superClass
        println("TDerivedClass.getSuperClass() = ${superClass?.name}")
        assertNotNull("Should resolve superclass", superClass)
        assertEquals("TBaseClass", superClass?.name)
    }

    private fun dumpAst(node: ASTNode?, indent: Int) {
        if (node == null) return
        val prefix = "  ".repeat(indent)
        val text = node.text.take(40).replace("\n", "\\n")
        println("$prefix${node.elementType} \"$text\"")
        var child = node.firstChildNode
        while (child != null) {
            dumpAst(child, indent + 1)
            child = child.treeNext
        }
    }
}
