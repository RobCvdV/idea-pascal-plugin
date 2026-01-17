package nl.akiar.pascal.dfm;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Token type for DFM language
 */
public class DfmTokenType extends IElementType {
    public DfmTokenType(@NotNull @NonNls String debugName) {
        super(debugName, DfmLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "DfmTokenType." + super.toString();
    }
}

