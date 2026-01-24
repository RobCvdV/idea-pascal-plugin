package nl.akiar.pascal.resolution

import com.intellij.psi.PsiFile
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.settings.PascalSourcePathsSettings
import nl.akiar.pascal.stubs.PascalRoutineIndex
import nl.akiar.pascal.stubs.PascalTypeIndex
import nl.akiar.pascal.stubs.PascalVariableIndex
import nl.akiar.pascal.uses.PascalUsesClauseInfo

/**
 * Unified symbol resolution that implements Delphi's scoping rules correctly.
 *
 * According to Delphi documentation:
 * "If two or more units declare the same identifier in their interface sections,
 * an unqualified reference to the identifier selects the declaration in the innermost scope,
 * that is, in the unit where the reference itself occurs, or, if that unit does not declare
 * the identifier, in the LAST unit in the uses clause that does declare the identifier."
 *
 * This means:
 * 1. Same-file declarations always win
 * 2. Among external units, the LAST one in the uses clause wins (not ambiguous!)
 * 3. Only truly unresolvable cases (not in uses at all) are errors
 */
object PascalSymbolResolver {

    /**
     * Resolve a type reference with proper "last wins" semantics.
     */
    @JvmStatic
    fun resolveType(name: String, fromFile: PsiFile, offset: Int): TypeResolutionResult {
        val allTypes = PascalTypeIndex.findTypes(name, fromFile.project)
        val usesInfo = PascalUsesClauseInfo.parse(fromFile)
        val scopes = PascalSourcePathsSettings.getInstance(fromFile.project).unitScopeNames

        val sameFileTypes = mutableListOf<PascalTypeDefinition>()
        val inScopeTypes = mutableListOf<TypeWithPriority>()
        val outOfScopeTypes = mutableListOf<PascalTypeDefinition>()

        for (typeDef in allTypes) {
            val targetFile = typeDef.containingFile ?: continue
            val targetUnit = typeDef.unitName

            when {
                targetFile == fromFile -> {
                    sameFileTypes.add(typeDef)
                }
                else -> {
                    val priority = usesInfo.getUnitPriority(targetUnit, offset, scopes)
                    if (priority >= -1) { // -1 is for scope units (like "System"), -2 is not found
                        inScopeTypes.add(TypeWithPriority(typeDef, priority))
                    } else {
                        outOfScopeTypes.add(typeDef)
                    }
                }
            }
        }

        return TypeResolutionResult(
            sameFileTypes = sameFileTypes,
            inScopeTypes = inScopeTypes.sortedByDescending { it.priority }.map { it.type },
            outOfScopeTypes = outOfScopeTypes,
            usesInfo = usesInfo,
            referenceOffset = offset,
            scopes = scopes
        )
    }

    /**
     * Resolve a routine reference with proper "last wins" semantics.
     */
    @JvmStatic
    fun resolveRoutine(name: String, fromFile: PsiFile, offset: Int): RoutineResolutionResult {
        val allRoutines = PascalRoutineIndex.findRoutines(name, fromFile.project)
        val usesInfo = PascalUsesClauseInfo.parse(fromFile)
        val scopes = PascalSourcePathsSettings.getInstance(fromFile.project).unitScopeNames

        val sameFileRoutines = mutableListOf<PascalRoutine>()
        val inScopeRoutines = mutableListOf<RoutineWithPriority>()
        val outOfScopeRoutines = mutableListOf<PascalRoutine>()

        for (routine in allRoutines) {
            val targetFile = routine.containingFile ?: continue
            val targetUnit = routine.unitName

            when {
                targetFile == fromFile -> {
                    sameFileRoutines.add(routine)
                }
                else -> {
                    val priority = usesInfo.getUnitPriority(targetUnit, offset, scopes)
                    if (priority >= -1) {
                        inScopeRoutines.add(RoutineWithPriority(routine, priority))
                    } else {
                        outOfScopeRoutines.add(routine)
                    }
                }
            }
        }

        return RoutineResolutionResult(
            sameFileRoutines = sameFileRoutines,
            inScopeRoutines = inScopeRoutines.sortedByDescending { it.priority }.map { it.routine },
            outOfScopeRoutines = outOfScopeRoutines,
            usesInfo = usesInfo,
            referenceOffset = offset
        )
    }

