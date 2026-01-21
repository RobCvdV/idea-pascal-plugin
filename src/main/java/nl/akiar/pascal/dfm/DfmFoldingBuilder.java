package nl.akiar.pascal.dfm;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.editor.FoldingGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Folding builder for DFM files - provides code folding for object...end and item blocks
 */
public class DfmFoldingBuilder extends FoldingBuilderEx {
    private static final Logger LOG = Logger.getInstance("#nl.akiar.pascal.dfm.DfmFoldingBuilder");

    // Pattern to match object/inherited/inline keywords (case insensitive)
    // These require something after the keyword (object name)
    private static final Pattern OBJECT_START_PATTERN =
        Pattern.compile("(?i)^\\s*(object|inherited|inline)\\s+", Pattern.MULTILINE);

    // Pattern to match standalone 'item' keyword (can be alone on a line)
    private static final Pattern ITEM_START_PATTERN =
        Pattern.compile("(?i)^\\s*item\\s*$", Pattern.MULTILINE);

    // Pattern to match 'end' keyword
    private static final Pattern END_PATTERN =
        Pattern.compile("(?i)^\\s*end\\s*$", Pattern.MULTILINE);

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        LOG.info("DFM-PLUGIN: buildFoldRegions called, quick=" + quick);
        try {
            List<FoldingDescriptor> descriptors = new ArrayList<>();

            String text = document.getText();
            int documentLength = document.getTextLength();

            // Find all object/inherited/inline...end blocks
            findBlockFolds(text, documentLength, root, descriptors);

            LOG.info("DFM-PLUGIN: buildFoldRegions found " + descriptors.size() + " regions");
            return descriptors.toArray(new FoldingDescriptor[0]);
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("DFM-PLUGIN: Error building fold regions", e);
            throw e;
        }
    }

    private void findBlockFolds(String text, int documentLength, PsiElement root,
                                List<FoldingDescriptor> descriptors) {
        // Use a stack-based approach to match nested blocks
        Stack<Integer> startPositions = new Stack<>();
        Stack<Integer> lineEndPositions = new Stack<>();

        String[] lines = text.split("\n");
        int currentPos = 0;
        int foldIndex = 0;

        for (String line : lines) {
            int lineStart = currentPos;
            int lineEnd = currentPos + line.length();

            // Check for block start (object/inherited/inline)
            Matcher objectMatcher = OBJECT_START_PATTERN.matcher(line);
            Matcher itemMatcher = ITEM_START_PATTERN.matcher(line);
            if (objectMatcher.find() || itemMatcher.find()) {
                startPositions.push(lineStart);
                lineEndPositions.push(lineEnd);
            }

            // Check for end keyword
            Matcher endMatcher = END_PATTERN.matcher(line);
            if (endMatcher.find() && !startPositions.isEmpty()) {
                int blockStart = startPositions.pop();
                int firstLineEnd = lineEndPositions.pop();
                int blockEnd = lineStart; // Fold ends at the start of 'end' line

                // Create fold from end of first line to start of 'end' line
                if (firstLineEnd < blockEnd && firstLineEnd < documentLength && blockEnd <= documentLength) {
                    // Make sure we have something to fold (at least one line between)
                    if (blockEnd - firstLineEnd > 1) {
                        try {
                            TextRange range = new TextRange(firstLineEnd, blockEnd);
                            // Find PSI element at the block start for unique node
                            PsiElement elementAtStart = root.getContainingFile().findElementAt(blockStart);
                            ASTNode node = (elementAtStart != null) ? elementAtStart.getNode() : root.getNode();
                            if (node == null) node = root.getNode();

                            // Use FoldingGroup to allow nested folds with same node
                            FoldingGroup group = FoldingGroup.newGroup("dfm-fold-" + foldIndex++);
                            descriptors.add(new FoldingDescriptor(node, range, group));
                        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                            throw e;
                        } catch (Exception e) {
                            // Skip invalid ranges
                            LOG.warn("DFM-PLUGIN: Failed to create fold region", e);
                        }
                    }
                }
            }

            // Move to next line (+1 for newline character)
            currentPos = lineEnd + 1;
        }
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
