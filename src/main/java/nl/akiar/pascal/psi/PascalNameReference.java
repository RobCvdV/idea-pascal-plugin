package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a Pascal name reference, which can be a simple identifier or a qualified name (member chain).
 * Examples:
 * - Simple: X, MyVar, Counter
 * - Qualified: MyObject.Property, System.SysUtils.StrToInt
 */
public interface PascalNameReference extends PascalExpression {

    /**
     * Gets all parts of the name reference.
     * For "a.b.c", returns [a, b, c] as PsiElements.
     *
     * @return list of name parts (identifiers)
     */
    @NotNull
    List<PsiElement> getNameParts();

    /**
     * Gets the qualifier (everything before the last dot).
     * For "a.b.c", returns the PsiElement representing "a.b".
     *
     * @return the qualifier, or null if this is a simple (non-qualified) name
     */
    @Nullable
    PsiElement getQualifier();

    /**
     * Gets just the last part of the name reference.
     * For "a.b.c", returns "c".
     *
     * @return the final name part as a string
     */
    @NotNull
    String getReferenceName();

    /**
     * Checks if this is a qualified name (has dots).
     *
     * @return true if qualified (e.g., "a.b"), false if simple (e.g., "a")
     */
    boolean isQualified();
}
