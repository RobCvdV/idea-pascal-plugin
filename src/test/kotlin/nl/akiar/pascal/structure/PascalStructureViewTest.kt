package nl.akiar.pascal.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import org.junit.Test

class PascalStructureViewTest : BasePlatformTestCase() {

    private fun TreeElement.asStructure() = this as StructureViewTreeElement
    private fun Iterable<TreeElement>.names() = map { it.presentation.presentableText }
    @JvmName("namesArray")
    private fun Array<out TreeElement>.names() = map { it.presentation.presentableText }

    @Test
    fun testFileWithClassShowsMembers() {
        val file = myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TMyClass = class
              private
                FName: String;
              public
                constructor Create;
                destructor Destroy; override;
                procedure DoWork;
                function GetName: String;
                property Name: String read FName;
              end;
            implementation
            constructor TMyClass.Create; begin end;
            destructor TMyClass.Destroy; begin end;
            procedure TMyClass.DoWork; begin end;
            function TMyClass.GetName: String; begin Result := FName; end;
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val root = model.root

        assertNotNull(root.presentation.presentableText)

        val fileChildren = root.children
        val classElement = fileChildren.firstOrNull {
            it.asStructure().value is PascalTypeDefinition &&
                (it.asStructure().value as PascalTypeDefinition).name == "TMyClass"
        }
        assertNotNull("Should find TMyClass", classElement)

        val memberNames = classElement!!.children.names()
        println("Class members: $memberNames")

        assertTrue("Should contain field FName", "FName" in memberNames)
        assertTrue("Should contain constructor Create", "Create" in memberNames)
        assertTrue("Should contain destructor Destroy", "Destroy" in memberNames)
        assertTrue("Should contain method DoWork", "DoWork" in memberNames)
        assertTrue("Should contain function GetName", "GetName" in memberNames)
        assertTrue("Should contain property Name", "Name" in memberNames)
    }