    /**
     * Resolve a variable reference with proper "last wins" semantics.
     */
    @JvmStatic
    fun resolveVariable(name: String, fromFile: PsiFile, offset: Int): VariableResolutionResult {
        val allVariables = PascalVariableIndex.findVariablesWithScope(name, fromFile)
        val usesInfo = PascalUsesClauseInfo.parse(fromFile)
        val scopes = PascalSourcePathsSettings.getInstance(fromFile.project).unitScopeNames

        val sameFileVariables = mutableListOf<PascalVariableDefinition>()
        val inScopeVariables = mutableListOf<VariableWithPriority>()
        val outOfScopeVariables = mutableListOf<PascalVariableDefinition>()

        for (variable in allVariables) {
            val targetFile = variable.containingFile ?: continue
            val targetUnit = variable.unitName

            when {
                targetFile == fromFile -> {
                    sameFileVariables.add(variable)
                }
                else -> {
                    val priority = usesInfo.getUnitPriority(targetUnit, offset, scopes)
                    if (priority >= -1) {
                        inScopeVariables.add(VariableWithPriority(variable, priority))
                    } else {
                        outOfScopeVariables.add(variable)
                    }
                }
            }
        }

        return VariableResolutionResult(
            sameFileVariables = sameFileVariables,
            inScopeVariables = inScopeVariables.sortedByDescending { it.priority }.map { it.variable },
            outOfScopeVariables = outOfScopeVariables,
            usesInfo = usesInfo,
            referenceOffset = offset
        )
    }

    private data class TypeWithPriority(val type: PascalTypeDefinition, val priority: Int)
    private data class RoutineWithPriority(val routine: PascalRoutine, val priority: Int)
    private data class VariableWithPriority(val variable: PascalVariableDefinition, val priority: Int)
}

/**
 * Result of type resolution with proper Delphi semantics.
 */
data class TypeResolutionResult(
    /** Types defined in the same file (highest priority) */
    val sameFileTypes: List<PascalTypeDefinition>,
    /** Types from units in uses clause, sorted by priority (last wins = first in list) */
    val inScopeTypes: List<PascalTypeDefinition>,
    /** Types that exist but their unit is not in uses clause */
    val outOfScopeTypes: List<PascalTypeDefinition>,
    val usesInfo: PascalUsesClauseInfo,
    val referenceOffset: Int,
    val scopes: List<String>
) {
    /**
     * Get the best matching type according to Delphi semantics.
     * Same-file types have highest priority, then in-scope types (with "last wins").
     */
    val resolvedType: PascalTypeDefinition?
        get() = sameFileTypes.firstOrNull() ?: inScopeTypes.firstOrNull()

    /**
     * Get all types that could be referenced (same-file + in-scope).
     * Does NOT include out-of-scope types.
     */
    val allValidTypes: List<PascalTypeDefinition>
        get() = sameFileTypes + inScopeTypes

    val isEmpty: Boolean
        get() = sameFileTypes.isEmpty() && inScopeTypes.isEmpty() && outOfScopeTypes.isEmpty()

    val hasValidResolution: Boolean
        get() = sameFileTypes.isNotEmpty() || inScopeTypes.isNotEmpty()

    /**
     * Get an error message if resolution failed.
     * Returns null if resolution succeeded (even with multiple matches - "last wins").
     */
    fun getErrorMessage(): String? {
        // If we have same-file types or in-scope types, resolution succeeded
        if (hasValidResolution) {
            return null
        }

        // No in-scope types found - check if any exist out-of-scope
        if (outOfScopeTypes.isEmpty()) {
            return null // Type doesn't exist anywhere - let other error handling deal with this
        }

        // Type exists but not in scope
        val outOfScopeUnits = outOfScopeTypes.map { it.unitName }.distinct()

        return when {
            outOfScopeUnits.size > 1 -> {
                "Type not in scope. Found in units: ${outOfScopeUnits.joinToString(", ")}. Add one to uses clause."
            }
            else -> {
                val unitName = outOfScopeUnits.first()
                if (usesInfo.isInInterfaceSection(referenceOffset)) {
                    val implUses = usesInfo.implementationUses.map { it.lowercase() }
                    if (unitName.lowercase() in implUses) {
                        "Unit '$unitName' is in implementation uses, but type is referenced in interface section. Add it to interface uses."
                    } else {
                        "Unit '$unitName' is not in uses clause. Add it to interface uses."
                    }
                } else {
                    "Unit '$unitName' is not in uses clause. Add it to uses clause."
                }
            }
        }
    }
}

