package nl.akiar.pascal.resolution

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import org.junit.Test

class FormalParameterChainTest : BasePlatformTestCase() {

    @Test
    fun testFormalParameterAsChainStart() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TTask = class
                function RideId: Integer;
              end;
            implementation
            function TTask.RideId: Integer; begin Result := 0; end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            type
              TFillTaskInfo = class
                procedure Fill(const ATask: TTask);
              end;
            implementation
            procedure TFillTaskInfo.Fill(const ATask: TTask);
            begin
              ATask.RideId<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find element at caret", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain: ${result.chainElements.map { it.text }}")
        println("Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<NULL>" }}")
        println("Fully resolved: ${result.isFullyResolved}")

        assertNotNull("ATask should resolve (index 0)", result.resolvedElements.getOrNull(0))
        assertNotNull("RideId should resolve (index 1)", result.resolvedElements.getOrNull(1))
    }

    @Test
    fun testConstParameterVariableIndex() {
        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            procedure DoWork(const AValue: Integer);
            begin
              AValue<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(file)
        assertNotNull(element)

        val found = nl.akiar.pascal.stubs.PascalVariableIndex.findVariableAtPosition(
            "AValue", file, element!!.textOffset
        )
        println("findVariableAtPosition result: ${found?.javaClass?.simpleName ?: "<NULL>"} name=${found?.name} kind=${found?.variableKind}")
        assertNotNull("Should find const parameter AValue", found)
    }

    @Test
    fun testParameterChain_FieldWithAttributes() {
        // Matches real pattern: TTask = class(TEntity) with [Map] attribute fields
        myFixture.configureByText("Entity.pas", """
            unit Entity;
            interface
            type
              TEntity = class
              public
                Id: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Task.pas", """
            unit Task;
            interface
            uses Entity;
            type
              MapAttribute = class end;
              TTask = class(TEntity)
              public
                [Map] RideId: Integer;
                [Map] TrailerId: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Task;
            type
              TFiller = class
                procedure Fill(const ATask: TTask);
              end;
            implementation
            procedure TFiller.Fill(const ATask: TTask);
            begin
              ATask.RideId<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find element at caret", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Attr test - Chain: ${result.chainElements.map { it.text }}")
        println("Attr test - Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<NULL>" }}")

        assertNotNull("ATask should resolve", result.resolvedElements.getOrNull(0))
        assertNotNull("RideId should resolve", result.resolvedElements.getOrNull(1))
    }

    @Test
    fun testParameterChain_InheritedField() {
        // Field from ancestor class
        myFixture.configureByText("Entity.pas", """
            unit Entity;
            interface
            type
              TEntity = class
              public
                Id: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Task.pas", """
            unit Task;
            interface
            uses Entity;
            type
              TTask = class(TEntity)
              public
                Name: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Task;
            implementation
            procedure Test(const ATask: TTask);
            begin
              ATask.Id<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull(element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Inherited test - Chain: ${result.chainElements.map { it.text }}")
        println("Inherited test - Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<NULL>" }}")

        assertNotNull("ATask should resolve", result.resolvedElements.getOrNull(0))
        assertNotNull("Id (inherited) should resolve", result.resolvedElements.getOrNull(1))
    }

    @Test
    fun testParameterChain_NestedInIfBlock() {
        // Parameter used deep inside nested if/begin blocks (like the real FillEoRideAndPackaging)
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TTask = class
              public
                RideId: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            type
              TFiller = class
                procedure Fill(const ATask: TTask);
              end;
            implementation
            procedure TFiller.Fill(const ATask: TTask);
            begin
              if ATask.RideId<caret> > 0 then begin
              end;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull(element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Nested test - Chain: ${result.chainElements.map { it.text }}")
        println("Nested test - Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<NULL>" }}")

        assertNotNull("ATask should resolve", result.resolvedElements.getOrNull(0))
        assertNotNull("RideId should resolve", result.resolvedElements.getOrNull(1))
    }

    private fun findIdentifierAtCaret(file: com.intellij.psi.PsiFile): PsiElement? {
        val caretOffset = myFixture.caretOffset
        for (offset in 0..3) {
            if (caretOffset - offset >= 0) {
                val el = file.findElementAt(caretOffset - offset)
                if (el != null && el.node.elementType == PascalTokenTypes.IDENTIFIER) return el
            }
        }
        return null
    }
}
