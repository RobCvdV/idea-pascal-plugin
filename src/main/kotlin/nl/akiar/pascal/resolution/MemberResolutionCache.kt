package nl.akiar.pascal.resolution

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiModificationTracker
import nl.akiar.pascal.psi.PascalProperty
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight caches for member traversal to avoid repeated expensive lookups.
 * Caches are invalidated on any PSI modification (global mod count change).
 */
object MemberResolutionCache {
    private data class TypeOfKey(
        val elementPtr: SmartPsiElementPointer<PsiElement>,
        val originPath: String?,
        val typeName: String,
        val offset: Int,
    )

    private data class TypeOfVal(
        val typePtr: SmartPsiElementPointer<PascalTypeDefinition>?
    )

    // Cache for member lists (fields, properties, methods) of a type
    private data class MemberListKey(
        val typePtr: SmartPsiElementPointer<PascalTypeDefinition>,
        val typeName: String,
        val unitName: String?,
        val includeAncestors: Boolean
    )

    private data class MemberListValue(
        val memberPtrs: List<SmartPsiElementPointer<PsiElement>>
    )

    private val typeOfCache = ConcurrentHashMap<TypeOfKey, TypeOfVal>()
    private val memberListCache = ConcurrentHashMap<MemberListKey, MemberListValue>()
    @Volatile private var lastModCount: Long = -1L

    private fun ensureFresh(project: Project) {
        val mod = PsiModificationTracker.getInstance(project).modificationCount
        if (mod != lastModCount) {
            synchronized(this) {
                if (mod != lastModCount) {
                    typeOfCache.clear()
                    memberListCache.clear()
                    lastModCount = mod
                }
            }
        }
    }

    private fun typeNameOf(element: PsiElement?): String? = when (element) {
        is PascalVariableDefinition -> element.typeName
        is PascalProperty -> element.typeName
        is PascalRoutine -> null
        else -> null
    }

    fun getOrComputeTypeOf(element: PsiElement?, originFile: PsiFile, contextFile: PsiFile, compute: () -> PascalTypeDefinition?): PascalTypeDefinition? {
        if (element == null) return null
        val tn = typeNameOf(element) ?: return null
        ensureFresh(originFile.project)
        val spm = SmartPointerManager.getInstance(originFile.project)
        val ePtr = spm.createSmartPsiElementPointer(element)
        val key = TypeOfKey(
            elementPtr = ePtr,
            originPath = originFile.virtualFile?.path, // use origin (call-site) path for cache scoping
            typeName = tn,
            offset = element.textOffset
        )
        val cached = typeOfCache[key]
        if (cached != null) {
            val cachedEl = cached.typePtr?.element
            if (cachedEl != null && cachedEl.isValid) return cachedEl
        }
        val computed = compute()
        val valPtr = computed?.let { spm.createSmartPsiElementPointer(it) }
        typeOfCache[key] = TypeOfVal(valPtr)
        return computed
    }

    /**
     * Get cached members for a type, or compute and cache them.
     *
     * @param typeDef The type definition to get members for
     * @param includeAncestors Whether to include inherited members
     * @param compute Function to compute the members if not cached
     * @return List of member elements (routines, properties, fields)
     */
    fun getOrComputeMembers(
        typeDef: PascalTypeDefinition,
        includeAncestors: Boolean,
        compute: () -> List<PsiElement>
    ): List<PsiElement> {
        val project = typeDef.project
        ensureFresh(project)
        val spm = SmartPointerManager.getInstance(project)
        val typePtr = spm.createSmartPsiElementPointer(typeDef)
        val key = MemberListKey(
            typePtr = typePtr,
            typeName = typeDef.name ?: "",
            unitName = typeDef.unitName,
            includeAncestors = includeAncestors
        )

        val cached = memberListCache[key]
        if (cached != null) {
            val members = cached.memberPtrs.mapNotNull { ptr ->
                ptr.element?.takeIf { it.isValid }
            }
            // If all pointers are still valid, return cached
            if (members.size == cached.memberPtrs.size) {
                return members
            }
            // Some pointers became invalid, recompute
        }

        val computed = compute()
        val ptrs = computed.map { spm.createSmartPsiElementPointer(it) }
        memberListCache[key] = MemberListValue(ptrs)
        return computed
    }

    /**
     * Clear all caches explicitly. Optionally align internal modification counter with the provided project.
     */
    @JvmStatic
    fun clearAll(project: Project? = null) {
        typeOfCache.clear()
        memberListCache.clear()
        if (project != null) {
            lastModCount = PsiModificationTracker.getInstance(project).modificationCount
        } else {
            lastModCount = -1L
        }
    }
}
