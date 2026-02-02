package nl.akiar.pascal.resolution

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiModificationTracker
import nl.akiar.pascal.psi.PascalTypeDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches inheritance chain information for Pascal types.
 * Provides fast access to superclasses and ancestor lists without repeated resolution.
 *
 * Cache is invalidated on any PSI modification (global mod count change).
 */
object InheritanceChainCache {
    private val LOG = Logger.getInstance(InheritanceChainCache::class.java)

    /**
     * Information about a type's inheritance chain.
     */
    data class InheritanceInfo(
        /** Direct superclass name (null if no superclass) */
        val superClassName: String?,
        /** All ancestor type names, in order from nearest to farthest */
        val ancestorNames: List<String>,
        /** True if a cycle was detected in the inheritance chain */
        val hasCycle: Boolean
    )

    private data class CacheKey(
        val typePtr: SmartPsiElementPointer<PascalTypeDefinition>,
        val typeName: String,
        val unitName: String?
    )

    private data class CacheValue(
        val info: InheritanceInfo,
        /** Pointers to resolved ancestor types (may contain nulls for unresolved ancestors) */
        val ancestorPtrs: List<SmartPsiElementPointer<PascalTypeDefinition>?>
    )

    private val cache = ConcurrentHashMap<CacheKey, CacheValue>()
    @Volatile private var lastModCount: Long = -1L

    private fun ensureFresh(project: Project) {
        val mod = PsiModificationTracker.getInstance(project).modificationCount
        if (mod != lastModCount) {
            synchronized(this) {
                if (mod != lastModCount) {
                    cache.clear()
                    lastModCount = mod
                }
            }
        }
    }

    /**
     * Get inheritance information for a type definition.
     *
     * @param typeDef The type definition to get inheritance info for
     * @return InheritanceInfo containing superclass name, all ancestors, and cycle detection
     */
    @JvmStatic
    fun getInheritanceInfo(typeDef: PascalTypeDefinition): InheritanceInfo {
        val project = typeDef.project
        ensureFresh(project)

        val spm = SmartPointerManager.getInstance(project)
        val typePtr = spm.createSmartPsiElementPointer(typeDef)
        val key = CacheKey(typePtr, typeDef.name ?: "", typeDef.unitName)

        val cached = cache[key]
        if (cached != null) {
            return cached.info
        }

        // Compute inheritance chain
        val (info, ancestorPtrs) = computeInheritanceChain(typeDef, spm)
        cache[key] = CacheValue(info, ancestorPtrs)
        return info
    }

    /**
     * Get all ancestor type definitions (resolved) for a type.
     *
     * @param typeDef The type definition
     * @return List of resolved ancestor types, in order from nearest to farthest.
     *         Unresolvable ancestors are skipped.
     */
    @JvmStatic
    fun getAllAncestorTypes(typeDef: PascalTypeDefinition): List<PascalTypeDefinition> {
        val project = typeDef.project
        ensureFresh(project)

        val spm = SmartPointerManager.getInstance(project)
        val typePtr = spm.createSmartPsiElementPointer(typeDef)
        val key = CacheKey(typePtr, typeDef.name ?: "", typeDef.unitName)

        val cached = cache[key]
        if (cached != null) {
            return cached.ancestorPtrs.mapNotNull { it?.element?.takeIf { el -> el.isValid } }
        }

        // Compute and cache
        val (info, ancestorPtrs) = computeInheritanceChain(typeDef, spm)
        cache[key] = CacheValue(info, ancestorPtrs)
        return ancestorPtrs.mapNotNull { it?.element?.takeIf { el -> el.isValid } }
    }

    /**
     * Check if a type is a descendant of another type.
     *
     * @param subType The potential descendant type
     * @param superTypeName The name of the potential ancestor type (case-insensitive)
     * @return true if subType descends from a type with the given name
     */
    @JvmStatic
    fun isDescendantOf(subType: PascalTypeDefinition, superTypeName: String): Boolean {
        val info = getInheritanceInfo(subType)
        return info.ancestorNames.any { it.equals(superTypeName, ignoreCase = true) }
    }

    /**
     * Compute the inheritance chain for a type definition.
     *
     * @return Pair of (InheritanceInfo, list of ancestor pointers)
     */
    private fun computeInheritanceChain(
        typeDef: PascalTypeDefinition,
        spm: SmartPointerManager
    ): Pair<InheritanceInfo, List<SmartPsiElementPointer<PascalTypeDefinition>?>> {
        val ancestorNames = mutableListOf<String>()
        val ancestorPtrs = mutableListOf<SmartPsiElementPointer<PascalTypeDefinition>?>()
        val visited = mutableSetOf<String>()
        var hasCycle = false

        // Add the starting type to visited set (using qualified key)
        val startKey = "${typeDef.unitName}.${typeDef.name}".lowercase()
        visited.add(startKey)

        var current: PascalTypeDefinition? = typeDef
        var superClassName: String? = null

        while (current != null) {
            val currentSuperName = current.superClassName
            if (superClassName == null) {
                superClassName = currentSuperName  // Remember the direct superclass
            }

            if (currentSuperName == null) {
                break  // No more superclasses
            }

            // Resolve the superclass
            val superClass = current.superClass
            if (superClass != null) {
                val superKey = "${superClass.unitName}.${superClass.name}".lowercase()
                if (visited.contains(superKey)) {
                    LOG.warn("Cycle detected in inheritance chain for ${typeDef.name}: $superKey already visited")
                    hasCycle = true
                    break
                }
                visited.add(superKey)
                ancestorNames.add(superClass.name ?: currentSuperName)
                ancestorPtrs.add(spm.createSmartPsiElementPointer(superClass))
                current = superClass
            } else {
                // Superclass couldn't be resolved - record the name but no pointer
                ancestorNames.add(currentSuperName)
                ancestorPtrs.add(null)
                break  // Can't continue chain without resolved type
            }
        }

        val info = InheritanceInfo(
            superClassName = superClassName,
            ancestorNames = ancestorNames,
            hasCycle = hasCycle
        )
        return Pair(info, ancestorPtrs)
    }

    /**
     * Clear all cached inheritance data.
     *
     * @param project Optional project to sync modification counter with
     */
    @JvmStatic
    fun clearAll(project: Project? = null) {
        cache.clear()
        if (project != null) {
            lastModCount = PsiModificationTracker.getInstance(project).modificationCount
        } else {
            lastModCount = -1L
        }
    }
}
