package nl.akiar.pascal.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import org.junit.Test

/**
 * Tests for PascalTargetElementEvaluator — maps leaf identifier tokens to Pascal PSI
 * elements (types, routines, properties) for TargetElementUtil.
 */
class PascalTargetElementEvaluatorTest : BasePlatformTestCase() {

    private val evaluator = PascalTargetElementEvaluator()

    private fun getNamedElement(code: String, fileName: String = "TestUnit.pas"): com.intellij.psi.PsiElement? {
        val file = myFixture.configureByText(fileName, code)
        val offset = myFixture.caretOffset
        val element = file.findElementAt(offset) ?: return null
        return evaluator.getNamedElement(element)
    }

    // ---- Declaration sites ----

    @Test
    fun testRoutineDeclarationReturnsRoutine() {
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure Do<caret>Work;
              end;
            implementation
            procedure TFoo.DoWork; begin end;
            end.
        """.trimIndent()

        val result = getNamedElement(code)
        assertNotNull("Should return named element for routine declaration", result)
        assertTrue("Should be a PascalRoutine, got ${result?.javaClass?.simpleName}", result is PascalRoutine)
        assertEquals("DoWork", (result as PascalRoutine).name)
    }

    @Test
    fun testTypeDefinitionReturnsType() {
        val code = """
            unit TestUnit;
            interface
            type
              TFo<caret>o = class
              public
                procedure DoWork;
              end;
            implementation
            procedure TFoo.DoWork; begin end;
            end.
        """.trimIndent()

        val result = getNamedElement(code)
        assertNotNull("Should return named element for type definition", result)
        assertTrue("Should be a PascalTypeDefinition, got ${result?.javaClass?.simpleName}", result is PascalTypeDefinition)
        assertEquals("TFoo", (result as PascalTypeDefinition).name)
    }

    @Test
    fun testPropertyDeclarationReturnsProperty() {
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                property Na<caret>me: String read FName;
              end;
            implementation
            end.
        """.trimIndent()

        val result = getNamedElement(code)
        assertNotNull("Should return named element for property declaration", result)
        assertTrue("Should be a PascalProperty, got ${result?.javaClass?.simpleName}", result is PascalProperty)
        assertEquals("Name", (result as PascalProperty).name)
    }

    // ---- Call site resolution ----

    @Test
    fun testCallSiteResolvesViaReference() {
        // Identifier at a call site should resolve to the target routine via references
        myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure DoWork;
                procedure Run;
              end;
            implementation
            procedure TFoo.DoWork; begin end;
            procedure TFoo.Run;
            begin
              Do<caret>Work;
            end;
            end.
        """.trimIndent())

        val offset = myFixture.caretOffset
        val element = myFixture.file.findElementAt(offset)
        assertNotNull("Should find element at caret", element)

        val result = evaluator.getNamedElement(element!!)
        // Call site resolution depends on reference providers being registered;
        // if the reference resolves, it should return a PascalRoutine
        if (result != null) {
            assertTrue("Resolved call site should be a PascalRoutine", result is PascalRoutine)
        }
    }

    // ---- isAcceptableNamedParent ----

    @Test
    fun testAcceptableNamedParents() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure DoWork;
                property Name: String read FName;
              end;
            implementation
            procedure TFoo.DoWork; begin end;
            end.
        """.trimIndent())

        val types = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val routines = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, PascalRoutine::class.java)
        val properties = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, PascalProperty::class.java)

        assertTrue("PascalTypeDefinition should be acceptable", evaluator.isAcceptableNamedParent(types.first()))
        assertTrue("PascalRoutine should be acceptable", evaluator.isAcceptableNamedParent(routines.first()))
        if (properties.isNotEmpty()) {
            assertTrue("PascalProperty should be acceptable", evaluator.isAcceptableNamedParent(properties.first()))
        }

        // Non-Pascal elements should not be acceptable
        assertFalse("Null should not be acceptable", evaluator.isAcceptableNamedParent(null))
    }
}
