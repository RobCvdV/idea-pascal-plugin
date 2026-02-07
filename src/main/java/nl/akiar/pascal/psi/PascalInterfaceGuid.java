package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an interface GUID declaration in Pascal.
 * Interface GUIDs have the format ['{GUID-STRING}'], e.g.:
 * <pre>
 *   IMyInterface = interface
 *     ['{285DEA8A-B865-11D1-AAA7-00C04FB17A72}']
 *     procedure DoSomething;
 *   end;
 * </pre>
 */
public interface PascalInterfaceGuid extends PsiElement {
    /**
     * Get the GUID string without braces and quotes.
     * For ['{285DEA8A-B865-11D1-AAA7-00C04FB17A72}'], returns "285DEA8A-B865-11D1-AAA7-00C04FB17A72".
     *
     * @return The GUID string, or null if malformed
     */
    @Nullable
    String getGuidValue();

    /**
     * Get the full GUID text including brackets, braces and quotes.
     * For example: "['{285DEA8A-B865-11D1-AAA7-00C04FB17A72}']"
     *
     * @return The full GUID text as written in source
     */
    @NotNull
    String getFullText();
}

