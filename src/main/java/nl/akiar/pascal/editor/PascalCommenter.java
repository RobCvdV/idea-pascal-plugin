package nl.akiar.pascal.editor;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/**
 * Commenter for Object Pascal (Delphi-style) language.
 * Provides line and block comment prefixes for IDE toggle actions.
 */
public class PascalCommenter implements Commenter {
    @Override
    public String getLineCommentPrefix() {
        return "//";
    }

    @Override
    public String getBlockCommentPrefix() {
        return "{";
    }

    @Override
    public String getBlockCommentSuffix() {
        return "}";
    }

    @Override
    public @Nullable String getCommentedBlockCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentSuffix() {
        return null;
    }
}
