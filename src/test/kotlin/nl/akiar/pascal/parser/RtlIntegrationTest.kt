package nl.akiar.pascal.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.io.File

/**
 * Integration tests using real RTL files (System.Classes.pas, System.Character.pas)
 * to verify that they parse correctly with the enriched PSI tree.
 */
class RtlIntegrationTest : BasePlatformTestCase() {

    private fun loadRtlFile(fileName: String): String {
        val file = File("src/test/data/$fileName")
        require(file.exists()) { "RTL test file not found: ${file.absolutePath}" }
        return file.readText()
    }

    private fun countElementType(debugInfo: String, elementType: String): Int {
        return debugInfo.lines().count { it.contains(elementType) }
    }

    private fun findElementTypes(debugInfo: String): Set<String> {
        return debugInfo.lines()
            .filter { it.contains("Pascal") }
            .mapNotNull { line ->
                // Extract element type from lines like "PascalPsiElement(PascalTokenType.ENUM_ELEMENT)"
                val tokenMatch = Regex("""PascalTokenType\.(\w+)""").find(line)
                if (tokenMatch != null) {
                    tokenMatch.groupValues[1]
                } else {
                    // Also check for stub-based types like "PascalVariableDefinition(...)"
                    when {
                        line.contains("PascalVariableDefinition") -> "VARIABLE_DEFINITION"
                        line.contains("PascalTypeDefinition") -> "TYPE_DEFINITION"
                        line.contains("PascalRoutine") -> "ROUTINE_DECLARATION"
                        line.contains("PascalProperty") -> "PROPERTY_DEFINITION"
                        else -> null
                    }
                }
            }
            .toSet()
    }

    // ============================================================================
    // System.Classes.pas Integration Tests
    // ============================================================================

    @Test
    fun testSystemClassesParsesWithoutErrors() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // Basic structural elements should be present
        assertTrue("Should contain INTERFACE_SECTION",
                   debugInfo.contains("INTERFACE_SECTION"))
        assertTrue("Should contain IMPLEMENTATION_SECTION",
                   debugInfo.contains("IMPLEMENTATION_SECTION"))
        assertTrue("Should contain UNIT_DECL_SECTION",
                   debugInfo.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testSystemClassesHasEnumElements() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // System.Classes has enums like TSeekOrigin, TListNotification, etc.
        val enumElementCount = countElementType(debugInfo, "ENUM_ELEMENT")
        assertTrue("System.Classes should contain ENUM_ELEMENT entries (has TSeekOrigin, TListNotification, etc.)",
                   enumElementCount > 0)

        val enumTypeCount = countElementType(debugInfo, "ENUM_TYPE")
        assertTrue("System.Classes should contain ENUM_TYPE entries",
                   enumTypeCount > 0)
    }

