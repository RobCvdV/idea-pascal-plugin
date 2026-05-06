package nl.akiar.pascal.resolution

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalTypeDefinition
import com.intellij.psi.util.PsiTreeUtil
import org.junit.Test

class IndexPropertyResolutionTest : BasePlatformTestCase() {

    @Test
    fun testIndexPropertyNameExtraction() {
        val file = myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              private
                function GetToken(AIndex: Integer): Int64;
              public
                property TokenProblemStatuses: Int64 index 1 read GetToken;
                property TokenRegistration: Int64 index 2 read GetToken;
              end;
            implementation
            function TMyClass.GetToken(AIndex: Integer): Int64; begin Result := 0; end;
            end.
        """.trimIndent())

        val typeDef = PsiTreeUtil.findChildOfType(file, PascalTypeDefinition::class.java)
        assertNotNull("Should find type definition", typeDef)

        val members = typeDef!!.getMembers(true)
        val memberNames = members.mapNotNull {
            when (it) {
                is nl.akiar.pascal.psi.PascalProperty -> it.name
                is nl.akiar.pascal.psi.PascalRoutine -> it.name
                is nl.akiar.pascal.psi.PascalVariableDefinition -> it.name
                else -> null
            }
        }
        println("Members found: $memberNames")

        assertTrue("Should find TokenProblemStatuses in members: $memberNames",
            memberNames.any { it.equals("TokenProblemStatuses", ignoreCase = true) })
        assertTrue("Should find TokenRegistration in members: $memberNames",
            memberNames.any { it.equals("TokenRegistration", ignoreCase = true) })
    }

    @Test
    fun testIndexPropertyMemberChainResolution() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              private
                function GetToken(AIndex: Integer): Int64;
              public
                property TokenValue: Int64 index 1 read GetToken;
              end;
            implementation
            function TMyClass.GetToken(AIndex: Integer): Int64; begin Result := 0; end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure Test;
            var Obj: TMyClass;
            begin
              Obj.TokenValue<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find element at caret", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")
        println("Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<null>" }}")

        assertNotNull("TokenValue should resolve", result.resolvedElements.getOrNull(1))
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