    @Test
    fun testFileWithEnum() {
        val file = myFixture.configureByText("Enums.pas", """
            unit Enums;
            interface
            type
              TColor = (clRed, clGreen, clBlue);
            implementation
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val root = model.root

        val enumElement = root.children.firstOrNull {
            it.asStructure().value is PascalTypeDefinition &&
                (it.asStructure().value as PascalTypeDefinition).name == "TColor"
        }
        assertNotNull("Should find TColor enum", enumElement)

        val names = enumElement!!.children.names()
        println("Enum elements: $names")

        assertTrue("Should contain clRed", "clRed" in names)
        assertTrue("Should contain clGreen", "clGreen" in names)
        assertTrue("Should contain clBlue", "clBlue" in names)
    }

    @Test
    fun testFileWithTopLevelRoutines() {
        val file = myFixture.configureByText("Utils.pas", """
            unit Utils;
            interface
            procedure DoSomething;
            function Calculate(A, B: Integer): Integer;
            implementation
            procedure DoSomething; begin end;
            function Calculate(A, B: Integer): Integer; begin Result := A + B; end;
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val routineNames = root(model).children
            .filter { it.asStructure().value is PascalRoutine }
            .names()
        println("Top-level routines: $routineNames")

        assertTrue("Should contain DoSomething", "DoSomething" in routineNames)
        assertTrue("Should contain Calculate", "Calculate" in routineNames)
    }

    @Test
    fun testFileWithConstants() {
        val file = myFixture.configureByText("Consts.pas", """
            unit Consts;
            interface
            const
              MAX_VALUE: Integer = 100;
            var
              GlobalCounter: Integer;
            implementation
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val varNames = root(model).children
            .filter { it.asStructure().value is PascalVariableDefinition }
            .names()
        println("Variables/constants: $varNames")

        assertTrue("Should contain MAX_VALUE", "MAX_VALUE" in varNames)
        assertTrue("Should contain GlobalCounter", "GlobalCounter" in varNames)
    }

    @Test
    fun testEmptyUnit() {
        val file = myFixture.configureByText("Empty.pas", """
            unit Empty;
            interface
            implementation
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val children = root(model).children
        assertNotNull(children)
    }

    @Test
    fun testPresentationText_RoutineWithSignature() {
        val file = myFixture.configureByText("Sigs.pas", """
            unit Sigs;
            interface
            function Add(const A: Integer; var B: String): Boolean;
            implementation
            function Add(const A: Integer; var B: String): Boolean; begin Result := True; end;
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val routineElement = root(model).children.firstOrNull {
            it.presentation.presentableText == "Add"
        }
        assertNotNull("Should find Add", routineElement)

        val location = routineElement!!.presentation.locationString
        println("Add location string: $location")
        assertNotNull("Should have location string with signature", location)
        assertTrue("Should contain return type", location!!.contains("Boolean"))
    }

    @Test
    fun testPresentationText_FieldShowsType() {
        val file = myFixture.configureByText("Fields.pas", """
            unit Fields;
            interface
            type
              TRec = class
              public
                Value: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val typeElement = root(model).children.firstOrNull {
            it.asStructure().value is PascalTypeDefinition &&
                (it.asStructure().value as PascalTypeDefinition).name == "TRec"
        }
        assertNotNull(typeElement)

        val fieldElement = typeElement!!.children.firstOrNull {
            it.presentation.presentableText == "Value"
        }
        assertNotNull("Should find field Value", fieldElement)

        val location = fieldElement!!.presentation.locationString
        println("Value location string: $location")
        assertEquals("Should show type", ": Integer", location)
    }

    @Test
    fun testNavigability() {
        val file = myFixture.configureByText("Nav.pas", """
            unit Nav;
            interface
            type
              TFoo = class
                procedure Bar;
              end;
            implementation
            procedure TFoo.Bar; begin end;
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val typeElement = root(model).children.firstOrNull {
            it.asStructure().value is PascalTypeDefinition &&
                (it.asStructure().value as PascalTypeDefinition).name == "TFoo"
        }
        assertNotNull(typeElement)
        assertTrue("Type should be navigable", typeElement!!.asStructure().canNavigate())

        val methodElement = typeElement.children.firstOrNull {
            it.presentation.presentableText == "Bar"
        }
        assertNotNull(methodElement)
        assertTrue("Method should be navigable", methodElement!!.asStructure().canNavigate())
    }

    @Test
    fun testForwardDeclarationSkipped() {
        val file = myFixture.configureByText("Forward.pas", """
            unit Forward;
            interface
            type
              TForward = class;
              TForward = class
              public
                Value: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val typeElements = root(model).children.filter {
            it.asStructure().value is PascalTypeDefinition &&
                (it.asStructure().value as PascalTypeDefinition).name == "TForward"
        }
        assertEquals("Should show only one TForward (not forward decl)", 1, typeElements.size)
        assertTrue("Should have children (not forward)", typeElements[0].children.isNotEmpty())
    }

    @Test
    fun testRecordType() {
        val file = myFixture.configureByText("Records.pas", """
            unit Records;
            interface
            type
              TPoint = record
                X: Double;
                Y: Double;
              end;
            implementation
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val recordElement = root(model).children.firstOrNull {
            it.asStructure().value is PascalTypeDefinition &&
                (it.asStructure().value as PascalTypeDefinition).name == "TPoint"
        }
        assertNotNull("Should find TPoint record", recordElement)

        val fieldNames = recordElement!!.children.names()
        println("Record fields: $fieldNames")
        assertTrue("Should contain X", "X" in fieldNames)
        assertTrue("Should contain Y", "Y" in fieldNames)
    }

    @Test
    fun testTypeShowsSuperclass() {
        val file = myFixture.configureByText("Inherit.pas", """
            unit Inherit;
            interface
            type
              TBase = class
              end;
              TChild = class(TBase)
              end;
            implementation
            end.
        """.trimIndent())

        val model = PascalStructureViewModel(file, null)
        val childElement = root(model).children.firstOrNull {
            it.asStructure().value is PascalTypeDefinition &&
                (it.asStructure().value as PascalTypeDefinition).name == "TChild"
        }
        assertNotNull(childElement)

        val location = childElement!!.presentation.locationString
        println("TChild location: $location")
        assertEquals("Should show superclass", "(TBase)", location)
    }

    private fun root(model: PascalStructureViewModel) = model.root
}