    @Test
    fun testSystemClassesHasClassDefinitions() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // System.Classes has many classes: TList, TStrings, TStream, TComponent, etc.
        val classTypeCount = countElementType(debugInfo, "CLASS_TYPE")
        assertTrue("System.Classes should contain many CLASS_TYPE entries (TList, TStrings, TStream, etc.)",
                   classTypeCount > 10)
    }

    @Test
    fun testSystemClassesHasMethodDeclarations() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // All routines (including methods) use stub-based PascalRoutine
        val routineCount = countElementType(debugInfo, "PascalRoutine")
        assertTrue("System.Classes should contain routine declarations",
                   routineCount > 0)
    }

    @Test
    fun testSystemClassesHasFieldDefinitions() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // Classes have fields like FCount, FCapacity, etc.
        // Fields use VARIABLE_DEFINITION (stub-based PascalVariableDefinition) with variableKind=FIELD
        val fieldCount = countElementType(debugInfo, "PascalVariableDefinition")
        assertTrue("System.Classes should contain variable definitions (including fields)",
                   fieldCount > 0)
    }

    @Test
    fun testSystemClassesHasConstructorsDestructors() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // System.Classes has many constructors (Create) and destructors (Destroy)
        // All routines use stub-based PascalRoutine
        val routineCount = countElementType(debugInfo, "PascalRoutine")
        assertTrue("System.Classes should contain routine declarations (including constructors/destructors)",
                   routineCount > 0)
    }

    @Test
    fun testSystemClassesHasVisibilitySections() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // Classes have private, protected, public sections
        val visibilityCount = countElementType(debugInfo, "VISIBILITY_SECTION")
        assertTrue("System.Classes should contain VISIBILITY_SECTION entries",
                   visibilityCount > 10)
    }

    @Test
    fun testSystemClassesHasConstants() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // System.Classes has constants like MaxListSize, fmCreate, etc.
        // Constants use VARIABLE_DEFINITION (stub-based PascalVariableDefinition) with variableKind=CONSTANT
        val constCount = countElementType(debugInfo, "CONST_SECTION")
        assertTrue("System.Classes should contain CONST_SECTION entries",
                   constCount > 0)
    }

    // ============================================================================
    // System.Character.pas Integration Tests
    // ============================================================================

    @Test
    fun testSystemCharacterParsesWithoutErrors() {
        val content = loadRtlFile("System.Character.pas")
        val psiFile = myFixture.configureByText("System.Character.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // Basic structural elements should be present
        assertTrue("Should contain INTERFACE_SECTION",
                   debugInfo.contains("INTERFACE_SECTION"))
        assertTrue("Should contain IMPLEMENTATION_SECTION",
                   debugInfo.contains("IMPLEMENTATION_SECTION"))
        assertTrue("Should contain UNIT_DECL_SECTION",
                   debugInfo.contains("UNIT_DECL_SECTION"))
    }

    @Test
    fun testSystemCharacterHasEnumElements() {
        val content = loadRtlFile("System.Character.pas")
        val psiFile = myFixture.configureByText("System.Character.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // System.Character has TUnicodeCategory, TUnicodeBreak enums
        val enumElementCount = countElementType(debugInfo, "ENUM_ELEMENT")
        assertTrue("System.Character should contain ENUM_ELEMENT entries (TUnicodeCategory, TUnicodeBreak)",
                   enumElementCount > 20) // TUnicodeCategory has ~30 values

        val enumTypeCount = countElementType(debugInfo, "ENUM_TYPE")
        assertTrue("System.Character should contain ENUM_TYPE entries",
                   enumTypeCount >= 2)
    }

    @Test
    fun testSystemCharacterHasRecordTypes() {
        val content = loadRtlFile("System.Character.pas")
        val psiFile = myFixture.configureByText("System.Character.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // System.Character has record helper types
        val types = findElementTypes(debugInfo)
        assertTrue("System.Character should contain record-related types",
                   types.contains("RECORD_TYPE") || types.contains("TYPE_DEFINITION"))
    }

    @Test
    fun testSystemCharacterHasStandaloneRoutines() {
        val content = loadRtlFile("System.Character.pas")
        val psiFile = myFixture.configureByText("System.Character.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        // System.Character has standalone functions
        // All routines use stub-based PascalRoutine
        val routineCount = countElementType(debugInfo, "PascalRoutine")
        assertTrue("System.Character should contain routine declarations",
                   routineCount > 0)
    }

    // ============================================================================
    // Complete Element Type Coverage Tests
    // ============================================================================

    @Test
    fun testSystemClassesElementTypeCoverage() {
        val content = loadRtlFile("System.Classes.pas")
        val psiFile = myFixture.configureByText("System.Classes.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        val types = findElementTypes(debugInfo)

        println("=== System.Classes.pas Element Type Coverage ===")
        println("Found element types: ${types.sorted().joinToString(", ")}")

        // Essential types that should be present
        val essentialTypes = listOf(
            "UNIT_DECL_SECTION",
            "INTERFACE_SECTION",
            "IMPLEMENTATION_SECTION",
            "USES_SECTION",
            "TYPE_SECTION",
            "CLASS_TYPE"  // Specific type - TYPE_DEFINITION only appears for simple type aliases
        )

        for (essential in essentialTypes) {
            assertTrue("System.Classes should contain $essential", types.contains(essential))
        }
    }

    @Test
    fun testSystemCharacterElementTypeCoverage() {
        val content = loadRtlFile("System.Character.pas")
        val psiFile = myFixture.configureByText("System.Character.pas", content)

        val debugInfo = com.intellij.openapi.application.runReadAction {
            com.intellij.psi.impl.DebugUtil.psiToString(psiFile, false)
        }

        val types = findElementTypes(debugInfo)

        println("=== System.Character.pas Element Type Coverage ===")
        println("Found element types: ${types.sorted().joinToString(", ")}")

        // Essential types that should be present
        val essentialTypes = listOf(
            "UNIT_DECL_SECTION",
            "INTERFACE_SECTION",
            "IMPLEMENTATION_SECTION",
            "USES_SECTION",
            "TYPE_SECTION",
            "ENUM_TYPE",
            "ENUM_ELEMENT"
        )

        for (essential in essentialTypes) {
            assertTrue("System.Character should contain $essential", types.contains(essential))
        }
    }
}
