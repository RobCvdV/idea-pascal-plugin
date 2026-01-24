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
        val usesInfo = PascalUsesClauseInfo.parse(file)
        val directUnits = usesInfo.allUses

        LOG.debug("[TransitiveDeps] Computing for file: ${file.name}")
        LOG.debug("[TransitiveDeps] Direct uses: $directUnits")

        val visited = mutableSetOf<String>()
        val unitGraph = mutableMapOf<String, List<String>>()

        // Track the file's own unit name separately to avoid self-reference during recursion
        val fileUnitName = extractUnitName(file)?.lowercase()

        // Resolve all direct dependencies first
        for (unitName in directUnits) {
            resolveTransitive(unitName, visited, unitGraph, project, 0, maxDepth, fileUnitName)
        }

        LOG.debug("[TransitiveDeps] Total transitive units: ${visited.size}")

        return TransitiveDependencyResult(
            directUnits = directUnits,
            transitiveUnits = visited.toSet(),
            unitGraph = unitGraph.toMap()
        )
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

        // Skip if this is the origin file (avoid self-reference)
        if (originUnitName != null && lowerUnit == originUnitName) {
            return
        }

        // Skip if already visited
        if (visited.contains(lowerUnit)) {
            return
        }

        // Mark as visited
        visited.add(lowerUnit)

        // Check depth limit
        if (currentDepth >= maxDepth) {
            LOG.debug("[TransitiveDeps] Max depth reached at: $unitName")
            return
        }

        // Resolve unit name to file
        val projectService = PascalProjectService.getInstance(project)
        val virtualFile = projectService.resolveUnit(unitName, true)

        if (virtualFile == null) {
            LOG.debug("[TransitiveDeps] Could not resolve unit: $unitName")
            return
        }

        // Get PsiFile from VirtualFile
        val psiFile = getPsiFile(virtualFile, project)
        if (psiFile == null) {
            LOG.debug("[TransitiveDeps] Could not get PsiFile for: $unitName")
            return
        }

        // Parse uses clause of the resolved file
        val usesInfo = PascalUsesClauseInfo.parse(psiFile)
        val dependencies = usesInfo.allUses

        // Store in graph for debugging
        unitGraph[lowerUnit] = dependencies

        LOG.debug("[TransitiveDeps] Unit '$unitName' uses: $dependencies")

        // Recurse into dependencies
        for (depUnit in dependencies) {
            resolveTransitive(depUnit, visited, unitGraph, project, currentDepth + 1, maxDepth, originUnitName)
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
        // Remove extension (.pas, .dpr, etc.)
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            fileName.substring(0, dotIndex)
        } else {
            fileName
        }
    }
}
