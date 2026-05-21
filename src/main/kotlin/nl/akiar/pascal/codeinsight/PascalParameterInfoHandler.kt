package nl.akiar.pascal.codeinsight

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import nl.akiar.pascal.PascalTokenTypes
import nl.akiar.pascal.psi.PascalRoutine
import nl.akiar.pascal.psi.PascalRoutineSignature
import nl.akiar.pascal.psi.PascalTypeDefinition
import nl.akiar.pascal.psi.PascalVariableDefinition
import nl.akiar.pascal.psi.PsiUtil
import nl.akiar.pascal.reference.PascalRoutineCallReference
import nl.akiar.pascal.stubs.PascalRoutineIndex

/**
 * Parameter info popup (Cmd+P) for Pascal routine calls.
 *
 * The sonar-delphi-backed parser does NOT emit a dedicated ARGUMENT_LIST PSI
 * node for routine calls — the parentheses and commas are floating leaves
 * inside the enclosing compound statement. So we anchor on the LPAREN itself:
 * walk leaves backwards from the caret, find the matching unbalanced LPAREN,
 * and verify it's preceded by an identifier (= the callee).
 */
class PascalParameterInfoHandler : ParameterInfoHandler<PsiElement, PascalRoutine> {

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val site = findCallSite(context.file, context.offset) ?: return null
        val candidates = resolveCandidates(site.callee).toTypedArray()
        if (candidates.isEmpty()) return null
        context.itemsToShow = candidates
        return site.lparen
    }

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.endOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
        return findCallSite(context.file, context.offset)?.lparen
    }

    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        val lparenOffset = parameterOwner.textRange.endOffset
        val index = countTopLevelCommas(context.file, lparenOffset, context.offset)
        context.setCurrentParameter(index)
    }

    override fun updateUI(routine: PascalRoutine?, context: ParameterInfoUIContext) {
        if (routine == null) {
            context.setUIComponentEnabled(false)
            return
        }
        val signature = routine.signature
        val params = signature?.parameters.orEmpty()
        val rendering = renderParameters(params)
        val currentIndex = context.currentParameterIndex
        val highlightStart: Int
        val highlightEnd: Int
        if (currentIndex in params.indices) {
            highlightStart = rendering.ranges[currentIndex].first
            highlightEnd = rendering.ranges[currentIndex].second
        } else {
            highlightStart = -1
            highlightEnd = -1
        }
        context.setupUIComponentPresentation(
            rendering.text,
            highlightStart,
            highlightEnd,
            !context.isUIComponentEnabled,
            false,
            false,
            context.defaultParameterColor
        )
    }

    // --- helpers ---

    internal data class CallSite(val lparen: PsiElement, val callee: PsiElement)

    /** Scan leaves strictly *before* the caret, tracking paren depth. The first unbalanced LPAREN is our anchor. */
    internal fun findCallSite(file: PsiFile, offset: Int): CallSite? {
        val safeOffset = offset.coerceIn(0, file.textLength)
        // Always step to the leaf strictly before the caret: that way RPARENs at or beyond
        // the caret (which belong to outer scopes) don't poison the paren-depth counter.
        var current: PsiElement? = if (safeOffset > 0) file.findElementAt(safeOffset - 1) else null
        // If we ended up on a multi-char leaf that the caret is INSIDE of, that's fine — walk back
        // from there. If we ended up exactly on the RPAREN of our own call, step back past it.
        if (current?.node?.elementType == PascalTokenTypes.RPAREN
            && current.textRange.endOffset == safeOffset) {
            current = PsiTreeUtil.prevLeaf(current)
        }
        var depth = 0
        while (current != null) {
            val t = current.node?.elementType
            when (t) {
                PascalTokenTypes.RPAREN -> depth++
                PascalTokenTypes.LPAREN -> {
                    if (depth == 0) {
                        val callee = findCalleeIdentifierBefore(current) ?: return null
                        return CallSite(current, callee)
                    }
                    depth--
                }
                PascalTokenTypes.SEMI, PascalTokenTypes.KW_BEGIN, PascalTokenTypes.KW_END -> {
                    return null
                }
                else -> { /* keep scanning */ }
            }
            current = PsiTreeUtil.prevLeaf(current)
        }
        return null
    }

    private fun findCalleeIdentifierBefore(lparen: PsiElement): PsiElement? {
        var prev: PsiElement? = PsiTreeUtil.prevLeaf(lparen)
        while (prev != null && (prev is PsiWhiteSpace || prev is PsiComment)) {
            prev = PsiTreeUtil.prevLeaf(prev)
        }
        if (prev == null) return null
        val type = prev.node?.elementType ?: return null
        if (PsiUtil.IDENTIFIER_LIKE_TYPES.none { it == type }) return null
        return prev
    }

    /** Count commas between [fromOffset] and [toOffset] ignoring nested parens. */
    private fun countTopLevelCommas(file: PsiFile, fromOffset: Int, toOffset: Int): Int {
        if (toOffset <= fromOffset) return 0
        val text = file.text
        var depth = 0
        var count = 0
        val end = toOffset.coerceAtMost(text.length)
        var i = fromOffset
        while (i < end) {
            when (text[i]) {
                '(', '[' -> depth++
                ')', ']' -> {
                    if (depth == 0) return count
                    depth--
                }
                ',' -> if (depth == 0) count++
            }
            i++
        }
        return count
    }

    private fun resolveCandidates(callee: PsiElement): List<PascalRoutine> {
        val name = callee.text
        if (name.isNullOrBlank()) return emptyList()

        val results = LinkedHashMap<String, PascalRoutine>()

        val direct = PascalRoutineCallReference(callee).resolve()
        if (direct is PascalRoutine) {
            results.putIfAbsent(signatureKey(direct), direct)
            direct.containingClass?.let { owner ->
                for (m in owner.getMembers(true)) {
                    if (m is PascalRoutine && name.equals(m.name, ignoreCase = true)) {
                        results.putIfAbsent(signatureKey(m), m)
                    }
                }
            }
        }

        val file = callee.containingFile
        val offset = callee.textOffset
        val routines = PascalRoutineIndex.findRoutinesWithUsesValidation(name, file, offset).inScopeRoutines
        for (r in routines) {
            results.putIfAbsent(signatureKey(r), r)
        }
        return results.values.toList()
    }

    private fun signatureKey(routine: PascalRoutine): String {
        val sig = routine.signature
        val owner = routine.containingClass?.name ?: ""
        val params = sig?.parameters?.joinToString(",") { it.typeName ?: "?" } ?: ""
        return "$owner#${routine.name}($params)"
    }

    private data class Rendering(val text: String, val ranges: List<Pair<Int, Int>>)

    private fun renderParameters(params: List<PascalVariableDefinition>): Rendering {
        if (params.isEmpty()) return Rendering("<no parameters>", emptyList())
        val sb = StringBuilder()
        val ranges = mutableListOf<Pair<Int, Int>>()
        for ((i, p) in params.withIndex()) {
            if (i > 0) sb.append(", ")
            val start = sb.length
            val mod = parameterModifier(p)
            if (mod.isNotEmpty()) sb.append(mod).append(' ')
            sb.append(p.name ?: "_")
            val typeName = p.typeName
            if (!typeName.isNullOrBlank()) sb.append(": ").append(typeName)
            ranges.add(start to sb.length)
        }
        return Rendering(sb.toString(), ranges)
    }

    private fun parameterModifier(p: PascalVariableDefinition): String {
        return try {
            when {
                p.isVarParameter -> "var"
                p.isConstParameter -> "const"
                p.isOutParameter -> "out"
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private val PascalRoutine.signature: PascalRoutineSignature?
        get() = PsiTreeUtil.findChildOfType(this, PascalRoutineSignature::class.java)

    private val PascalRoutine.containingClass: PascalTypeDefinition?
        get() = this.getContainingClass()
}
