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

    private val typeOfCache = ConcurrentHashMap<TypeOfKey, TypeOfVal>()
    @Volatile private var lastModCount: Long = -1L

    private fun ensureFresh(project: Project) {
        val mod = PsiModificationTracker.getInstance(project).modificationCount
        if (mod != lastModCount) {
            synchronized(this) {
                if (mod != lastModCount) {
                    typeOfCache.clear()
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
     * Clear all caches explicitly. Optionally align internal modification counter with the provided project.
     */
    @JvmStatic
    fun clearAll(project: Project? = null) {
        typeOfCache.clear()
        if (project != null) {
            lastModCount = PsiModificationTracker.getInstance(project).modificationCount
        } else {
            lastModCount = -1L
        }
    }
}
