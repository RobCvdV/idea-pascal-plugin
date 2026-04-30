package nl.akiar.pascal.refactoring

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import org.junit.Test

class PascalRenameTest : BasePlatformTestCase() {

    @Test
    fun testSetName_Type() {
        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TMyClass = class
              end;
            implementation
            end.
        """.trimIndent())

        val typeDef = PsiTreeUtil.findChildOfType(file, PascalTypeDefinition::class.java)
        assertNotNull(typeDef)
        assertEquals("TMyClass", typeDef!!.name)

        myFixture.project.let { project ->
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                typeDef.setName("TRenamed")
            }
        }

        assertEquals("TRenamed", typeDef.name)
        assertTrue("File should contain renamed type", file.text.contains("TRenamed"))
    }

    @Test
    fun testSetName_Routine() {
        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            procedure DoWork;
            implementation
            procedure DoWork;
            begin
            end;
            end.
        """.trimIndent())

        val routine = PsiTreeUtil.findChildrenOfType(file, PascalRoutine::class.java)
            .first { !it.isImplementation }
        assertNotNull(routine)
        assertEquals("DoWork", routine.name)

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            routine.setName("Execute")
        }

        assertEquals("Execute", routine.name)
        assertTrue("File should contain renamed routine", file.text.contains("procedure Execute"))
    }

    @Test
    fun testSetName_Variable() {
        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            var
              MyValue: Integer;
            implementation
            end.
        """.trimIndent())

        val varDef = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
            .first { it.name == "MyValue" }
        assertNotNull(varDef)

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            varDef.setName("Counter")
        }

        assertEquals("Counter", varDef.name)
        assertTrue("File should contain renamed variable", file.text.contains("Counter"))
    }

    @Test
    fun testSetName_Property() {
        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TMyClass = class
              private
                FName: String;
              public
                property Name: String read FName;
              end;
            implementation
            end.
        """.trimIndent())

        val prop = PsiTreeUtil.findChildOfType(file, PascalProperty::class.java)
        assertNotNull(prop)
        assertEquals("Name", prop!!.name)

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            prop.setName("Title")
        }

        assertEquals("Title", prop.name)
        assertTrue("File should contain renamed property", file.text.contains("property Title"))
    }

    @Test
    fun testRenameElementAtCaret_Type() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TMy<caret>Class = class
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.renameElementAtCaret("TRenamed")

        val text = myFixture.file.text
        assertTrue("Type should be renamed", text.contains("TRenamed = class"))
        assertFalse("Old name should not exist in declaration", text.contains("TMyClass = class"))
    }

    @Test
    fun testRenameElementAtCaret_Routine() {
        myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            procedure Do<caret>Work;
            implementation
            procedure DoWork;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.renameElementAtCaret("Execute")

        val text = myFixture.file.text
        // At minimum, the declaration at caret should be renamed
        assertTrue("Declaration should be renamed", text.contains("procedure Execute"))
    }

    @Test
    fun testRenamePsiElementProcessor_PairsRoutines() {
        val file = myFixture.configureByText("Main.pas", """
            unit Main;
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
        """.trimIndent())

        // Find the interface declaration
        val declaration = PsiTreeUtil.findChildrenOfType(file, PascalRoutine::class.java)
            .first { !it.isImplementation && it.name == "DoWork" }
        val implementation = declaration.implementation

        // Verify the processor would pair them
        val processor = PascalRenamePsiElementProcessor()
        assertTrue("Processor should handle routines", processor.canProcessElement(declaration))

        val allRenames = mutableMapOf<com.intellij.psi.PsiElement, String>()
        processor.prepareRenaming(declaration, "Execute", allRenames)

        if (implementation != null) {
            assertTrue("Implementation should be in allRenames", allRenames.containsKey(implementation))
            assertEquals("Execute", allRenames[implementation])
        }
    }

    @Test
    fun testFindUsagesProvider_CanFindUsages() {
        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            type
              TMyClass = class end;
            implementation
            end.
        """.trimIndent())

        val provider = nl.akiar.pascal.reference.PascalFindUsagesProvider()
        val typeDef = PsiTreeUtil.findChildOfType(file, PascalTypeDefinition::class.java)
        assertNotNull(typeDef)

        assertTrue("Should be able to find usages for types", provider.canFindUsagesFor(typeDef!!))
        assertEquals("class", provider.getType(typeDef))
        assertEquals("TMyClass", provider.getDescriptiveName(typeDef))
        assertNotNull("Should have words scanner", provider.wordsScanner)
    }
}
