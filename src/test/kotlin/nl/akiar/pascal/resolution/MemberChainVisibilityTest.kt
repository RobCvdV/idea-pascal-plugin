package nl.akiar.pascal.resolution

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalProperty
import org.junit.Test

class MemberChainVisibilityTest : BasePlatformTestCase() {

    @Test
    fun testPrivateMemberInaccessible() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              private
                FValue: Integer;
                procedure PrivateProc;
              public
                PublicValue: Integer;
              end;
            implementation
            procedure TMyClass.PrivateProc; begin end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TMyClass;
            begin
              Obj.FValue<caret> := 1;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        val result = MemberChainResolver.resolveChain(element!!)

        // Currently, it probably DOES resolve it because there are no visibility checks.
        // Once we implement visibility checks, this should be null.
        assertNull("Private member FValue should NOT be resolved from another unit", result.resolvedElements[1])
    }

    @Test
    fun testProtectedMemberInaccessibleFromOtherUnit() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TBase = class
              protected
                FProtected: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TBase;
            begin
              Obj.FProtected<caret> := 1;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        val result = MemberChainResolver.resolveChain(element!!)

        assertNull("Protected member should NOT be accessible from another unit if not in a subclass", result.resolvedElements[1])
    }

    @Test
    fun testPublicMemberAccessible() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              public
                PublicValue: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var
              Obj: TMyClass;
            begin
              Obj.PublicValue<caret> := 1;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        val result = MemberChainResolver.resolveChain(element!!)

        assertNotNull("Public member should be accessible", result.resolvedElements[1])
    }

    private fun findIdentifierAtCaret(file: com.intellij.psi.PsiFile): com.intellij.psi.PsiElement? {
        val caretOffset = myFixture.caretOffset
        var element = file.findElementAt(caretOffset)
        if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) return element
        if (caretOffset > 0) {
            element = file.findElementAt(caretOffset - 1)
            if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) return element
        }
        return null
    }
}