/**
 * Result of routine resolution with proper Delphi semantics.
 */
data class RoutineResolutionResult(
    val sameFileRoutines: List<PascalRoutine>,
    val inScopeRoutines: List<PascalRoutine>,
    val outOfScopeRoutines: List<PascalRoutine>,
    val usesInfo: PascalUsesClauseInfo,
    val referenceOffset: Int
) {
    val resolvedRoutine: PascalRoutine?
        get() = sameFileRoutines.firstOrNull() ?: inScopeRoutines.firstOrNull()

    val allValidRoutines: List<PascalRoutine>
        get() = sameFileRoutines + inScopeRoutines

    val isEmpty: Boolean
        get() = sameFileRoutines.isEmpty() && inScopeRoutines.isEmpty() && outOfScopeRoutines.isEmpty()

    val hasValidResolution: Boolean
        get() = sameFileRoutines.isNotEmpty() || inScopeRoutines.isNotEmpty()

    fun getErrorMessage(): String? {
        if (hasValidResolution) {
            return null
        }

        if (outOfScopeRoutines.isEmpty()) {
            return null
        }

        val outOfScopeUnits = outOfScopeRoutines.map { it.unitName }.distinct()

        return when {
            outOfScopeUnits.size > 1 -> {
                "Routine not in scope. Found in units: ${outOfScopeUnits.joinToString(", ")}. Add one to uses clause."
            }
            else -> {
                val unitName = outOfScopeUnits.first()
                if (usesInfo.isInInterfaceSection(referenceOffset)) {
                    val implUses = usesInfo.implementationUses.map { it.lowercase() }
                    if (unitName.lowercase() in implUses) {
                        "Unit '$unitName' is in implementation uses, but routine is referenced in interface section. Add it to interface uses."
                    } else {
                        "Unit '$unitName' is not in uses clause. Add it to interface uses."
                    }
                } else {
                    "Unit '$unitName' is not in uses clause. Add it to uses clause."
                }
            }
        }
    }
}

/**
 * Result of variable resolution with proper Delphi semantics.
 */
data class VariableResolutionResult(
    val sameFileVariables: List<PascalVariableDefinition>,
    val inScopeVariables: List<PascalVariableDefinition>,
    val outOfScopeVariables: List<PascalVariableDefinition>,
    val usesInfo: PascalUsesClauseInfo,
    val referenceOffset: Int
) {
    val resolvedVariable: PascalVariableDefinition?
        get() = sameFileVariables.firstOrNull() ?: inScopeVariables.firstOrNull()

    val allValidVariables: List<PascalVariableDefinition>
        get() = sameFileVariables + inScopeVariables

    val isEmpty: Boolean
        get() = sameFileVariables.isEmpty() && inScopeVariables.isEmpty() && outOfScopeVariables.isEmpty()

    val hasValidResolution: Boolean
        get() = sameFileVariables.isNotEmpty() || inScopeVariables.isNotEmpty()

    fun getErrorMessage(): String? {
        if (hasValidResolution) {
            return null
        }

        if (outOfScopeVariables.isEmpty()) {
            return null
        }

        val outOfScopeUnits = outOfScopeVariables.map { it.unitName }.distinct()

        return when {
            outOfScopeUnits.size > 1 -> {
                "Variable not in scope. Found in units: ${outOfScopeUnits.joinToString(", ")}. Add one to uses clause."
            }
            else -> {
                val unitName = outOfScopeUnits.first()
                if (usesInfo.isInInterfaceSection(referenceOffset)) {
                    val implUses = usesInfo.implementationUses.map { it.lowercase() }
                    if (unitName.lowercase() in implUses) {
                        "Unit '$unitName' is in implementation uses, but variable is referenced in interface section. Add it to interface uses."
                    } else {
                        "Unit '$unitName' is not in uses clause. Add it to interface uses."
                    }
                } else {
                    "Unit '$unitName' is not in uses clause. Add it to uses clause."
                }
            }
        }
    }
}
