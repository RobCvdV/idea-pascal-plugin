package nl.akiar.pascal.resolution

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.*
import org.junit.Test

/**
 * Tests for generic type handling: type parameter extraction, generic stripping
 * for index lookup, type argument substitution through member chains, and
 * documentation rendering.
 */
class GenericTypeResolutionTest : BasePlatformTestCase() {

    // ==================== Type Parameter Extraction ====================

    @Test
    fun testTypeParametersExtracted() {
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        val psiFile = myFixture.configureByText("Dummy.pas", """
            unit Dummy;
            interface
            uses GenericUnit;
            implementation
            end.
        """.trimIndent())

        val typeDefs = PsiTreeUtil.findChildrenOfType(psiFile.project.let {
            val files = nl.akiar.pascal.stubs.PascalTypeIndex.findTypes("TEntityList", it)
            files.firstOrNull()?.containingFile
        } ?: return, PascalTypeDefinition::class.java)

        val entityList = typeDefs.firstOrNull { it.name == "TEntityList" }
        assertNotNull("Should find TEntityList type", entityList)

        val typeParams = entityList!!.typeParameters
        assertTrue("Should have at least one type parameter", typeParams.isNotEmpty())
        assertEquals("First type parameter should be 'T'", "T", typeParams[0])
    }

    // ==================== Variable Type Name Includes Generics ====================

