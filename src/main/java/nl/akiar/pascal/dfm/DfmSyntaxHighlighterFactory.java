package nl.akiar.pascal.dfm;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating DFM syntax highlighter
 */
public class DfmSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
    private static final Logger LOG = Logger.getInstance("#nl.akiar.pascal.dfm.DfmSyntaxHighlighterFactory");

    @NotNull
    @Override
    public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        LOG.info("DFM-PLUGIN: getSyntaxHighlighter called for " + (virtualFile != null ? virtualFile.getName() : "null"));
        try {
            return new DfmSyntaxHighlighter();
        } catch (Exception e) {
            LOG.error("DFM-PLUGIN: Error creating syntax highlighter", e);
            throw e;
        }
    }
}

