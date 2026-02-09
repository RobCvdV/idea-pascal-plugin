package nl.akiar.pascal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for CLASS_TYPE_REFERENCE PSI element in method implementations.
 */
class ClassTypeReferenceTest : BasePlatformTestCase() {

    @Test
    fun testSimpleMethodImplementation() {
        val code = """
            unit Test;
            interface
            type
              TMyClass = class
                procedure DoWork;
              end;
            implementation
            procedure TMyClass.DoWork;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val classRefs = PsiTreeUtil.findChildrenOfType(psiFile, PascalClassTypeReference::class.java)

        assertTrue("Should find at least one class type reference", classRefs.isNotEmpty())

        val classRef = classRefs.first()
        assertEquals("Class name should be TMyClass", "TMyClass", classRef.className)
    }

    @Test
    fun testMethodWithParameters() {
        val code = """
            unit Test;
            interface
            type
              TMyClass = class
                procedure Process(A: Integer; B: String);
              end;
            implementation
            procedure TMyClass.Process(A: Integer; B: String);
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val classRefs = PsiTreeUtil.findChildrenOfType(psiFile, PascalClassTypeReference::class.java)

        assertTrue("Should find at least one class type reference", classRefs.isNotEmpty())

        val classRef = classRefs.first()
        assertEquals("Class name should be TMyClass", "TMyClass", classRef.className)
    }

    @Test
    fun testFunctionImplementation() {
        val code = """
            unit Test;
            interface
            type
              TMyClass = class
                function GetValue: Integer;
              end;
            implementation
            function TMyClass.GetValue: Integer;
            begin
              Result := 42;
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val classRefs = PsiTreeUtil.findChildrenOfType(psiFile, PascalClassTypeReference::class.java)

        assertTrue("Should find at least one class type reference", classRefs.isNotEmpty())

        val classRef = classRefs.first()
        assertEquals("Class name should be TMyClass", "TMyClass", classRef.className)
    }

    @Test
    fun testMultipleMethodImplementations() {
        val code = """
            unit Test;
            interface
            type
              TMyClass = class
                procedure First;
                procedure Second;
                function Third: Integer;
              end;
            implementation
            procedure TMyClass.First;
            begin
            end;
            procedure TMyClass.Second;
            begin
            end;
            function TMyClass.Third: Integer;
            begin
              Result := 0;
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val classRefs = PsiTreeUtil.findChildrenOfType(psiFile, PascalClassTypeReference::class.java)

        // Should find 3 class type references (one for each method implementation)
        assertTrue("Should find at least 3 class type references", classRefs.size >= 3)

        // All should reference TMyClass
        for (classRef in classRefs) {
            assertEquals("Class name should be TMyClass", "TMyClass", classRef.className)
        }
    }

    @Test
    fun testDifferentClasses() {
        val code = """
            unit Test;
            interface
            type
              TClassA = class
                procedure MethodA;
              end;
              TClassB = class
                procedure MethodB;
              end;
            implementation
            procedure TClassA.MethodA;
            begin
            end;
            procedure TClassB.MethodB;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val classRefs = PsiTreeUtil.findChildrenOfType(psiFile, PascalClassTypeReference::class.java)

        assertTrue("Should find at least 2 class type references", classRefs.size >= 2)

        val classNames = classRefs.map { it.className }
        assertTrue("Should contain TClassA", classNames.any { it == "TClassA" })
        assertTrue("Should contain TClassB", classNames.any { it == "TClassB" })
    }

    @Test
    fun testConstructorImplementation() {
        val code = """
            unit Test;
            interface
            type
              TMyClass = class
                constructor Create;
              end;
            implementation
            constructor TMyClass.Create;
            begin
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val classRefs = PsiTreeUtil.findChildrenOfType(psiFile, PascalClassTypeReference::class.java)

        assertTrue("Should find at least one class type reference", classRefs.isNotEmpty())

        val classRef = classRefs.first()
        assertEquals("Class name should be TMyClass", "TMyClass", classRef.className)
    }

    @Test
    fun testDestructorImplementation() {
        val code = """
            unit Test;
            interface
            type
              TMyClass = class
                destructor Destroy; override;
              end;
            implementation
            destructor TMyClass.Destroy;
            begin
              inherited;
            end;
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        val classRefs = PsiTreeUtil.findChildrenOfType(psiFile, PascalClassTypeReference::class.java)

        assertTrue("Should find at least one class type reference", classRefs.isNotEmpty())

        val classRef = classRefs.first()
        assertEquals("Class name should be TMyClass", "TMyClass", classRef.className)
    }
}
