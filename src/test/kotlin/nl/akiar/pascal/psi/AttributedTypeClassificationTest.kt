package nl.akiar.pascal.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for type classification when attributes are present.
 * Ensures that LPAREN tokens in attributes don't cause misclassification as ENUM.
 */
class AttributedTypeClassificationTest : BasePlatformTestCase() {

    @Test
    fun testClassWithAttribute() {
        val code = """
            unit Test;
            interface
            type
              [BaseUrl('/rides')]
              TRideResource = class
              private
                FRideRepository: IRideRepository;
              public
                constructor Create(const ARideRepository: IRideRepository);
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        val typeDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       PascalTypeDefinition::class.java)

        val rideResource = typeDefs.firstOrNull { it.name == "TRideResource" }
        assertNotNull("Should find TRideResource type definition", rideResource)

        // The critical check: should be CLASS, not ENUM
        assertEquals("TRideResource should be classified as CLASS (not ENUM due to attribute LPAREN)",
                    TypeKind.CLASS,
                    rideResource?.typeKind)
    }

    @Test
    fun testRecordWithAttribute() {
        val code = """
            unit Test;
            interface
            type
              [SomeAttribute('value', 123)]
              TMyRecord = record
                X: Integer;
                Y: String;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        val typeDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       PascalTypeDefinition::class.java)

        val myRecord = typeDefs.firstOrNull { it.name == "TMyRecord" }
        assertNotNull("Should find TMyRecord type definition", myRecord)

        assertEquals("TMyRecord should be classified as RECORD",
                    TypeKind.RECORD,
                    myRecord?.typeKind)
    }

    @Test
    fun testInterfaceWithAttribute() {
        val code = """
            unit Test;
            interface
            type
              [Guid('{12345678-1234-1234-1234-123456789ABC}')]
              IMyInterface = interface
                procedure DoSomething;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        val typeDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       PascalTypeDefinition::class.java)

        val myInterface = typeDefs.firstOrNull { it.name == "IMyInterface" }
        assertNotNull("Should find IMyInterface type definition", myInterface)

        assertEquals("IMyInterface should be classified as INTERFACE",
                    TypeKind.INTERFACE,
                    myInterface?.typeKind)
    }

    @Test
    fun testActualEnumStillWorks() {
        val code = """
            unit Test;
            interface
            type
              TColor = (Red, Green, Blue);
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        val typeDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       PascalTypeDefinition::class.java)

        val colorType = typeDefs.firstOrNull { it.name == "TColor" }
        assertNotNull("Should find TColor type definition", colorType)

        // This SHOULD be classified as ENUM (has actual LPAREN for enum values)
        assertEquals("TColor should be classified as ENUM",
                    TypeKind.ENUM,
                    colorType?.typeKind)
    }

    @Test
    fun testClassWithoutAttribute() {
        val code = """
            unit Test;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.pas", code)
        assertNotNull("File should parse", psiFile)

        val typeDefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile,
                                                       PascalTypeDefinition::class.java)

        val myClass = typeDefs.firstOrNull { it.name == "TMyClass" }
        assertNotNull("Should find TMyClass type definition", myClass)

        assertEquals("TMyClass should be classified as CLASS",
                    TypeKind.CLASS,
                    myClass?.typeKind)
    }
}