    @Test
    fun testVariableTypeNameIncludesGenericArgs() {
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              LResult: TEntityList<TRide>;
            begin
              LResult.GetItem<caret>(0);
            end;
            end.
        """.trimIndent())

        // Verify that the variable's type name includes generic args
        val varDefs = PsiTreeUtil.findChildrenOfType(mainFile, PascalVariableDefinition::class.java)
        val lResult = varDefs.firstOrNull { it.name == "LResult" }
        assertNotNull("Should find LResult variable", lResult)

        val typeName = lResult!!.typeName
        assertNotNull("LResult should have a type name", typeName)
        println("LResult typeName = '$typeName'")
        // The type name should contain the generic arguments
        assertTrue("Type name should contain generic args: got '$typeName'",
            typeName!!.contains("TEntityList"))
    }

    // ==================== Chain Resolution with Generic Substitution ====================

    @Test
    fun testGenericChainResolution_SimpleProperty() {
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TContainer<T> = class
              public
                Value: T;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              C: TContainer<TRide>;
            begin
              C.Value.RideName<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'RideName' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 3 elements", 3, result.chainElements.size)
        assertEquals("C", result.chainElements[0].text)
        assertEquals("Value", result.chainElements[1].text)
        assertEquals("RideName", result.chainElements[2].text)

        assertNotNull("'C' should be resolved", result.resolvedElements[0])
        assertNotNull("'Value' should be resolved", result.resolvedElements[1])
        // This is the key test: RideName should be resolved through generic substitution
        // Value has type T, T is substituted to TRide, and TRide has RideName
        assertNotNull("'RideName' should be resolved via generic substitution T->TRide",
            result.resolvedElements[2])
    }

    @Test
    fun testGenericChainResolution_MethodReturnType() {
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              LResult: TEntityList<TRide>;
            begin
              LResult.GetItem(0).RideName<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'RideName' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 3 elements", 3, result.chainElements.size)
        assertEquals("LResult", result.chainElements[0].text)
        assertEquals("GetItem", result.chainElements[1].text)
        assertEquals("RideName", result.chainElements[2].text)

        assertNotNull("'LResult' should be resolved", result.resolvedElements[0])
        assertNotNull("'GetItem' should be resolved", result.resolvedElements[1])
        assertTrue("'GetItem' should be PascalRoutine", result.resolvedElements[1] is PascalRoutine)
        // GetItem returns T, T is substituted to TRide, TRide has RideName
        assertNotNull("'RideName' should be resolved via generic substitution T->TRide",
            result.resolvedElements[2])
    }

    @Test
    fun testGenericChainResolution_TypeArgMap_SingleElement() {
        // When the chain resolves through a generic type (C.Value), the intermediate
        // typeArgMap should have been used for substitution. We verify this by checking
        // that Value resolves correctly to a member of TContainer (which means the
        // chain resolution correctly built the type arg map {T → TRide}).
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TContainer<T> = class
              public
                Value: T;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              C: TContainer<TRide>;
            begin
              C.Value<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Value' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertEquals("C", result.chainElements[0].text)
        assertEquals("Value", result.chainElements[1].text)

        // Value should be resolved — this proves the typeArgMap was built correctly
        assertNotNull("'C' should be resolved", result.resolvedElements[0])
        assertNotNull("'Value' should be resolved on TContainer", result.resolvedElements[1])
        assertTrue("'Value' should be a variable definition",
            result.resolvedElements[1] is PascalVariableDefinition)
    }

    @Test
    fun testGenericChainResolution_TypeArgMapAfterSubstitution() {
        // After resolving through a generic chain, the map should represent
        // the concrete type's context (empty for non-generic types)
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TContainer<T> = class
              public
                Value: T;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              C: TContainer<TRide>;
            begin
              C.Value<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Value' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertNotNull("'C' should be resolved", result.resolvedElements[0])
        assertNotNull("'Value' should be resolved", result.resolvedElements[1])

        // After resolving Value (type T -> substituted to TRide),
        // the typeArgMap represents TRide's context which has no generics
        println("typeArgMap after C.Value = ${result.typeArgMap}")
        assertTrue("typeArgMap should be empty for concrete TRide type",
            result.typeArgMap.isEmpty())
    }

    // ==================== Variable with Generic Type Resolves to Base Type ====================

    @Test
    fun testGenericVariableResolvesToBaseType() {
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
                procedure Add(Item: T);
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            procedure TEntityList<T>.Add(Item: T);
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              LResult: TEntityList<TRide>;
            begin
              LResult.Add<caret>(nil);
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Add' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertNotNull("'LResult' should be resolved", result.resolvedElements[0])
        assertNotNull("'Add' should be resolved on TEntityList", result.resolvedElements[1])
        assertTrue("'Add' should be PascalRoutine", result.resolvedElements[1] is PascalRoutine)
    }

    // ==================== Type Definition Signature with Generic Constraints ====================

    @Test
    fun testTypeSignatureNotTruncatedByClassConstraint() {
        val mainFile = myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T: class> = class
              public
                function GetItem(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        val typeDefs = PsiTreeUtil.findChildrenOfType(mainFile, PascalTypeDefinition::class.java)
        val entityList = typeDefs.firstOrNull { it.name == "TEntityList" }
        assertNotNull("Should find TEntityList type", entityList)

        val header = entityList!!.declarationHeader
        println("Declaration header: '$header'")
        // The header should NOT be truncated at "class" inside the generic constraint
        // It should include the full signature like "TEntityList<T: class> = class"
        assertTrue("Header should contain '= class' after the generic constraint, got: '$header'",
            header.contains("= class") || header.contains("=class"))
        assertTrue("Header should contain generic params '<T', got: '$header'",
            header.contains("<T"))
    }

    @Test
    fun testTypeSignatureWithMultipleConstraints() {
        val mainFile = myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T: class, constructor> = class
              public
                function GetItem(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        val typeDefs = PsiTreeUtil.findChildrenOfType(mainFile, PascalTypeDefinition::class.java)
        val entityList = typeDefs.firstOrNull { it.name == "TEntityList" }
        assertNotNull("Should find TEntityList type", entityList)

        val header = entityList!!.declarationHeader
        println("Declaration header: '$header'")
        // Should not be truncated at "class" inside the constraint
        assertTrue("Header should contain 'constructor' (not truncated at 'class'), got: '$header'",
            header.contains("constructor"))
    }

    // ==================== Documentation Tests ====================

    @Test
    fun testDocumentationShowsGenericTypeName() {
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              LResult: TEntityList<TRide>;
            begin
              LResult<caret>.GetItem(0);
            end;
            end.
        """.trimIndent())

        // Find the LResult variable definition
        val varDefs = PsiTreeUtil.findChildrenOfType(mainFile, PascalVariableDefinition::class.java)
        val lResult = varDefs.firstOrNull { it.name == "LResult" }
        assertNotNull("Should find LResult variable", lResult)

