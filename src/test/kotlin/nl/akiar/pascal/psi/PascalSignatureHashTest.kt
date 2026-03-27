package nl.akiar.pascal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.impl.PascalRoutineImpl
import org.junit.Test

/**
 * Tests for getSignatureHash() and getOwnParameters() on PascalRoutineImpl.
 * Validates the critical fix that only scans the routine's own FORMAL_PARAMETER_LIST,
 * not parameters from nested routines/lambdas.
 */
class PascalSignatureHashTest : BasePlatformTestCase() {

    private fun findRoutine(code: String, routineName: String, isImpl: Boolean = false): PascalRoutineImpl {
        val file = myFixture.configureByText("TestUnit.pas", code)
        val routines = PsiTreeUtil.findChildrenOfType(file, PascalRoutineImpl::class.java)
        return routines.first {
            it.name.equals(routineName, ignoreCase = true) && it.isImplementation == isImpl
        }
    }

    @Test
    fun testSignatureHashSimpleParams() {
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure DoWork(A: Integer; B: String);
              end;
            implementation
            procedure TFoo.DoWork(A: Integer; B: String); begin end;
            end.
        """.trimIndent()

        val routine = findRoutine(code, "DoWork", isImpl = false)
        assertEquals("integer;string;", routine.signatureHash)
    }

    @Test
    fun testSignatureHashNoParams() {
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure DoWork;
              end;
            implementation
            procedure TFoo.DoWork; begin end;
            end.
        """.trimIndent()

        val routine = findRoutine(code, "DoWork", isImpl = false)
        assertEquals("", routine.signatureHash)
    }

    @Test
    fun testSignatureHashExcludesNestedRoutineParams() {
        // Implementation body contains a nested anonymous method with its own parameters.
        // getSignatureHash() must only include outer routine's params (A: Integer; B: String).
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure DoWork(A: Integer; B: String);
              end;
            implementation
            procedure TFoo.DoWork(A: Integer; B: String);
            var
              F: TFunc<Integer, String>;
            begin
              F := function(X: Integer): String
              begin
                Result := IntToStr(X);
              end;
            end;
            end.
        """.trimIndent()

        // Test the implementation body (which has nested routine)
        val implRoutine = findRoutine(code, "DoWork", isImpl = true)
        assertEquals("Impl should only have outer params", "integer;string;", implRoutine.signatureHash)

        // Test the declaration (no nested routines)
        val declRoutine = findRoutine(code, "DoWork", isImpl = false)
        assertEquals("Decl should have same hash", "integer;string;", declRoutine.signatureHash)
    }

    @Test
    fun testGetOwnParametersExcludesNested() {
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure DoWork(A: Integer; B: String);
              end;
            implementation
            procedure TFoo.DoWork(A: Integer; B: String);
            var
              F: TFunc<Integer, String>;
            begin
              F := function(X: Integer): String
              begin
                Result := IntToStr(X);
              end;
            end;
            end.
        """.trimIndent()

        val implRoutine = findRoutine(code, "DoWork", isImpl = true)
        // getOwnParameters() is package-private, so we test it indirectly via signatureHash.
        // If getOwnParameters() included nested params, the hash would be longer than "integer;string;"
        val hash = implRoutine.signatureHash
        assertEquals("Should only include outer params (2 semicolons = 2 params)", "integer;string;", hash)
        // Count semicolons to verify param count
        assertEquals("Should have exactly 2 params", 2, hash.count { it == ';' })
    }

    @Test
    fun testSignatureHashMatchesBetweenDeclAndImpl() {
        val code = """
            unit TestUnit;
            interface
            type
              TFoo = class
              public
                procedure Process(const Name: String; Value: Integer);
              end;
            implementation
            procedure TFoo.Process(const Name: String; Value: Integer);
            begin
            end;
            end.
        """.trimIndent()

        val decl = findRoutine(code, "Process", isImpl = false)
        val impl = findRoutine(code, "Process", isImpl = true)
        assertEquals("Declaration and implementation should have same signature hash",
            decl.signatureHash, impl.signatureHash)
    }
}
