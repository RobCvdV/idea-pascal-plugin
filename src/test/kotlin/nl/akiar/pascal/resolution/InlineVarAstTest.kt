package nl.akiar.pascal.resolution

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.VariableKind
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

    @Test
    fun testInlineVarMemberChainInference() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TExtFileResult = class
              public
                Text: string;
              end;
              TExtFiles = class
              public
                function Retrieve(AId: Integer): TExtFileResult;
              end;
            var ExtFiles: TExtFiles;
            implementation
            function TExtFiles.Retrieve(AId: Integer): TExtFileResult;
            begin
              Result := nil;
            end;
            procedure DoTest;
            begin
              var LGoodsSql := ExtFiles.Retrieve(42).Text;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lGoodsSql = vars.firstOrNull { it.name == "LGoodsSql" }
        assertNotNull("LGoodsSql should be found", lGoodsSql)
        assertNull("LGoodsSql should have no explicit type", lGoodsSql!!.typeName)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lGoodsSql, file)
        assertEquals("Should infer 'string' from member chain", "string", inferredTypeName)
    }

    @Test
    fun testInlineVarStringLiteral() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure DoTest;
            begin
              var LCsv := '';
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lCsv = vars.firstOrNull { it.name == "LCsv" }
        assertNotNull("LCsv should be found", lCsv)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lCsv!!, file)
        assertEquals("Should infer 'string' from string literal", "string", inferredTypeName)
    }

    @Test
    fun testInlineVarIntegerLiteral() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure DoTest;
            begin
              var LCount := 42;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lCount = vars.firstOrNull { it.name == "LCount" }
        assertNotNull("LCount should be found", lCount)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lCount!!, file)
        assertEquals("Should infer 'Integer' from integer literal", "Integer", inferredTypeName)
    }

    @Test
    fun testInlineVarBooleanLiteral() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure DoTest;
            begin
              var LFlag := True;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lFlag = vars.firstOrNull { it.name == "LFlag" }
        assertNotNull("LFlag should be found", lFlag)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lFlag!!, file)
        assertEquals("Should infer 'Boolean' from True literal", "Boolean", inferredTypeName)
    }

    @Test
    fun testInlineVarNilLiteral() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure DoTest;
            begin
              var LObj := nil;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lObj = vars.firstOrNull { it.name == "LObj" }
        assertNotNull("LObj should be found", lObj)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lObj!!, file)
        assertNull("Should not infer type from nil", inferredTypeName)
    }

    @Test
    fun testInlineVarVariableToVariable() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TMyClass = class
              public
                procedure DoSomething;
              end;
            var GlobalObj: TMyClass;
            implementation
            procedure TMyClass.DoSomething; begin end;
            procedure DoTest;
            begin
              var L := GlobalObj;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val l = vars.firstOrNull { it.name == "L" }
        assertNotNull("L should be found", l)

        val inferredType = MemberChainResolver.getInferredTypeOf(l!!, file)
        assertNotNull("Should infer type from GlobalObj", inferredType)
        assertEquals("Inferred type should be TMyClass", "TMyClass", inferredType!!.name)
    }

    @Test
    fun testInlineVarChainResolutionOnInferredVar() {
        myFixture.configureByText("Types.pas", """
            unit Types;
            interface
            type
              TStatus = class
              public
                function GetCode: Integer;
              end;
            function GetStatus: TStatus;
            implementation
            function GetStatus: TStatus;
            begin
              Result := nil;
            end;
            function TStatus.GetCode: Integer;
            begin
              Result := 0;
            end;
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
              var LCode := LStatus.GetCode;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)

        // LStatus should infer to TStatus
        val lStatus = vars.firstOrNull { it.name == "LStatus" }
        assertNotNull("LStatus should be found", lStatus)
        val statusType = MemberChainResolver.getInferredTypeOf(lStatus!!, file)
        assertNotNull("LStatus should infer to TStatus", statusType)
        assertEquals("TStatus", statusType!!.name)

        // LCode should infer to Integer (via LStatus.GetCode chain)
        val lCode = vars.firstOrNull { it.name == "LCode" }
        assertNotNull("LCode should be found", lCode)
        val codeName = MemberChainResolver.getInferredTypeName(lCode!!, file)
        assertEquals("Should infer 'Integer' from LStatus.GetCode return type", "Integer", codeName)
    }

    @Test
    fun testPropertyTypeNameResolved() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TMyClass = class
              public
                property Text: String read GetText write SetText;
              end;
            implementation
            end.
        """.trimIndent())

        val props = PsiTreeUtil.findChildrenOfType(file, PascalProperty::class.java)
        val textProp = props.firstOrNull { it.name == "Text" }
        assertNotNull("Text property should be found", textProp)
        assertEquals("Property type should be String", "String", textProp!!.typeName)
    }

    @Test
    fun testInlineVarGenericSubstitution() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              IFuture<T> = interface
                function Await: T;
              end;
              TJson = class
              end;
            function GetFuture: IFuture<TJson>;
            implementation
            function GetFuture: IFuture<TJson>;
            begin
              Result := nil;
            end;
            procedure DoTest;
            begin
              var L := GetFuture.Await;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val l = vars.firstOrNull { it.name == "L" }
        assertNotNull("L should be found", l)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(l!!, file)
        assertEquals("Should substitute generic T with TJson", "TJson", inferredTypeName)
    }

    @Test
    fun testInlineVarKindIsLocal() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure DoTest;
            begin
              var L := '';
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val l = vars.firstOrNull { it.name == "L" }
        assertNotNull("L should be found", l)
        assertEquals("Inline var should be LOCAL kind", VariableKind.LOCAL, l!!.variableKind)
    }

    @Test
    fun testForInGenericCollection() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TMyClass = class
              public
                Name: string;
              end;
              TList<T> = class
              public
                function GetEnumerator: TListEnumerator<T>;
              end;
              TListEnumerator<T> = class
              public
                property Current: T read GetCurrent;
              end;
            implementation
            procedure DoTest(AList: TList<TMyClass>);
            begin
              for var LItem in AList do
                LItem.Name;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lItem = vars.firstOrNull { it.name == "LItem" }
        assertNotNull("LItem should be found", lItem)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lItem!!, file)
        assertEquals("Should infer TMyClass from TList<TMyClass>", "TMyClass", inferredTypeName)
    }

    @Test
    fun testForInGenericInterface() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TId = class
              public
                Value: Integer;
              end;
              IList<T> = interface
                function GetEnumerator: IEnumerator<T>;
              end;
              IEnumerator<T> = interface
                property Current: T read GetCurrent;
              end;
            implementation
            procedure DoTest(AIds: IList<TId>);
            begin
              for var LId in AIds do
                LId.Value;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lId = vars.firstOrNull { it.name == "LId" }
        assertNotNull("LId should be found", lId)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lId!!, file)
        assertEquals("Should infer TId from IList<TId>", "TId", inferredTypeName)
    }

    @Test
    fun testForInStringIteration() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure DoTest(AStr: string);
            begin
              for var LChar in AStr do
                ;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lChar = vars.firstOrNull { it.name == "LChar" }
        assertNotNull("LChar should be found", lChar)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lChar!!, file)
        assertEquals("Should infer Char from string iteration", "Char", inferredTypeName)
    }

    @Test
    fun testForInGetEnumeratorCurrent() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TJsonEnumerator = class
              public
                property Current: TJson read GetCurrent;
              end;
              TJson = class
              public
                function GetEnumerator: TJsonEnumerator;
                Name: string;
              end;
            implementation
            function TJson.GetEnumerator: TJsonEnumerator;
            begin
              Result := nil;
            end;
            procedure DoTest(AJson: TJson);
            begin
              for var LItem in AJson do
                LItem.Name;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lItem = vars.firstOrNull { it.name == "LItem" }
        assertNotNull("LItem should be found", lItem)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lItem!!, file)
        assertEquals("Should infer TJson from GetEnumerator.Current", "TJson", inferredTypeName)
    }

    @Test
    fun testForInRecordGetEnumeratorCurrent() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TJsonEnumerator = class
              public
                function GetCurrent: TJson;
                property Current: TJson read GetCurrent;
              end;
              TJson = record
              public
                function GetEnumerator: TJsonEnumerator;
                function Count: Integer;
              end;
            implementation
            function TJsonEnumerator.GetCurrent: TJson;
            begin
            end;
            function TJson.GetEnumerator: TJsonEnumerator;
            begin
              Result := nil;
            end;
            function TJson.Count: Integer;
            begin
              Result := 0;
            end;
            procedure DoTest(AJson: TJson);
            begin
              for var LItem in AJson do
                LItem.Count;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lItem = vars.firstOrNull { it.name == "LItem" }
        assertNotNull("LItem should be found", lItem)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lItem!!, file)
        assertEquals("Should infer TJson from record GetEnumerator.Current", "TJson", inferredTypeName)
    }

    @Test
    fun testForInRecordCrossUnit() {
        myFixture.configureByText("JsonUnit.pas", """
            unit JsonUnit;
            interface
            type
              TJsonEnumerator = class
              public
                function GetCurrent: TJson;
                function MoveNext: Boolean;
                property Current: TJson read GetCurrent;
              end;
              TJson = record
              public
                function GetEnumerator: TJsonEnumerator;
                function Count: Integer;
                function Get(const key: string): string;
              end;
            implementation
            function TJsonEnumerator.GetCurrent: TJson;
            begin
            end;
            function TJsonEnumerator.MoveNext: Boolean;
            begin
              Result := False;
            end;
            function TJson.GetEnumerator: TJsonEnumerator;
            begin
              Result := nil;
            end;
            function TJson.Count: Integer;
            begin
              Result := 0;
            end;
            function TJson.Get(const key: string): string;
            begin
              Result := '';
            end;
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses JsonUnit;
            implementation
            procedure DoTest(AListJson: TJson);
            begin
              for var LItemJson in AListJson do
                LItemJson.Count;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lItemJson = vars.firstOrNull { it.name == "LItemJson" }
        assertNotNull("LItemJson should be found", lItemJson)

        // Should infer TJson from GetEnumerator.Current of the cross-unit record type
        val inferredTypeName = MemberChainResolver.getInferredTypeName(lItemJson!!, file)
        assertEquals("Should infer TJson from cross-unit record GetEnumerator.Current", "TJson", inferredTypeName)

        // Also verify that using LItemJson in a member chain resolves its type
        val inferredType = MemberChainResolver.getInferredTypeOf(lItemJson, file)
        assertNotNull("Should resolve TJson type definition cross-unit", inferredType)
        assertEquals("TJson", inferredType!!.name)
    }

    @Test
    fun testForInIterableIsInferredVar() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TJsonEnumerator = class
              public
                function GetCurrent: TJson;
                function MoveNext: Boolean;
                property Current: TJson read GetCurrent;
              end;
              TJson = record
              public
                function GetEnumerator: TJsonEnumerator;
                function Count: Integer;
              end;
            function GetListJson: TJson;
            implementation
            function TJsonEnumerator.GetCurrent: TJson;
            begin
            end;
            function TJsonEnumerator.MoveNext: Boolean;
            begin
              Result := False;
            end;
            function TJson.GetEnumerator: TJsonEnumerator;
            begin
              Result := nil;
            end;
            function TJson.Count: Integer;
            begin
              Result := 0;
            end;
            function GetListJson: TJson;
            begin
            end;
            procedure DoTest;
            begin
              var LListJson := GetListJson;
              for var LItemJson in LListJson do
                LItemJson.Count;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lItemJson = vars.firstOrNull { it.name == "LItemJson" }
        assertNotNull("LItemJson should be found", lItemJson)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(lItemJson!!, file)
        assertEquals("Should infer TJson when iterable is itself an inferred var", "TJson", inferredTypeName)
    }

    @Test
    fun testForInVarUsedInMemberChain() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TJsonEnumerator = class
              public
                function GetCurrent: TJson;
                function MoveNext: Boolean;
                property Current: TJson read GetCurrent;
              end;
              TJson = record
              public
                function GetEnumerator: TJsonEnumerator;
                function Count: Integer;
                function Get(const key: string): string;
              end;
            implementation
            function TJsonEnumerator.GetCurrent: TJson;
            begin
            end;
            function TJsonEnumerator.MoveNext: Boolean;
            begin
              Result := False;
            end;
            function TJson.GetEnumerator: TJsonEnumerator;
            begin
              Result := nil;
            end;
            function TJson.Count: Integer;
            begin
              Result := 0;
            end;
            function TJson.Get(const key: string): string;
            begin
              Result := '';
            end;
            procedure DoTest(AListJson: TJson);
            begin
              for var LItemJson in AListJson do
              begin
                var LName := LItemJson.Count;
              end;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)

        // LItemJson should be inferred as TJson
        val lItemJson = vars.firstOrNull { it.name == "LItemJson" }
        assertNotNull("LItemJson should be found", lItemJson)
        val itemType = MemberChainResolver.getInferredTypeName(lItemJson!!, file)
        assertEquals("LItemJson should be TJson", "TJson", itemType)

        // LName should be inferred as Integer (from LItemJson.Count which returns Integer)
        val lName = vars.firstOrNull { it.name == "LName" }
        assertNotNull("LName should be found", lName)
        val nameType = MemberChainResolver.getInferredTypeName(lName!!, file)
        assertEquals("LName should be Integer from LItemJson.Count chain", "Integer", nameType)
    }

    @Test
    fun testForInInlineVarKindIsLoopVar() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            implementation
            procedure DoTest(AStr: string);
            begin
              for var LChar in AStr do
                ;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lChar = vars.firstOrNull { it.name == "LChar" }
        assertNotNull("LChar should be found", lChar)
        assertEquals("For-in inline var should be LOOP_VAR kind", VariableKind.LOOP_VAR, lChar!!.variableKind)
    }

    @Test
    fun testProtectedAncestorFieldResolution() {
        myFixture.configureByText("BaseUnit.pas", """
            unit BaseUnit;
            interface
            type
              TBase = class
              protected
                FField: string;
              end;
            implementation
            end.
        """.trimIndent())

        val file = myFixture.configureByText("ChildUnit.pas", """
            unit ChildUnit;
            interface
            uses BaseUnit;
            type
              TChild = class(TBase)
              public
                procedure DoSomething;
              end;
            implementation
            procedure TChild.DoSomething;
            begin
              var L := FField;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val l = vars.firstOrNull { it.name == "L" }
        assertNotNull("L should be found", l)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(l!!, file)
        assertEquals("Should infer string from inherited protected field FField", "string", inferredTypeName)
    }

    @Test
    fun testProtectedMemberFromSubclass() {
        myFixture.configureByText("Base.pas", """
            unit Base;
            interface
            type
              TBase = class
              protected
                FData: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val file = myFixture.configureByText("Child.pas", """
            unit Child;
            interface
            uses Base;
            type
              TChild = class(TBase)
              public
                function GetData: Integer;
              end;
            implementation
            function TChild.GetData: Integer;
            begin
              var L := Self.FData;
            end;
            end.
        """.trimIndent())

        // Self.FData uses the member chain resolution path through findMemberInType,
        // which tests that protected members from an ancestor in another unit are visible
        // when the call site is inside a subclass.
        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val l = vars.firstOrNull { it.name == "L" }
        assertNotNull("L should be found", l)

        val inferredTypeName = MemberChainResolver.getInferredTypeName(l!!, file)
        assertEquals("Should infer Integer from protected field FData via Self", "Integer", inferredTypeName)
    }
}
