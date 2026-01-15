package com.mendrix.pascal;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Token type for Pascal language elements
 */
public class PascalTokenType extends IElementType {
    public PascalTokenType(@NotNull @NonNls String debugName) {
        super(debugName, PascalLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "PascalTokenType." + super.toString();
    }
}
