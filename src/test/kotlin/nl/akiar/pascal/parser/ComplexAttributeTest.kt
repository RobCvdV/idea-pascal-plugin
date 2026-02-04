package nl.akiar.pascal.parser

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalAttribute
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import org.junit.Test

/**
 * Test complex attribute scenarios from real-world code.
 */
class ComplexAttributeTest : BasePlatformTestCase() {

    @Test
    fun testComplexClassWithMethodAttributes() {
        val code = """
            unit TestUnit;
            interface
            type
              [BaseUrl('/clients')]
              TClientResource = class
              private
                FPackagingBalancesRepository: IPackagingBalancesRepository;
                FGetClaimsFromAuthorizationHeader: IGetClaimsFromAuthorizationHeader;
              public
                constructor Create(
                  const APackagingBalancesRepository: IPackagingBalancesRepository;
                  const AGetClaimsFromAuthorizationHeader: IGetClaimsFromAuthorizationHeader);

                [Authenticate([driver, apitoken])]
                [Path(rmGet, '/packaging-balances')]
                [QueryParameter('ids', 'string')]
                [Summary('Packaging balances by client ids')]
                [Description('Get packaging balances by client ids.')]
                [ResultType(TPackagingBalanceListClient)]
                function PackagingBalancesByClientIds(const ARequest: IHttpRequest): IPromise<TStructList<TPackagingBalanceListClient>>;
              end;
            implementation
            end.
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.pas", code)

        // Print the full PSI tree for debugging
        val psiTree = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, true)
        }
        println("=== FULL PSI TREE ===")
        println(psiTree)
        println("=== END PSI TREE ===")

        // Find all types and check their names
        val types = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalTypeDefinition::class.java)
        }
        println("\n=== TYPE DEFINITIONS ===")
        types.forEach { t ->
            println("Type: ${t.name} (${t.typeKind})")
            println("  Attributes: ${t.attributes.map { it.name }}")
        }

        // Find all routines
        val routines = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalRoutine::class.java)
        }
        println("\n=== ROUTINES ===")
        routines.forEach { r ->
            println("Routine: ${r.name}")
            println("  Text start: ${r.text.take(100)}...")
            // Check attributes
            val attrs = com.intellij.openapi.application.runReadAction {
                PsiTreeUtil.findChildrenOfType(r, PascalAttribute::class.java)
            }
            println("  Attributes inside routine: ${attrs.map { it.name }}")
        }

        // Find all attributes
        val allAttrs = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(psiFile, PascalAttribute::class.java)
        }
        println("\n=== ALL ATTRIBUTES ===")
        allAttrs.forEach { a ->
            println("Attribute: ${a.name}")
            println("  Arguments: ${a.arguments}")
            println("  Text: ${a.text}")
        }

        // Assertions
        assertEquals("Should have 1 type definition", 1, types.size)
        assertEquals("Type name should be 'TClientResource'", "TClientResource", types.first().name)

        // The class should have 1 attribute: BaseUrl
        val classAttrs = types.first().attributes
        assertEquals("Class should have 1 attribute", 1, classAttrs.size)
        assertEquals("Class attribute should be 'BaseUrl'", "BaseUrl", classAttrs.first().name)

        // Find the function (not constructor)
        val function = routines.find { it.name == "PackagingBalancesByClientIds" }
        assertNotNull("Should find PackagingBalancesByClientIds function", function)

        // The function should have 6 attributes
        val funcAttrs = com.intellij.openapi.application.runReadAction {
            PsiTreeUtil.findChildrenOfType(function!!, PascalAttribute::class.java)
        }
        println("\n=== FUNCTION ATTRIBUTES ===")
        funcAttrs.forEach { println("  - ${it.name}") }

        assertEquals("Function should have 6 attributes", 6, funcAttrs.size)
        val funcAttrNames = funcAttrs.map { it.name }
        assertTrue("Should have Authenticate attr", funcAttrNames.contains("Authenticate"))
        assertTrue("Should have Path attr", funcAttrNames.contains("Path"))
        assertTrue("Should have QueryParameter attr", funcAttrNames.contains("QueryParameter"))
        assertTrue("Should have Summary attr", funcAttrNames.contains("Summary"))
        assertTrue("Should have Description attr", funcAttrNames.contains("Description"))
        assertTrue("Should have ResultType attr", funcAttrNames.contains("ResultType"))
    }
}
