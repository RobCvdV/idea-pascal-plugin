package nl.akiar.pascal.resolution

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import nl.akiar.pascal.project.PascalProjectService
import nl.akiar.pascal.settings.PascalSourcePathsSettings
import nl.akiar.pascal.uses.PascalUsesClauseInfo

/**
 * Resolves transitive dependencies for a Pascal file.
 *
 * Given a PsiFile, this resolver recursively resolves all units in its uses clauses
 * and builds a complete set of transitively-available units. This is essential for
 * proper member chain resolution where types may be defined in indirectly-used units.
 *
 * For example, if FileA uses UnitB, and UnitB uses UnitC, then types from UnitC
 * should be available when resolving member chains in FileA.
 *
 * The resolver:
 * - Tracks visited units to prevent infinite loops
 * - Caches results for performance
 * - Preserves uses clause order for "last wins" semantics
 */
object TransitiveDependencyResolver {
    private val LOG = Logger.getInstance(TransitiveDependencyResolver::class.java)

    private val CACHE_KEY = Key.create<CachedValue<TransitiveDependencyResult>>(
        "PASCAL_TRANSITIVE_DEPS"
    )

    /**
     * Result of transitive dependency resolution.
     *
     * @param directUnits Units directly in the uses clause of the origin file
     * @param transitiveUnits All units transitively available (including direct)
     * @param unitGraph Map of unit -> its direct dependencies (for debugging)
     */
    data class TransitiveDependencyResult(
        val directUnits: List<String>,
        val transitiveUnits: Set<String>,
        val unitGraph: Map<String, List<String>>
    ) {
        /**
         * Check if a unit is available (either directly or transitively).
         */
        fun isUnitAvailable(unitName: String): Boolean {
            return transitiveUnits.contains(unitName.lowercase())
        }

        /**
         * Check if a unit is directly used (in the file's own uses clause).
         */
        fun isDirectDependency(unitName: String): Boolean {
            return directUnits.any { it.equals(unitName, ignoreCase = true) }
        }
    }

    /**
     * Get all transitively available units for a file.
     *
     * @param file The PsiFile to analyze
     * @param maxDepth Maximum recursion depth (default 10, to prevent excessive crawling)
     * @return TransitiveDependencyResult containing all available units
     */
    @JvmStatic
    @JvmOverloads
    fun getTransitiveDependencies(file: PsiFile, maxDepth: Int = 10): TransitiveDependencyResult {
        return CachedValuesManager.getManager(file.project).getCachedValue(file, CACHE_KEY, {
            val result = computeTransitiveDependencies(file, maxDepth)
            // Invalidate when PSI tree changes or project structure changes
            CachedValueProvider.Result.create(
                result,
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }, false)
    }

    /**
     * Get the set of all transitively available unit names (lowercased).
     */
    @JvmStatic
    fun getAvailableUnits(file: PsiFile): Set<String> {
        return getTransitiveDependencies(file).transitiveUnits
    }

    /**
     * Clear the cache for a specific file.
     */
    @JvmStatic
    fun invalidateCache(file: PsiFile) {
        file.putUserData(CACHE_KEY, null)
    }

    private fun computeTransitiveDependencies(file: PsiFile, maxDepth: Int): TransitiveDependencyResult {
        val project = file.project
        val usesInfo = nl.akiar.pascal.uses.PascalUsesClauseInfo.parse(file)
        val directUnitsRaw = usesInfo.allUses // both interface and implementation
        val scopes = nl.akiar.pascal.settings.PascalSourcePathsSettings.getInstance(project).unitScopeNames.map { it.lowercase() }

        // Seed with direct units and scope-expanded variants (e.g., Classes -> System.Classes)
        val directUnits = mutableListOf<String>()
        val seed = mutableSetOf<String>()
        for (u in directUnitsRaw) {
            val lu = u.lowercase()
            directUnits.add(lu)
            seed.add(lu)
            if (!lu.contains('.')) {
                for (scope in scopes) {
                    seed.add("$scope.$lu")
                }
            }
        }
        // Always ensure implicit base scopes are present
        seed.add("system")
        seed.add("system.classes")
        seed.add("classes")

        val visited = mutableSetOf<String>()
        val unitGraph = mutableMapOf<String, List<String>>()

        val fileUnitName = extractUnitName(file)?.lowercase()

        for (unitName in seed) {
            resolveTransitive(unitName, visited, unitGraph, project, 0, maxDepth, fileUnitName)
        }

        val result = TransitiveDependencyResult(
            directUnits = directUnits,
            transitiveUnits = visited.toSet(),
            unitGraph = unitGraph.toMap()
        )
        // Gate the diagnostic log with the unit log filter to avoid spam
        if (nl.akiar.pascal.log.UnitLogFilter.shouldLog(file)) {
            com.intellij.openapi.diagnostic.Logger.getInstance(TransitiveDependencyResolver::class.java)
                .info("[MemberTraversal][Diag] Transitive available units for '${file.name}' count=${result.transitiveUnits.size} sample=${result.transitiveUnits.take(10)}")
        }
        return result
    }

    private fun resolveTransitive(
        unitName: String,
        visited: MutableSet<String>,
        unitGraph: MutableMap<String, List<String>>,
        project: Project,
        currentDepth: Int,
        maxDepth: Int,
        originUnitName: String? = null
    ) {
        val lowerUnit = unitName.lowercase()
        if (originUnitName != null && lowerUnit == originUnitName) return
        if (!visited.add(lowerUnit)) return
        if (currentDepth >= maxDepth) return

        val projectService = PascalProjectService.getInstance(project)
        val virtualFile = projectService.resolveUnit(unitName, true)
        if (virtualFile == null) {
            return
        }
        val psiFile = getPsiFile(virtualFile, project) ?: return
        val usesInfo = PascalUsesClauseInfo.parse(psiFile)
        val deps = usesInfo.allUses
        unitGraph[lowerUnit] = deps

        // Expand dependencies: only prefix scopes for unscoped names
        val scopes = PascalSourcePathsSettings.getInstance(project).unitScopeNames.map { it.lowercase() }
        for (dep in deps) {
            val ldep = dep.lowercase()
            resolveTransitive(ldep, visited, unitGraph, project, currentDepth + 1, maxDepth, originUnitName)
            if (!ldep.contains('.')) {
                for (scope in scopes) {
                    resolveTransitive("$scope.$ldep", visited, unitGraph, project, currentDepth + 1, maxDepth, originUnitName)
                }
            }
        }
    }

    private fun getPsiFile(virtualFile: VirtualFile, project: Project): PsiFile? {
        if (!virtualFile.isValid) return null
        return com.intellij.openapi.application.ReadAction.compute<PsiFile?, Throwable> {
            PsiManager.getInstance(project).findFile(virtualFile)
        }
    }

    private fun extractUnitName(file: PsiFile): String? {
        val fileName = file.name
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
    }
}
