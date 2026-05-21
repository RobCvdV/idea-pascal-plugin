package nl.akiar.pascal.surround

import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Exposes the four Pascal Surround With templates: begin..end, try..except,
 * try..finally, with..do.
 *
 * Surround operates on raw text (selection start to end); we return a tiny
 * synthetic range so the platform invokes our surrounders rather than trying
 * to match PSI-shaped element trees.
 */
class PascalSurroundDescriptor : SurroundDescriptor {

    private val surrounders: Array<Surrounder> = arrayOf(
        PascalBeginEndSurrounder(),
        PascalTryFinallySurrounder(),
        PascalTryExceptSurrounder(),
        PascalWithDoSurrounder(),
    )

    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement> {
        if (startOffset >= endOffset) return PsiElement.EMPTY_ARRAY
        val start = file.findElementAt(startOffset) ?: return PsiElement.EMPTY_ARRAY
        val end = file.findElementAt((endOffset - 1).coerceAtLeast(startOffset)) ?: return PsiElement.EMPTY_ARRAY
        // Return the two boundary leaves; surrounders only use editor offsets,
        // not the PSI shape, so this is enough to satisfy the platform contract.
        return if (start == end) arrayOf(start) else arrayOf(start, end)
    }

    override fun getSurrounders(): Array<Surrounder> = surrounders

    override fun isExclusive(): Boolean = false
}
