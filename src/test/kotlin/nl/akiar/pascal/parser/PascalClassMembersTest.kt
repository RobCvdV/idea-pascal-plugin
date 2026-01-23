package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.TypeKind
import nl.akiar.pascal.psi.VariableKind

class PascalNavigationTest : BasePlatformTestCase() {

    fun testMethodRecognition() {
        val file = myFixture.configureByText("test.pas", """
            unit test;
            interface
            type
              TMyClass = class
                procedure MyMethod;
                property MyProp: Integer read FProp;
              end;
            implementation
            end.
        """.trimIndent())
        
        val typeDef = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file, PascalTypeDefinition::class.java)
        assertNotNull("Type definition not found", typeDef)
        assertEquals(TypeKind.CLASS, typeDef!!.typeKind)
        
        val methods = typeDef.methods
        assertEquals(1, methods.size)
        assertEquals("MyMethod", methods[0].name)
        assertTrue(methods[0].isMethod)
        assertEquals(typeDef, methods[0].containingClass)
        
        val properties = typeDef.properties
        assertEquals(1, properties.size)
        assertEquals("MyProp", properties[0].name)
        assertEquals(typeDef, properties[0].containingClass)
    }

    fun testInheritanceResolution() {
        myFixture.configureByText("base.pas", """
            unit base;
            interface
            type
              TBase = class
                procedure BaseMethod;
              end;
            implementation
            procedure TBase.BaseMethod; begin end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("descendant.pas", """
            unit descendant;
            interface
            uses base;
            type
              TDescendant = class(TBase)
                procedure ChildMethod;
              end;
            implementation
            procedure TDescendant.ChildMethod;
            var
              d: TDescendant;
            begin
              d.BaseMethod;
              d.ChildMethod;
            end;
            end.
        """.trimIndent())

        val dOffset = file.text.indexOf("d: TDescendant")
        val dVar = com.intellij.psi.util.PsiTreeUtil.findElementOfClassAtOffset(file, dOffset, nl.akiar.pascal.psi.PascalVariableDefinition::class.java, false)
        assertNotNull("Variable 'd' not found", dVar)
        assertEquals("TDescendant", dVar!!.typeName)

        val offset = file.text.indexOf("BaseMethod")
        val element = file.findElementAt(offset)
        assertNotNull("Element at BaseMethod not found", element)
        
        val handler = nl.akiar.pascal.navigation.PascalGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(element, offset, myFixture.editor)
        
        assertNotNull("Should find targets for BaseMethod", targets)
        assertTrue("Should find at least one target", targets!!.isNotEmpty())
        
        val resolved = targets[0]
        assertNotNull("Should resolve BaseMethod", resolved)
        assertTrue(resolved is PascalRoutine)
        assertEquals("TBase", (resolved as PascalRoutine).containingClass?.name)
    }

    fun testJumpBetweenSections() {
        val file = myFixture.configureByText("nav.pas", """
            unit nav;
            interface
            procedure MyProc;
            implementation
            procedure MyProc;
            begin
            end;
            end.
        """.trimIndent())

        val handler = nl.akiar.pascal.navigation.PascalGotoDeclarationHandler()

        // From implementation to interface
        val implOffset = file.text.lastIndexOf("MyProc")
        val implElement = file.findElementAt(implOffset)
        val toDeclTargets = handler.getGotoDeclarationTargets(implElement, implOffset, myFixture.editor)
        assertNotNull("Should find target from implementation", toDeclTargets)
        assertEquals(1, toDeclTargets!!.size)
        val decl = toDeclTargets[0] as nl.akiar.pascal.psi.PascalRoutine
        assertFalse("Should jump to declaration (not implementation)", decl.isImplementation)

        // From interface to implementation
        val declOffset = file.text.indexOf("MyProc")
        val declElement = file.findElementAt(declOffset)
        val toImplTargets = handler.getGotoDeclarationTargets(declElement, declOffset, myFixture.editor)
        assertNotNull("Should find target from declaration", toImplTargets)
        assertEquals(1, toImplTargets!!.size)
        val impl = toImplTargets[0] as nl.akiar.pascal.psi.PascalRoutine
        assertTrue("Should jump to implementation", impl.isImplementation)
    }

    fun testVisibility() {
        myFixture.configureByText("visibility.pas", """
            unit visibility;
            interface
            type
              TMyClass = class
              private
                procedure PrivateMethod;
              protected
                procedure ProtectedMethod;
              public
                procedure PublicMethod;
              end;
            implementation
            procedure TMyClass.PrivateMethod; begin end;
            procedure TMyClass.ProtectedMethod; begin end;
            procedure TMyClass.PublicMethod; begin end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("caller.pas", """
            unit caller;
            interface
            uses visibility;
            procedure CallIt;
            implementation
            procedure CallIt;
            var
              obj: TMyClass;
            begin
              obj.PublicMethod;
              obj.PrivateMethod;
            end;
            end.
        """.trimIndent())

        val handler = nl.akiar.pascal.navigation.PascalGotoDeclarationHandler()

        // Public should resolve
        val publicOffset = file.text.indexOf("PublicMethod")
        val publicElement = file.findElementAt(publicOffset)
        val publicTargets = handler.getGotoDeclarationTargets(publicElement, publicOffset, myFixture.editor)
        assertNotNull("PublicMethod should be resolvable", publicTargets)
        assertTrue(publicTargets!!.isNotEmpty())

        // Private should NOT resolve from different unit
        val privateOffset = file.text.indexOf("PrivateMethod")
        val privateElement = file.findElementAt(privateOffset)
        val privateTargets = handler.getGotoDeclarationTargets(privateElement, privateOffset, myFixture.editor)
        assertTrue("Private method should not be accessible from another unit", privateTargets == null || privateTargets.isEmpty())
    }
}
