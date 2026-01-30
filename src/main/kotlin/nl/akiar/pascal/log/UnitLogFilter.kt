package nl.akiar.pascal.log

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Filters plugin logs to only selected Pascal units, controlled via VM option:
 *   -Dnl.akiar.pascal.log.unitFilter=System\.Classes,MyUnit,MyPkg\..*
 *
 * Accepts comma-separated values, each a regex. Matches against:
 *  - file name (e.g., System.Classes.pas)
 *  - name without extension (e.g., System.Classes)
 *  - full path
 */
object UnitLogFilter {
  private const val KEY = "nl.akiar.pascal.log.unitFilter"

  private val patterns: List<Regex> = System.getProperty(KEY)
    ?.split(',')
    ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.toRegex(RegexOption.IGNORE_CASE) }
    ?: emptyList()

  fun enabled(): Boolean = patterns.isNotEmpty()

  fun shouldLog(file: PsiFile?): Boolean = shouldLog(file?.virtualFile)

  fun shouldLog(element: PsiElement?): Boolean = shouldLog(element?.containingFile?.virtualFile)

  fun shouldLog(unitFqnOrFileName: String?): Boolean {
    if (patterns.isEmpty()) return true
    if (unitFqnOrFileName.isNullOrBlank()) return false
    return patterns.any { rx -> rx.containsMatchIn(unitFqnOrFileName) }
  }

  private fun shouldLog(vf: VirtualFile?): Boolean {
    if (patterns.isEmpty()) return true
    if (vf == null) return false
    val candidates = sequenceOf(
      vf.name,                 // System.Classes.pas
      vf.nameWithoutExtension, // System.Classes
      vf.path                  // full path
    )
    return candidates.any { name -> shouldLog(name) }
  }
}
