package nl.akiar.pascal.reference

import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import org.junit.Test

class PascalFindUsagesTest : BasePlatformTestCase() {

    @Test
    fun testDiagnoseReferenceChain() {
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
            procedure Test;
            var
              Obj: TMyClass;
            begin
              Obj.DoWork;
            end;
            end.
        """.trimIndent())

        // Find the declaration of DoWork
        val declaration = PsiTreeUtil.findChildrenOfType(file, PascalRoutine::class.java)
            .first { !it.isImplementation && it.name == "DoWork" }
        println("Declaration: ${declaration.name} impl=${declaration.isImplementation} class=${declaration.javaClass.simpleName}")
        println("Declaration file: ${declaration.containingFile?.name}")
        println("Declaration useScope: ${declaration.useScope}")

        // Find all identifiers named "DoWork" in the file
        val allDoWorkIds = PsiTreeUtil.findChildrenOfType(file, PsiElement::class.java)
            .filter { it.node?.elementType == PascalTokenTypes.IDENTIFIER && it.text == "DoWork" }
        println("\nAll 'DoWork' identifiers: ${allDoWorkIds.size}")

        for (id in allDoWorkIds) {
            val parent = id.parent
            println("\n  id at offset=${id.textOffset} parent=${parent?.javaClass?.simpleName} parentType=${parent?.node?.elementType}")

            // Check references from the element itself
            val ownRefs = id.references
            println("  element.getReferences(): ${ownRefs.size}")
            for (r in ownRefs) {
                val resolved = r.resolve()
                println("    ref: ${r.javaClass.simpleName} resolves=${resolved?.javaClass?.simpleName} name=${(resolved as? com.intellij.psi.PsiNamedElement)?.name}")
                println("    isReferenceTo(declaration): ${r.isReferenceTo(declaration)}")
            }

            // Check references from providers registry
            val providerRefs = com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry.getReferencesFromProviders(id)
            println("  providerRefs: ${providerRefs.size}")
            for (r in providerRefs) {
                val resolved = r.resolve()
                println("    provRef: ${r.javaClass.simpleName} resolves=${resolved?.javaClass?.simpleName} name=${(resolved as? com.intellij.psi.PsiNamedElement)?.name}")
                println("    isReferenceTo(declaration): ${r.isReferenceTo(declaration)}")
            }
        }

        // Try ReferencesSearch
        val refs = ReferencesSearch.search(declaration).findAll()
        println("\nReferencesSearch results: ${refs.size}")
        for (r in refs) {
            println("  found: ${r.element.text} at ${r.element.textOffset}")
        }

        // The test: we expect at least the callsite
        assertTrue("Should find at least 1 usage of DoWork, found ${refs.size}", refs.isNotEmpty())
    }
}
