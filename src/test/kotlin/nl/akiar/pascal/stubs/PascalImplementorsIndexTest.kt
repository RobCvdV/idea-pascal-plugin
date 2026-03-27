package nl.akiar.pascal.stubs

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.TypeKind
import org.junit.Test

class PascalImplementorsIndexTest : BasePlatformTestCase() {

    // ---- Phase 1: getAllAncestorNames extraction ----

    @Test
    fun testAllAncestorNamesExtraction() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IFoo = interface
                procedure DoFoo;
              end;
              IBar = interface
                procedure DoBar;
              end;
              TBase = class
              public
                procedure BaseMethod;
              end;
              TFoo = class(TBase, IFoo, IBar)
              public
                procedure DoFoo;
                procedure DoBar;
              end;
            implementation
            procedure TBase.BaseMethod; begin end;
            procedure TFoo.DoFoo; begin end;
            procedure TFoo.DoBar; begin end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val tFoo = types.first { it.name.equals("TFoo", ignoreCase = true) }

        val ancestors = tFoo.allAncestorNames
        assertEquals("Should have 3 ancestors", 3, ancestors.size)
        assertEquals("TBase", ancestors[0])
        assertEquals("IFoo", ancestors[1])
        assertEquals("IBar", ancestors[2])
    }

    @Test
    fun testSuperClassNameDerivedFromAncestorList() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TFoo = class(TBar, IFoo)
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val tFoo = types.first { it.name.equals("TFoo", ignoreCase = true) }

        assertEquals("getSuperClassName should return first ancestor", "TBar", tFoo.superClassName)
    }

    @Test
    fun testNoAncestors() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              TSimple = class
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val tSimple = types.first { it.name.equals("TSimple", ignoreCase = true) }

        assertTrue("No ancestors expected", tSimple.allAncestorNames.isEmpty())
        assertNull("No superclass expected", tSimple.superClassName)
    }

    @Test
    fun testInterfaceWithParent() {
        val file = myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IBase = interface
              end;
              IChild = interface(IBase)
                procedure ChildMethod;
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val iChild = types.first { it.name.equals("IChild", ignoreCase = true) }

        val ancestors = iChild.allAncestorNames
        assertEquals("Should have 1 ancestor", 1, ancestors.size)
        assertEquals("IBase", ancestors[0])
    }

    // ---- Phase 2: Implementors index lookup ----

    @Test
    fun testImplementorsIndex() {
        myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IMyInterface = interface
                procedure DoSomething;
              end;
              TImplA = class(TObject, IMyInterface)
              public
                procedure DoSomething;
              end;
              TImplB = class(TObject, IMyInterface)
              public
                procedure DoSomething;
              end;
              TUnrelated = class(TObject)
              end;
            implementation
            procedure TImplA.DoSomething; begin end;
            procedure TImplB.DoSomething; begin end;
            end.
        """.trimIndent())

        val implementors = PascalImplementorsIndex.findImplementors("imyinterface", project)
        // The index key is lowercase simple name; both TImplA and TImplB should appear
        val classImplementors = implementors.filter { it.typeKind == TypeKind.CLASS }
        assertTrue("Should find at least 2 implementing classes", classImplementors.size >= 2)

        val names = classImplementors.map { it.name }
        assertTrue("TImplA should be found", names.any { it.equals("TImplA", ignoreCase = true) })
        assertTrue("TImplB should be found", names.any { it.equals("TImplB", ignoreCase = true) })
        assertFalse("TUnrelated should NOT appear", names.any { it.equals("TUnrelated", ignoreCase = true) })
    }

    @Test
    fun testSingleImplementor() {
        myFixture.configureByText("TestUnit.pas", """
            unit TestUnit;
            interface
            type
              IWorker = interface
                procedure Work;
              end;
              TMyWorker = class(TObject, IWorker)
              public
                procedure Work;
              end;
            implementation
            procedure TMyWorker.Work; begin end;
            end.
        """.trimIndent())

        val implementors = PascalImplementorsIndex.findImplementors("iworker", project)
        val classImpls = implementors.filter { it.typeKind == TypeKind.CLASS }
        assertEquals("Should find exactly 1 implementing class", 1, classImpls.size)
        assertEquals("TMyWorker", classImpls.first().name)
    }

    @Test
    fun testCrossUnitImplementor() {
        // Interface defined in one unit, implementor in another
        myFixture.configureByText("Interfaces.pas", """
            unit Interfaces;
            interface
            type
              ILogger = interface
                procedure Log(const Msg: string);
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Implementations.pas", """
            unit Implementations;
            interface
            uses Interfaces;
            type
              TConsoleLogger = class(TObject, ILogger)
              public
                procedure Log(const Msg: string);
              end;
            implementation
            procedure TConsoleLogger.Log(const Msg: string); begin end;
            end.
        """.trimIndent())

        val implementors = PascalImplementorsIndex.findImplementors("ilogger", project)
        val classImpls = implementors.filter { it.typeKind == TypeKind.CLASS }
        assertTrue("Should find the cross-unit implementor", classImpls.any {
            it.name.equals("TConsoleLogger", ignoreCase = true)
        })
    }
}
