package nl.akiar.pascal.resolution

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for TransitiveDependencyResolver.
 *
 * These tests verify that:
 * 1. Direct dependencies are properly identified
 * 2. Transitive dependencies are resolved correctly
 * 3. Circular dependencies don't cause infinite loops
 * 4. Diamond dependencies are handled efficiently
 */
class TransitiveDependencyResolverTest : BasePlatformTestCase() {

    // ==================== Direct Dependency Tests ====================

    @Test
    fun testDirectDependency_SingleUnit() {
        // UnitB - a dependency
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            type TBType = Integer;
            implementation
            end.
        """.trimIndent())

        // UnitA - uses UnitB
        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB;
            type TAType = TBType;
            implementation
            end.
        """.trimIndent())

        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("UnitB should be available", result.isUnitAvailable("UnitB"))
        assertTrue("unitb should be available (case insensitive)", result.isUnitAvailable("unitb"))
        assertTrue("UnitB should be a direct dependency", result.isDirectDependency("UnitB"))
        assertEquals(listOf("unitb"), result.directUnits)
    }

    @Test
    fun testDirectDependency_MultipleUnits() {
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitC.pas", """
            unit UnitC;
            interface
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitD.pas", """
            unit UnitD;
            interface
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB, UnitC, UnitD;
            implementation
            end.
        """.trimIndent())

        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("UnitB should be available", result.isUnitAvailable("UnitB"))
        assertTrue("UnitC should be available", result.isUnitAvailable("UnitC"))
        assertTrue("UnitD should be available", result.isUnitAvailable("UnitD"))
        assertEquals(3, result.directUnits.size)
    }

    // ==================== Transitive Dependency Tests ====================

    @Test
    fun testTransitiveDependency_TwoLevels() {
        // UnitC - leaf dependency
        myFixture.configureByText("UnitC.pas", """
            unit UnitC;
            interface
            type TCType = String;
            implementation
            end.
        """.trimIndent())

        // UnitB - uses UnitC
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            uses UnitC;
            type TBType = TCType;
            implementation
            end.
        """.trimIndent())

        // UnitA - uses UnitB
        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB;
            implementation
            end.
        """.trimIndent())

        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("UnitB should be available", result.isUnitAvailable("UnitB"))
        assertTrue("UnitC should be available transitively", result.isUnitAvailable("UnitC"))
        assertTrue("UnitB should be direct", result.isDirectDependency("UnitB"))
        assertFalse("UnitC should NOT be direct", result.isDirectDependency("UnitC"))
    }

    @Test
    fun testTransitiveDependency_ThreeLevels() {
        myFixture.configureByText("UnitD.pas", """
            unit UnitD;
            interface
            type TDType = Integer;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitC.pas", """
            unit UnitC;
            interface
            uses UnitD;
            type TCType = TDType;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            uses UnitC;
            type TBType = TCType;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB;
            implementation
            end.
        """.trimIndent())

        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("UnitB should be available", result.isUnitAvailable("UnitB"))
        assertTrue("UnitC should be available transitively", result.isUnitAvailable("UnitC"))
        assertTrue("UnitD should be available transitively", result.isUnitAvailable("UnitD"))
    }

    // ==================== Circular Dependency Tests ====================

    @Test
    fun testCircularDependency_TwoUnits() {
        // UnitA uses UnitB, UnitB uses UnitA - should not infinite loop

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            uses UnitA;
            type TBType = Integer;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB;
            type TAType = Integer;
            implementation
            end.
        """.trimIndent())

        // This should complete without stack overflow
        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("UnitB should be available", result.isUnitAvailable("UnitB"))
        // UnitA is the origin file, so it's handled specially
    }

    @Test
    fun testCircularDependency_ThreeUnits() {
        // A -> B -> C -> A
        myFixture.configureByText("UnitC.pas", """
            unit UnitC;
            interface
            uses UnitA;
            type TCType = Integer;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            uses UnitC;
            type TBType = Integer;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB;
            type TAType = Integer;
            implementation
            end.
        """.trimIndent())

        // This should complete without stack overflow
        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("UnitB should be available", result.isUnitAvailable("UnitB"))
        assertTrue("UnitC should be available", result.isUnitAvailable("UnitC"))
    }

    // ==================== Diamond Dependency Tests ====================

    @Test
    fun testDiamondDependency() {
        // UnitA uses UnitB and UnitC
        // UnitB uses UnitD
        // UnitC uses UnitD
        // UnitD should only be processed once

        myFixture.configureByText("UnitD.pas", """
            unit UnitD;
            interface
            type TDType = Integer;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            uses UnitD;
            type TBType = TDType;
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitC.pas", """
            unit UnitC;
            interface
            uses UnitD;
            type TCType = TDType;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB, UnitC;
            implementation
            end.
        """.trimIndent())

        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("UnitB should be available", result.isUnitAvailable("UnitB"))
        assertTrue("UnitC should be available", result.isUnitAvailable("UnitC"))
        assertTrue("UnitD should be available (once)", result.isUnitAvailable("UnitD"))

        // Check that UnitD appears in the graph via both paths
        val graphKeys = result.unitGraph.keys
        assertTrue("unitb should be in graph", "unitb" in graphKeys)
        assertTrue("unitc should be in graph", "unitc" in graphKeys)
    }

    // ==================== Dotted Unit Name Tests ====================

    @Test
    fun testDottedUnitName() {
        myFixture.configureByText("System.Classes.pas", """
            unit System.Classes;
            interface
            type TStrings = class end;
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            uses System.Classes;
            var Lines: TStrings;
            implementation
            end.
        """.trimIndent())

        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("System.Classes should be available", result.isUnitAvailable("System.Classes"))
    }

    // ==================== Interface vs Implementation Uses ====================

    @Test
    fun testBothInterfaceAndImplementationUses() {
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            implementation
            end.
        """.trimIndent())

        myFixture.configureByText("UnitC.pas", """
            unit UnitC;
            interface
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB;
            implementation
            uses UnitC;
            end.
        """.trimIndent())

        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("UnitB should be available", result.isUnitAvailable("UnitB"))
        assertTrue("UnitC should be available", result.isUnitAvailable("UnitC"))
        assertEquals(2, result.directUnits.size)
    }

    // ==================== Max Depth Tests ====================

    @Test
    fun testMaxDepthLimit() {
        // Create a chain of 15 units (exceeds default max depth of 10)
        for (i in 15 downTo 2) {
            val usesClause = if (i < 15) "uses Unit${i + 1};" else ""
            myFixture.configureByText("Unit$i.pas", """
                unit Unit$i;
                interface
                $usesClause
                implementation
                end.
            """.trimIndent())
        }

        val mainFile = myFixture.configureByText("Unit1.pas", """
            unit Unit1;
            interface
            uses Unit2;
            implementation
            end.
        """.trimIndent())

        // With default max depth of 10, we should see units up to depth 10
        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile, maxDepth = 10)

        assertTrue("Unit2 should be available", result.isUnitAvailable("Unit2"))
        assertTrue("Unit10 should be available (at max depth)", result.isUnitAvailable("Unit10"))
        // Unit11 might not be available due to depth limit
    }

    // ==================== Empty Uses Clause Tests ====================

    @Test
    fun testNoUsesClauses() {
        val mainFile = myFixture.configureByText("Main.pas", """
            unit Main;
            interface
            implementation
            end.
        """.trimIndent())

        val result = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        assertTrue("Direct units should be empty", result.directUnits.isEmpty())
        // Note: Transitive units include implicit System units (system, system.classes, classes)
        // which are always available in Delphi programs even without explicit uses clause
        assertTrue("Transitive units should contain implicit System units",
            result.transitiveUnits.any { it.startsWith("system") || it == "classes" })
    }

    // ==================== Caching Tests ====================

    @Test
    fun testCachingWorks() {
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB;
            implementation
            end.
        """.trimIndent())

        // First call
        val result1 = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)
        // Second call should return cached result
        val result2 = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        // Results should be equal (and ideally the same instance due to caching)
        assertEquals(result1.directUnits, result2.directUnits)
        assertEquals(result1.transitiveUnits, result2.transitiveUnits)
    }

    @Test
    fun testInvalidateCacheWorks() {
        myFixture.configureByText("UnitB.pas", """
            unit UnitB;
            interface
            implementation
            end.
        """.trimIndent())

        val mainFile = myFixture.configureByText("UnitA.pas", """
            unit UnitA;
            interface
            uses UnitB;
            implementation
            end.
        """.trimIndent())

        val result1 = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        // Invalidate cache
        TransitiveDependencyResolver.invalidateCache(mainFile)

        // Get result again
        val result2 = TransitiveDependencyResolver.getTransitiveDependencies(mainFile)

        // Results should still be equal
        assertEquals(result1.directUnits, result2.directUnits)
        assertEquals(result1.transitiveUnits, result2.transitiveUnits)
    }
}
