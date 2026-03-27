package nl.akiar.pascal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.TypeKind
import org.junit.Test

/**
 * Tests for PascalImplementationSearcher — the core Go to Implementation (Cmd+Opt+B)
 * searcher that handles Cases A/B/B2/C/C2.
 */
class PascalImplementationSearcherTest : BasePlatformTestCase() {

    private fun findImplementations(element: PsiElement): List<PsiElement> {
        return DefinitionsScopedSearch.search(element).toList()
    }

    // ---- Case A: Interface TYPE → find implementing classes ----

    @Test
    fun testInterfaceTypeFindsImplementingClasses() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IFoo = interface
                procedure DoFoo;
              end;
              TFoo = class(TObject, IFoo)
              public
                procedure DoFoo;
              end;
            implementation
            procedure TFoo.DoFoo; begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val iFoo = types.first { it.name.equals("IFoo", ignoreCase = true) }
        assertEquals(TypeKind.INTERFACE, iFoo.typeKind)

        val results = findImplementations(iFoo)
        assertTrue("Should find at least one implementing class", results.isNotEmpty())

        val classResults = results.filterIsInstance<PascalTypeDefinition>()
        assertTrue("Should find TFoo", classResults.any { it.name.equals("TFoo", ignoreCase = true) })
    }

    @Test
    fun testInterfaceTypeMultipleImplementors() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IWorker = interface
                procedure Work;
              end;
              TWorkerA = class(TObject, IWorker)
              public
                procedure Work;
              end;
              TWorkerB = class(TObject, IWorker)
              public
                procedure Work;
              end;
            implementation
            procedure TWorkerA.Work; begin end;
            procedure TWorkerB.Work; begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val iWorker = types.first { it.name.equals("IWorker", ignoreCase = true) }

        val results = findImplementations(iWorker)
        val classResults = results.filterIsInstance<PascalTypeDefinition>()
        assertTrue("Should find TWorkerA", classResults.any { it.name.equals("TWorkerA", ignoreCase = true) })
        assertTrue("Should find TWorkerB", classResults.any { it.name.equals("TWorkerB", ignoreCase = true) })
    }

    // ---- Case B: Interface METHOD → find implementing methods ----

    @Test
    fun testInterfaceMethodFindsImplementations() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IRepo = interface
                procedure DoWork;
              end;
              TRepo = class(TObject, IRepo)
              public
                procedure DoWork;
              end;
            implementation
            procedure TRepo.DoWork; begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val iRepo = types.first { it.name.equals("IRepo", ignoreCase = true) }
        val interfaceMethod = iRepo.methods.first { it.name.equals("DoWork", ignoreCase = true) }

        val results = findImplementations(interfaceMethod)
        assertTrue("Should find at least one implementation", results.isNotEmpty())

        val routineResults = results.filterIsInstance<PascalRoutine>()
        assertTrue("Should find DoWork implementation", routineResults.any {
            it.name.equals("DoWork", ignoreCase = true)
        })
    }

    @Test
    fun testInterfaceMethodOverloadMatching() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IRepo = interface
                procedure ByIds(A: Integer);
                procedure ByIds(A: Integer; B: String);
              end;
              TRepo = class(TObject, IRepo)
              public
                procedure ByIds(A: Integer);
                procedure ByIds(A: Integer; B: String);
              end;
            implementation
            procedure TRepo.ByIds(A: Integer); begin end;
            procedure TRepo.ByIds(A: Integer; B: String); begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val iRepo = types.first { it.name.equals("IRepo", ignoreCase = true) }

        // Test single-param overload
        val singleParamMethod = iRepo.methods.first {
            it.name.equals("ByIds", ignoreCase = true) && it.getSignatureHash().equals("integer;", ignoreCase = true)
        }
        val singleResults = findImplementations(singleParamMethod)
        val singleRoutines = singleResults.filterIsInstance<PascalRoutine>()
        assertTrue("Should find single-param implementation", singleRoutines.isNotEmpty())
        // The signature-matched result should have matching signature
        assertTrue("Should match single-param overload", singleRoutines.any {
            it.name.equals("ByIds", ignoreCase = true)
        })

        // Test two-param overload
        val twoParamMethod = iRepo.methods.first {
            it.name.equals("ByIds", ignoreCase = true) && it.getSignatureHash().equals("integer;string;", ignoreCase = true)
        }
        val twoResults = findImplementations(twoParamMethod)
        val twoRoutines = twoResults.filterIsInstance<PascalRoutine>()
        assertTrue("Should find two-param implementation", twoRoutines.isNotEmpty())
    }

    // ---- Case B2: Class METHOD → trace back to interface ----

    @Test
    fun testClassMethodTracesBackToInterface() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IWorker = interface
                procedure DoWork;
              end;
              TWorkerA = class(TObject, IWorker)
              public
                procedure DoWork;
              end;
              TWorkerB = class(TObject, IWorker)
              public
                procedure DoWork;
              end;
            implementation
            procedure TWorkerA.DoWork; begin end;
            procedure TWorkerB.DoWork; begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val tWorkerA = types.first { it.name.equals("TWorkerA", ignoreCase = true) }
        val classMethod = tWorkerA.methods.first { it.name.equals("DoWork", ignoreCase = true) }

        val results = findImplementations(classMethod)
        val routineResults = results.filterIsInstance<PascalRoutine>()
        // B2 traces back to interface, so should find implementations from both TWorkerA and TWorkerB
        assertTrue("Should find implementations via interface", routineResults.isNotEmpty())
    }

    // ---- Case C: Interface PROPERTY → find implementing properties ----

    @Test
    fun testInterfacePropertyFindsImplementations() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IFoo = interface
                property Name: String read GetName;
              end;
              TFoo = class(TObject, IFoo)
              public
                property Name: String read GetName;
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val iFoo = types.first { it.name.equals("IFoo", ignoreCase = true) }
        val interfaceProp = iFoo.properties.first { it.name.equals("Name", ignoreCase = true) }

        val results = findImplementations(interfaceProp)
        val propResults = results.filterIsInstance<PascalProperty>()
        assertTrue("Should find Name property implementation", propResults.any {
            it.name.equals("Name", ignoreCase = true)
        })
    }

    // ---- Case C2: Class PROPERTY → trace back to interface ----

    @Test
    fun testClassPropertyTracesBackToInterface() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IFoo = interface
                property Name: String read GetName;
              end;
              TFooA = class(TObject, IFoo)
              public
                property Name: String read GetName;
              end;
              TFooB = class(TObject, IFoo)
              public
                property Name: String read GetName;
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val tFooA = types.first { it.name.equals("TFooA", ignoreCase = true) }
        val classProp = tFooA.properties.first { it.name.equals("Name", ignoreCase = true) }

        val results = findImplementations(classProp)
        val propResults = results.filterIsInstance<PascalProperty>()
        // C2 traces back to interface, so should find properties from sibling implementors
        assertTrue("Should find property implementations via interface", propResults.isNotEmpty())
    }

    // ---- Negative: non-interface type returns no results ----

    @Test
    fun testNonInterfaceTypeReturnsNoResults() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TPlain = class
              public
                procedure DoWork;
              end;
            implementation
            procedure TPlain.DoWork; begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val tPlain = types.first { it.name.equals("TPlain", ignoreCase = true) }
        val method = tPlain.methods.first { it.name.equals("DoWork", ignoreCase = true) }

        val typeResults = findImplementations(tPlain)
        assertTrue("Regular class should have no implementations", typeResults.isEmpty())

        val methodResults = findImplementations(method)
        assertTrue("Regular class method should have no implementations", methodResults.isEmpty())
    }

    private fun PascalRoutine.getSignatureHash(): String {
        return (this as nl.akiar.pascal.psi.impl.PascalRoutineImpl).signatureHash
    }
}
