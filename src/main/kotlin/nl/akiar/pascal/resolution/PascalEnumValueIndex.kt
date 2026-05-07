package nl.akiar.pascal.resolution

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalElementTypes
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.settings.PascalSourcePathsSettings
import nl.akiar.pascal.stubs.PascalEnumValueStubIndex
import nl.akiar.pascal.uses.PascalUsesClauseUtil

/**
 * Resolves unqualified enum-value references like `crNormal` using a real
 * stub index ({@link PascalEnumValueStubIndex}). Each {@code TYPE_DEFINITION}
 * stub registers its enum value names at build time, so lookup is O(1) and
 * never requires loading AST.
 *
 * Lookup strategy:
 *   1. Stub-index query: `name.lowercase()` → list of containing TypeDefinitions.
 *   2. For each candidate, validate scope: same file OR the type's unit is in
 *      the caller's uses clause.
 *   3. To return an actual ENUM_ELEMENT (for navigation/docs), walk the type's
 *      PSI for the matching value. This loads AST for that one file the first
 *      time, then it stays cached.
 *
 * The earlier implementations (project-wide PSI scans, async background
 * builds) either froze the IDE for tens of seconds or OOM'd. This is the
 * proper architectural fix.
 */
object PascalEnumValueIndex {

    /**
     * Stub-only check: is this name a known enum value somewhere in scope?
     * Does NOT load AST — safe for hot daemon paths (semantic highlighting).
     */
    @JvmStatic
    fun isKnownEnumValueInScope(name: String, file: PsiFile, offset: Int): Boolean {
        return findContainingType(name, file, offset) != null
    }

    /**
     * Resolve to the actual ENUM_ELEMENT PSI for navigation / docs.
     * Loads AST of the containing type's file the first time.
     */
    @JvmStatic
    fun findEnumValueInScope(name: String, file: PsiFile, offset: Int): PsiElement? {
        val containingType = findContainingType(name, file, offset) ?: return null
        val nameLc = name.lowercase()
        var match: PsiElement? = null
        PsiTreeUtil.processElements(containingType) { element ->
            if (element.node?.elementType == PascalElementTypes.ENUM_ELEMENT) {
                val valueName = extractEnumValueName(element)
                if (valueName?.lowercase() == nameLc) {
                    match = element
                    return@processElements false
                }
            }
            true
        }
        return match
    }

    /** Compatibility shim — no-op now that we use stub index, no manual cache to invalidate. */
    @JvmStatic
    fun invalidate(@Suppress("UNUSED_PARAMETER") project: Project) {
        // Stub index invalidation is handled by IntelliJ on file changes.
    }

    private fun findContainingType(name: String, file: PsiFile, offset: Int): PascalTypeDefinition? {
        val project = file.project
        val candidates = StubIndex.getElements(
            PascalEnumValueStubIndex.KEY,
            name.lowercase(),
            project,
            GlobalSearchScope.allScope(project),
            PascalTypeDefinition::class.java,
        )
        if (candidates.isEmpty()) return null
        val usesInfo = PascalUsesClauseUtil.parseUsesClause(file)
        val scopes = PascalSourcePathsSettings.getInstance(project).unitScopeNames
        for (candidate in candidates) {
            if (!candidate.isValid) continue
            val candidateFile = candidate.containingFile ?: continue
            if (candidateFile == file) return candidate
            val unitName = candidate.unitName ?: continue
            if (usesInfo.findUnitInUses(unitName, offset, scopes) != null) return candidate
        }
        return null
    }

    private fun extractEnumValueName(enumEl: PsiElement): String? {
        for (child in enumEl.children) {
            if (child.node?.elementType == PascalTokenTypes.IDENTIFIER) return child.text
        }
        val raw = enumEl.text ?: return null
        val eqIdx = raw.indexOf('=')
        return (if (eqIdx > 0) raw.substring(0, eqIdx) else raw).trim().ifEmpty { null }
    }
}
