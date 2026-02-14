package nl.akiar.pascal.resolution

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalVariableDefinition
import org.junit.Test

class InlineVarAstTest : BasePlatformTestCase() {

    @Test
    fun testInlineVarTypeInference() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TFileMasks = class
              public
                procedure Apply;
              end;
            function GetFileMaskFilter(AOrderId: Integer): TFileMasks;
            implementation
            function GetFileMaskFilter(AOrderId: Integer): TFileMasks;
            begin
              Result := nil;
            end;
            procedure TFileMasks.Apply; begin end;
            procedure DoTest;
            begin
              var LFilter := GetFileMaskFilter(42);
              LFilter.Apply;
            end;
            end.
        """.trimIndent())

        // Find LFilter variable definition
        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lFilter = vars.firstOrNull { it.name == "LFilter" }
        assertNotNull("LFilter should be found as a variable definition", lFilter)

        // Verify no explicit type
        assertNull("LFilter should have no explicit type", lFilter!!.typeName)

        // Verify type inference works
        val inferredType = MemberChainResolver.getInferredTypeOf(lFilter, file)
        assertNotNull("Should infer type from GetFileMaskFilter return type", inferredType)
        assertEquals("Inferred type should be TFileMasks", "TFileMasks", inferredType!!.name)
    }

    @Test
    fun testInlineVarTypeInferenceCrossUnit() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TStatus = class
              public
                procedure Check;
              end;
            function GetStatus: TStatus;
            implementation
            function GetStatus: TStatus;
            begin
              Result := nil;
            end;
            procedure TStatus.Check; begin end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses Types;
            implementation
            procedure DoTest;
            begin
              var LStatus := GetStatus;
              LStatus.Check;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lStatus = vars.firstOrNull { it.name == "LStatus" }
        assertNotNull("LStatus should be found", lStatus)
        assertNull("LStatus should have no explicit type", lStatus!!.typeName)

        val inferredType = MemberChainResolver.getInferredTypeOf(lStatus, file)
        assertNotNull("Should infer type from GetStatus return type", inferredType)
        assertEquals("Inferred type should be TStatus", "TStatus", inferredType!!.name)
    }
}