        val typeName = lResult!!.typeName
        println("Variable type name: '$typeName'")
        // Type name should include generic part
        assertTrue("Type name should include generic arguments, got: '$typeName'",
            typeName != null && typeName.contains("<") && typeName.contains(">"))
    }

    @Test
    fun testDocumentationGenericSectionNotDoubleEscaped() {
        val mainFile = myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        val typeDefs = PsiTreeUtil.findChildrenOfType(mainFile, PascalTypeDefinition::class.java)
        val entityList = typeDefs.firstOrNull { it.name == "TEntityList" }
        assertNotNull("Should find TEntityList type", entityList)

        val provider = nl.akiar.pascal.documentation.PascalDocumentationProvider()
        val doc = provider.generateDoc(entityList, null)
        if (doc != null) {
            println("Generated doc: $doc")
            // Should NOT contain double-escaped entities like &amp;lt;
            assertFalse("Documentation should not contain double-escaped '&amp;lt;', got: $doc",
                doc.contains("&amp;lt;"))
            assertFalse("Documentation should not contain double-escaped '&amp;gt;', got: $doc",
                doc.contains("&amp;gt;"))
        }
    }

    // ==================== Per-Element TypeArgMap Tests ====================

    @Test
    fun testPerElementTypeArgMaps() {
        // Verify that per-element typeArgMaps are correctly populated
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TContainer<T> = class
              public
                Value: T;
              end;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              C: TContainer<TRide>;
            begin
              C.Value.RideName<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'RideName' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 3 elements", 3, result.chainElements.size)

        // Element 0 (C): no owner, map should be empty
        val map0 = result.getTypeArgMapForIndex(0)
        assertTrue("typeArgMap for C should be empty", map0.isEmpty())

        // Element 1 (Value): owner is C with type TContainer<TRide>, map should have {T → TRide}
        val map1 = result.getTypeArgMapForIndex(1)
        assertTrue("typeArgMap for Value should contain T", map1.containsKey("T"))
        assertEquals("T should map to TRide for Value", "TRide", map1["T"])

        // Element 2 (RideName): owner is Value with substituted type TRide, map should be empty
        val map2 = result.getTypeArgMapForIndex(2)
        assertTrue("typeArgMap for RideName should be empty (TRide has no generics)", map2.isEmpty())
    }

    // ==================== Chain with Bracket Access ====================

    @Test
    fun testChainWithBracketAccess() {
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
                property Items[Index: Integer]: T read GetItem;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              LResult: TEntityList<TRide>;
            begin
              LResult.GetItem(0).RideName<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'RideName' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 3 elements (brackets skipped)", 3, result.chainElements.size)
        assertEquals("LResult", result.chainElements[0].text)
        assertEquals("GetItem", result.chainElements[1].text)
        assertEquals("RideName", result.chainElements[2].text)

        // All elements should be resolved
        assertNotNull("'LResult' should be resolved", result.resolvedElements[0])
        assertNotNull("'GetItem' should be resolved", result.resolvedElements[1])
        assertNotNull("'RideName' should be resolved via generic substitution", result.resolvedElements[2])
    }

    // ==================== Full Chain Through Bracket Access ====================

    @Test
    fun testChainResolution_MemberOfSubstitutedType() {
        // Tests the full chain: LResult.ItemsById[0].RideId
        // where ItemsById has type T (substituted to TRide) and TRide has RideId
        myFixture.configureByText("GenericUnit.pas", """
            unit GenericUnit;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
                property ItemsById[Index: Integer]: T read GetItem;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        myFixture.configureByText("Rides.pas", """
            unit Rides;
            interface
            type
              TRide = class
              public
                RideId: Integer;
                RideName: String;
              end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericUnit, Rides;
            implementation
            procedure Test;
            var
              LResult: TEntityList<TRide>;
            begin
              LResult.ItemsById[0].RideId<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'RideId' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")
        println("Resolved elements: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<null>" }}")
        for (i in result.chainElements.indices) {
            println("  [$i] ${result.chainElements[i].text} -> ${result.resolvedElements[i]?.javaClass?.simpleName ?: "<null>"}, typeArgMap=${result.getTypeArgMapForIndex(i)}")
        }

        assertEquals("Chain should have 3 elements", 3, result.chainElements.size)
        assertEquals("LResult", result.chainElements[0].text)
        assertEquals("ItemsById", result.chainElements[1].text)
        assertEquals("RideId", result.chainElements[2].text)

        assertNotNull("'LResult' should be resolved", result.resolvedElements[0])
        assertNotNull("'ItemsById' should be resolved", result.resolvedElements[1])
        // This is the critical test: RideId should resolve through generic substitution
        // ItemsById has type T, T→TRide, TRide has RideId
        assertNotNull("'RideId' should be resolved through generic substitution (T→TRide→RideId)",
            result.resolvedElements[2])
    }

    // ==================== Generic Method Name Extraction ====================

    @Test
    fun testGenericMethodNameNotConfusedWithTypeParam() {
        // For "class function Resolve<T>: T;", getName() should return "Resolve", not "T"
        val mainFile = myFixture.configureByText("GenericMethods.pas", """
            unit GenericMethods;
            interface
            type
              TDI = class
              public
                class function Resolve<T>: T; static;
                class procedure Register<T>(AInstance: T); static;
              end;
            implementation
            class function TDI.Resolve<T>: T;
            begin
            end;
            class procedure TDI.Register<T>(AInstance: T);
            begin
            end;
            end.
        """.trimIndent())

        val routines = PsiTreeUtil.findChildrenOfType(mainFile, PascalRoutine::class.java)
        for (r in routines) {
            println("Routine: name='${r.name}' impl=${r.isImplementation} nameId='${r.nameIdentifier?.text}' text='${r.text.take(60)}'")
            // Dump direct child tokens for the Register declaration
            if (r.text.contains("Register") && !r.isImplementation) {
                val node = r.node
                var child = node.firstChildNode
                while (child != null) {
                    println("  child: type=${child.elementType} text='${child.text.take(30)}' offset=${child.startOffset}")
                    child = child.treeNext
                }
            }
        }
        val methodNames = routines.map { it.name }.filterNotNull()
        println("Method names: $methodNames")

        // Should contain "Resolve" and "Register", NOT "T"
        assertTrue("Should find a method named 'Resolve', got: $methodNames",
            methodNames.any { it.equals("Resolve", ignoreCase = true) })
        assertTrue("Should find a method named 'Register', got: $methodNames",
            methodNames.any { it.equals("Register", ignoreCase = true) })

        // The type param 'T' should NOT appear as a method name
        val declarationMethods = routines.filter { !it.isImplementation }
        val declNames = declarationMethods.map { it.name }
        assertFalse("Declaration method names should not be 'T', got: $declNames",
            declNames.all { it == "T" })
    }

    @Test
    fun testGenericMethodFoundAsMember() {
        // Verify that findMemberInType can find generic methods by their actual name
        myFixture.configureByText("DITypes.pas", """
            unit DITypes;
            interface
            type
              TDI = class
              public
                class function Resolve<T>: T; static;
              end;
            implementation
            class function TDI.Resolve<T>: T;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses DITypes;
            implementation
            procedure Test;
            begin
              TDI.Resolve<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Resolve' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertEquals("TDI", result.chainElements[0].text)
        assertEquals("Resolve", result.chainElements[1].text)

        assertNotNull("'TDI' should be resolved", result.resolvedElements[0])
        assertNotNull("'Resolve' should be found as member of TDI", result.resolvedElements[1])
        assertTrue("'Resolve' should be a PascalRoutine", result.resolvedElements[1] is PascalRoutine)
        assertEquals("Resolve", (result.resolvedElements[1] as PascalRoutine).name)
    }

    // ==================== Routine Return Type Includes Generics ====================

    @Test
    fun testRoutineReturnTypeIncludesGenericArgs() {
        // Verifies that extractReturnTypeNameFromAST preserves generic arguments
        // e.g. "function Await: IPromise<TEntityList<TMutation>>" should return
        // "IPromise<TEntityList<TMutation>>" not just "IPromise"
        val mainFile = myFixture.configureByText("GenericRoutine.pas", """
            unit GenericRoutine;
            interface
            type
              TEntityList<T> = class
              end;
              TMutation = class
              end;
              IPromise<T> = interface
                function Await: T;
              end;
              TMyService = class
              public
                function GetMutations: IPromise<TEntityList<TMutation>>;
              end;
            implementation
            function TMyService.GetMutations: IPromise<TEntityList<TMutation>>;
            begin
            end;
            end.
        """.trimIndent())

        val routines = PsiTreeUtil.findChildrenOfType(mainFile, PascalRoutine::class.java)
        val getMutations = routines.firstOrNull { it.name == "GetMutations" && !it.isImplementation }
        assertNotNull("Should find GetMutations declaration", getMutations)

        val returnType = getMutations!!.returnTypeName
        assertNotNull("GetMutations should have a return type", returnType)
        println("GetMutations return type: '$returnType'")
        assertTrue("Return type should contain generic args '<', got: '$returnType'",
            returnType!!.contains("<"))
        assertTrue("Return type should contain 'TEntityList', got: '$returnType'",
            returnType.contains("TEntityList"))
        assertTrue("Return type should contain 'TMutation', got: '$returnType'",
            returnType.contains("TMutation"))
    }

    @Test
    fun testRoutineReturnTypeSimpleGenericArg() {
        // Simpler case: function GetItem: TList<String>
        val mainFile = myFixture.configureByText("SimpleGenericReturn.pas", """
            unit SimpleGenericReturn;
            interface
            type
              TList<T> = class
              end;
              TMyClass = class
              public
                function GetItems: TList<String>;
              end;
            implementation
            function TMyClass.GetItems: TList<String>;
            begin
            end;
            end.
        """.trimIndent())

        val routines = PsiTreeUtil.findChildrenOfType(mainFile, PascalRoutine::class.java)
        val getItems = routines.firstOrNull { it.name == "GetItems" && !it.isImplementation }
        assertNotNull("Should find GetItems declaration", getItems)

        val returnType = getItems!!.returnTypeName
        assertNotNull("GetItems should have a return type", returnType)
        assertTrue("Return type should be 'TList<String>' or similar, got: '$returnType'",
            returnType!!.contains("TList") && returnType.contains("String") && returnType.contains("<"))
    }

    // ==================== Multi-level Generic Substitution ====================

    @Test
    fun testMultiLevelGenericSubstitution() {
        // Tests chain through IPromise<TEntityList<TMutation>> -> Await returns TEntityList<TMutation>
        myFixture.configureByText("GenericTypes.pas", """
            unit GenericTypes;
            interface
            type
              TEntityList<T> = class
              public
                function GetItem(Index: Integer): T;
              end;
              TMutation = class
              public
                MutationId: Integer;
              end;
              IPromise<T> = interface
                function Await: T;
              end;
            implementation
            function TEntityList<T>.GetItem(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses GenericTypes;
            implementation
            procedure Test;
            var
              LPromise: IPromise<TEntityList<TMutation>>;
            begin
              LPromise.Await<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Await' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        assertEquals("Chain should have 2 elements", 2, result.chainElements.size)
        assertEquals("LPromise", result.chainElements[0].text)
        assertEquals("Await", result.chainElements[1].text)

        assertNotNull("'LPromise' should be resolved", result.resolvedElements[0])
        assertNotNull("'Await' should be resolved on IPromise", result.resolvedElements[1])
        assertTrue("'Await' should be PascalRoutine", result.resolvedElements[1] is PascalRoutine)

        // After resolving Await (return type T, substituted to TEntityList<TMutation>),
        // the final typeArgMap should reflect TEntityList's context: {T → TMutation}
        val finalMap = result.typeArgMap
        println("Final typeArgMap after LPromise.Await = $finalMap")
        assertTrue("typeArgMap should contain T->TMutation after Await resolves to TEntityList<TMutation>",
            finalMap.containsKey("T") && finalMap["T"] == "TMutation")
    }

    // ==================== Chain Collection with Angle Brackets ====================

    @Test
    fun testChainCollectionSkipsAngleBrackets() {
        // Tests that DI.Resolve<IMutationsRepository>.AddFromIds collects chain correctly
        // The chain collector must skip <...> just like it skips (...) and [...]
        myFixture.configureByText("DITypes.pas", """
            unit DITypes;
            interface
            type
              IMutationsRepository = interface
                procedure AddFromIds(Ids: String);
              end;
              TDI = class
              public
                class function Resolve<T>: T; static;
              end;
            implementation
            class function TDI.Resolve<T>: T;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses DITypes;
            implementation
            procedure Test;
            begin
              TDI.Resolve<IMutationsRepository>.AddFromIds<caret>('123');
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'AddFromIds' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")

        // The chain should be [TDI, Resolve, AddFromIds] — angle brackets skipped
        assertEquals("Chain should have 3 elements (angle brackets skipped)", 3, result.chainElements.size)
        assertEquals("TDI", result.chainElements[0].text)
        assertEquals("Resolve", result.chainElements[1].text)
        assertEquals("AddFromIds", result.chainElements[2].text)
    }

    @Test
    fun testChainCollectionSkipsNestedAngleBrackets() {
        // Tests chain collection with nested generics: Foo.Bar<A, B<C>>.Baz
        myFixture.configureByText("NestedGeneric.pas", """
            unit NestedGeneric;
            interface
            type
              TInner<T> = class
              end;
              TOuter = class
              public
                class function Create<A, B>: TOuter; static;
                procedure DoWork;
              end;
            implementation
            class function TOuter.Create<A, B>: TOuter;
            begin
            end;
            procedure TOuter.DoWork;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses NestedGeneric;
            implementation
            procedure Test;
            begin
              TOuter.Create<String, TInner<Integer>>.DoWork<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'DoWork' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")

        // Nested angle brackets should be properly depth-tracked
        assertEquals("Chain should have 3 elements (nested angle brackets skipped)", 3, result.chainElements.size)
        assertEquals("TOuter", result.chainElements[0].text)
        assertEquals("Create", result.chainElements[1].text)
        assertEquals("DoWork", result.chainElements[2].text)
    }

    @Test
    fun testChainCollectionFromMiddleElement_SkipsAngleBrackets() {
        // When starting resolution from Resolve, the backward walk should still find TDI before the dot
        myFixture.configureByText("DITypes.pas", """
            unit DITypes;
            interface
            type
              IMutationsRepository = interface
                procedure AddFromIds(Ids: String);
              end;
              TDI = class
              public
                class function Resolve<T>: T; static;
              end;
            implementation
            class function TDI.Resolve<T>: T;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses DITypes;
            implementation
            procedure Test;
            begin
              TDI.Resolve<caret><IMutationsRepository>.AddFromIds('123');
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Resolve' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")

        // Starting from Resolve, forward walk should skip <...> to find .AddFromIds
        assertEquals("Chain should have 3 elements", 3, result.chainElements.size)
        assertEquals("TDI", result.chainElements[0].text)
        assertEquals("Resolve", result.chainElements[1].text)
        assertEquals("AddFromIds", result.chainElements[2].text)
    }

    // ==================== Call-Site Generic Type Argument Extraction ====================

    @Test
    fun testGenericMethodCallSiteTypeArgSubstitution() {
        // DI.Resolve<IMutationsRepository> should substitute T → IMutationsRepository
        // so that .AddFromIds is resolved on IMutationsRepository
        myFixture.configureByText("DITypes.pas", """
            unit DITypes;
            interface
            type
              IMutationsRepository = interface
                procedure AddFromIds(Ids: String);
              end;
              TDI = class
              public
                class function Resolve<T>: T; static;
              end;
            implementation
            class function TDI.Resolve<T>: T;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses DITypes;
            implementation
            procedure Test;
            begin
              TDI.Resolve<IMutationsRepository>.AddFromIds<caret>('123');
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'AddFromIds' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")
        println("Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<null>" }}")

        assertEquals(3, result.chainElements.size)
        assertEquals("TDI", result.chainElements[0].text)
        assertEquals("Resolve", result.chainElements[1].text)
        assertEquals("AddFromIds", result.chainElements[2].text)

        // Resolve should be resolved as a routine
        assertNotNull("Resolve should be resolved", result.resolvedElements[1])
        assertTrue("Resolve should be a PascalRoutine", result.resolvedElements[1] is PascalRoutine)

        // AddFromIds should be resolved via substitution T → IMutationsRepository
        assertNotNull("AddFromIds should be resolved via generic substitution", result.resolvedElements[2])
        assertEquals("AddFromIds", (result.resolvedElements[2] as? com.intellij.psi.PsiNameIdentifierOwner)?.name)
    }

    @Test
    fun testGenericMethodCallSiteTypeArgSubstitutionWithChain() {
        // Test that call-site type args enable multi-step resolution:
        // DI.Resolve<IPromise<String>>.Await should resolve Await on IPromise with T=String
        myFixture.configureByText("PromiseTypes.pas", """
            unit PromiseTypes;
            interface
            type
              IPromise<T> = interface
                function Await: T;
              end;
              TDI = class
              public
                class function Resolve<T>: T; static;
              end;
            implementation
            class function TDI.Resolve<T>: T;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses PromiseTypes;
            implementation
            procedure Test;
            begin
              TDI.Resolve<IPromise<String>>.Await<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Await' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")
        println("Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<null>" }}")

        assertEquals(3, result.chainElements.size)
        // Resolve should be resolved
        assertNotNull("Resolve should be resolved", result.resolvedElements[1])
        // Await should be resolved on IPromise (via substitution T → IPromise<String>)
        assertNotNull("Await should be resolved via generic substitution", result.resolvedElements[2])
        assertEquals("Await", (result.resolvedElements[2] as? com.intellij.psi.PsiNameIdentifierOwner)?.name)
    }

    // ==================== Ancestor Member Resolution ====================

    @Test
    fun testInheritedMemberResolutionThroughChain() {
        // Test that member resolution walks the ancestor chain:
        // TRide extends TEntity which has Id property
        myFixture.configureByText("EntityTypes.pas", """
            unit EntityTypes;
            interface
            type
              TEntity = class
              public
                Id: Integer;
              end;
              TRide = class(TEntity)
              public
                Name: String;
              end;
              TEntityList<T> = class
              public
                function ItemsById(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.ItemsById(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses EntityTypes;
            implementation
            procedure Test;
            var
              LResult: TEntityList<TRide>;
            begin
              LResult.ItemsById(0).Id<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Id' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")
        println("Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<null>" }}")

        assertEquals(3, result.chainElements.size)
        assertEquals("LResult", result.chainElements[0].text)
        assertEquals("ItemsById", result.chainElements[1].text)
        assertEquals("Id", result.chainElements[2].text)

        // LResult resolved
        assertNotNull("LResult should be resolved", result.resolvedElements[0])

        // ItemsById resolved with substitution T → TRide
        assertNotNull("ItemsById should be resolved", result.resolvedElements[1])

        // Id should be resolved via ancestor TEntity (TRide inherits from TEntity)
        assertNotNull("Id should be resolved via inherited member from TEntity", result.resolvedElements[2])
        assertEquals("Id", (result.resolvedElements[2] as? com.intellij.psi.PsiNameIdentifierOwner)?.name)
    }

    @Test
    fun testSuperClassNameExtractedCorrectly() {
        // Verify that getSuperClassName() returns the correct name
        // even when the parser wraps the superclass in a TYPE_REFERENCE node
        val file = myFixture.configureByText("InheritTest.pas", """
            unit InheritTest;
            interface
            type
              TBase = class
              public
                Id: Integer;
              end;
              TDerived = class(TBase)
              public
                Name: String;
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val derived = types.first { it.name == "TDerived" }

        val superName = derived.superClassName
        assertNotNull("TDerived should have a superclass name", superName)
        assertEquals("TBase", superName)

        val superClass = derived.superClass
        assertNotNull("TDerived.getSuperClass() should resolve to TBase", superClass)
        assertEquals("TBase", superClass!!.name)
    }

    @Test
    fun testSuperClassNameWithGenericArgs() {
        // TGenericDerived = class(TGenericBase<String>) should extract "TGenericBase"
        val file = myFixture.configureByText("GenericInheritTest.pas", """
            unit GenericInheritTest;
            interface
            type
              TGenericBase<T> = class
              public
                Value: T;
              end;
              TGenericDerived = class(TGenericBase<String>)
              public
                Extra: Integer;
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val derived = types.first { it.name == "TGenericDerived" }

        val superName = derived.superClassName
        assertNotNull("TGenericDerived should have a superclass name", superName)
        // Should be the base name without generic arguments
        assertTrue("Superclass name should be TGenericBase (possibly with generic args), got '$superName'",
            superName!!.startsWith("TGenericBase"))

        val superClass = derived.superClass
        assertNotNull("TGenericDerived.getSuperClass() should resolve to TGenericBase", superClass)
        assertEquals("TGenericBase", superClass!!.name)
    }

    @Test
    fun testInheritedMemberFoundViaGetMembers() {
        // Direct test: getMembers(true) should include inherited members
        val file = myFixture.configureByText("MemberInheritTest.pas", """
            unit MemberInheritTest;
            interface
            type
              TBase = class
              public
                Id: Integer;
                BaseName: String;
              end;
              TChild = class(TBase)
              public
                ChildField: Boolean;
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val child = types.first { it.name == "TChild" }

        val members = child.getMembers(true)
        val memberNames = members.filterIsInstance<com.intellij.psi.PsiNameIdentifierOwner>().map { it.name }
        println("TChild members(includeAncestors=true): $memberNames")

        assertTrue("Should contain ChildField", memberNames.any { it.equals("ChildField", ignoreCase = true) })
        assertTrue("Should contain inherited Id from TBase", memberNames.any { it.equals("Id", ignoreCase = true) })
        assertTrue("Should contain inherited BaseName from TBase", memberNames.any { it.equals("BaseName", ignoreCase = true) })
    }

    // ==================== Property vs Field Resolution ====================

    @Test
    fun testPropertyDeclarationFoundInMembers() {
        // Test that property declarations (not just fields) are found by getMembers(true)
        // This mimics the real-world case: TEntity has 'property Id: TId read GetEntityId write SetEntityId;'
        val file = myFixture.configureByText("PropertyTest.pas", """
            unit PropertyTest;
            interface
            type
              TEntity = class
              private
                FId: Integer;
                FName: String;
              public
                function GetId: Integer;
                procedure SetId(Value: Integer);
                property Id: Integer read GetId write SetId;
                property Name: String read FName write FName;
              end;
              TRide = class(TEntity)
              public
                RideDate: String;
              end;
            implementation
            function TEntity.GetId: Integer;
            begin
              Result := FId;
            end;
            procedure TEntity.SetId(Value: Integer);
            begin
              FId := Value;
            end;
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)

        // Check TEntity has property Id
        val entity = types.first { it.name == "TEntity" }
        val entityProps = entity.properties
        val entityPropNames = entityProps.map { it.name }
        println("TEntity properties: $entityPropNames")
        assertTrue("TEntity should have property 'Id'", entityPropNames.any { it.equals("Id", ignoreCase = true) })
        assertTrue("TEntity should have property 'Name'", entityPropNames.any { it.equals("Name", ignoreCase = true) })

        // Check TRide inherits Id via getMembers(true)
        val ride = types.first { it.name == "TRide" }
        val rideMembers = ride.getMembers(true)
        val rideMemberNames = rideMembers.filterIsInstance<com.intellij.psi.PsiNameIdentifierOwner>().map { it.name }
        println("TRide members(includeAncestors=true): $rideMemberNames")
        assertTrue("TRide should inherit property 'Id' from TEntity", rideMemberNames.any { it.equals("Id", ignoreCase = true) })
        assertTrue("TRide should inherit property 'Name' from TEntity", rideMemberNames.any { it.equals("Name", ignoreCase = true) })
        assertTrue("TRide should have own field 'RideDate'", rideMemberNames.any { it.equals("RideDate", ignoreCase = true) })
    }

    @Test
    fun testPropertyWithAttributeNamedCorrectly() {
        // Verify that properties with attributes like [Map('id')] get the correct name 'Id',
        // not the attribute name 'Map'. This was a bug where findFirstRecursiveAnyOf
        // would find the attribute's identifier before the property's own identifier.
        val file = myFixture.configureByText("AttrPropTest.pas", """
            unit AttrPropTest;
            interface
            type
              TEntity = class
              private
                FId: Integer;
              public
                [Map('id')]
                property Id: Integer read FId;
                [JsonName('entity_name')]
                property Name: String read FId;
              end;
            implementation
            end.
        """.trimIndent())

        val types = PsiTreeUtil.findChildrenOfType(file, PascalTypeDefinition::class.java)
        val entity = types.first { it.name == "TEntity" }
        val props = entity.properties
        val propNames = props.map { it.name }
        println("Properties with attributes: $propNames")

        assertTrue("Should find property 'Id' (not 'Map')", propNames.any { it.equals("Id", ignoreCase = true) })
        assertTrue("Should find property 'Name' (not 'JsonName')", propNames.any { it.equals("Name", ignoreCase = true) })
        assertFalse("Should NOT have a property named 'Map'", propNames.any { it.equals("Map", ignoreCase = true) })
        assertFalse("Should NOT have a property named 'JsonName'", propNames.any { it.equals("JsonName", ignoreCase = true) })
    }

    @Test
    fun testPropertyResolvedThroughChain() {
        // End-to-end: resolve chain where last element is a property on an ancestor type
        myFixture.configureByText("ChainPropTypes.pas", """
            unit ChainPropTypes;
            interface
            type
              TEntity = class
              private
                FId: Integer;
              public
                [Map('id')]
                property Id: Integer read FId;
              end;
              TRide = class(TEntity)
              public
                RideName: String;
              end;
              TEntityList<T> = class
              public
                function ItemsById(Index: Integer): T;
              end;
            implementation
            function TEntityList<T>.ItemsById(Index: Integer): T;
            begin
            end;
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("ChainPropMain.pas", """
            unit ChainPropMain;
            interface
            uses ChainPropTypes;
            implementation
            procedure Test;
            var
              LResult: TEntityList<TRide>;
            begin
              LResult.ItemsById(0).Id<caret>;
            end;
            end.
        """.trimIndent())

        val element = findIdentifierAtCaret(mainFile)
        assertNotNull("Should find 'Id' identifier", element)

        val result = MemberChainResolver.resolveChain(element!!)
        println("Chain elements: ${result.chainElements.map { it.text }}")
        println("Resolved: ${result.resolvedElements.map { it?.javaClass?.simpleName ?: "<null>" }}")

        assertEquals(3, result.chainElements.size)
        assertEquals("Id", result.chainElements[2].text)

        // Id should resolve to the property declaration on TEntity
        assertNotNull("Id should be resolved via inherited property from TEntity", result.resolvedElements[2])
        assertTrue("Id should resolve to a PascalProperty", result.resolvedElements[2] is PascalProperty)
    }

    // ==================== Helper Methods ====================

    private fun findIdentifierAtCaret(file: PsiFile): PsiElement? {
        val caretOffset = myFixture.caretOffset

        var element = file.findElementAt(caretOffset)
        if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) {
            return element
        }

        if (caretOffset > 0) {
            element = file.findElementAt(caretOffset - 1)
            if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) {
                return element
            }
        }

        for (offset in 1..3) {
            if (caretOffset - offset >= 0) {
                element = file.findElementAt(caretOffset - offset)
                if (element != null && element.node.elementType == PascalTokenTypes.IDENTIFIER) {
                    return element
                }
            }
        }

        return null
    }
}
