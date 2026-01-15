package com.mendrix.pascal;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Folding builder for Pascal files.
 * Provides code folding for begin..end, class, record, try..except/finally, case, repeat..until,
 * interface/implementation sections, and block comments.
 */
public class PascalFoldingBuilder extends FoldingBuilderEx {

    // Block types for tracking different fold regions
    private enum BlockType {
        BEGIN, CLASS, RECORD, TRY, CASE, REPEAT, INTERFACE_SECTION, IMPLEMENTATION_SECTION, ASM
    }

    private static class FoldBlock {
        final BlockType type;
        final int startOffset;
        final int lineEndOffset;

        FoldBlock(BlockType type, int startOffset, int lineEndOffset) {
            this.type = type;
            this.startOffset = startOffset;
            this.lineEndOffset = lineEndOffset;
        }
    }

    // Patterns for matching Pascal constructs (case insensitive)
    private static final Pattern BEGIN_PATTERN = Pattern.compile("(?i)\\bbegin\\b");
    private static final Pattern END_PATTERN = Pattern.compile("(?i)\\bend\\b");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?i)=\\s*class\\b(?!\\s*;)(?!\\s*of\\b)");
    private static final Pattern RECORD_PATTERN = Pattern.compile("(?i)=\\s*record\\b");
    private static final Pattern TRY_PATTERN = Pattern.compile("(?i)\\btry\\b");
    private static final Pattern EXCEPT_PATTERN = Pattern.compile("(?i)\\bexcept\\b");
    private static final Pattern FINALLY_PATTERN = Pattern.compile("(?i)\\bfinally\\b");
    private static final Pattern CASE_PATTERN = Pattern.compile("(?i)\\bcase\\b.*\\bof\\b");
    private static final Pattern REPEAT_PATTERN = Pattern.compile("(?i)\\brepeat\\b");
    private static final Pattern UNTIL_PATTERN = Pattern.compile("(?i)\\buntil\\b");
    private static final Pattern INTERFACE_SECTION_PATTERN = Pattern.compile("(?i)^\\s*interface\\s*$");
    private static final Pattern IMPLEMENTATION_PATTERN = Pattern.compile("(?i)^\\s*implementation\\s*$");
    private static final Pattern INITIALIZATION_PATTERN = Pattern.compile("(?i)^\\s*initialization\\s*$");
    private static final Pattern FINALIZATION_PATTERN = Pattern.compile("(?i)^\\s*finalization\\s*$");
    private static final Pattern ASM_PATTERN = Pattern.compile("(?i)\\basm\\b");
    private static final Pattern BLOCK_COMMENT_START = Pattern.compile("\\{|\\(\\*");
    private static final Pattern USES_PATTERN = Pattern.compile("(?i)^\\s*uses\\b");

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        String text = document.getText();
        int documentLength = document.getTextLength();

        findBlockFolds(text, documentLength, root, descriptors);
        findCommentFolds(text, documentLength, root, descriptors);

        return descriptors.toArray(new FoldingDescriptor[0]);
    }

    private void findBlockFolds(String text, int documentLength, PsiElement root,
                                List<FoldingDescriptor> descriptors) {
        Stack<FoldBlock> blockStack = new Stack<>();
        String[] lines = text.split("\n", -1);
        int currentPos = 0;
        int foldIndex = 0;

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];
            int lineStart = currentPos;
            int lineEnd = currentPos + line.length();

            // Skip comment lines for block detection
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("//") || trimmedLine.startsWith("{") || trimmedLine.startsWith("(*")) {
                currentPos = lineEnd + 1;
                continue;
            }

            // Check for interface section (unit structure)
            if (INTERFACE_SECTION_PATTERN.matcher(line).find()) {
                blockStack.push(new FoldBlock(BlockType.INTERFACE_SECTION, lineStart, lineEnd));
            }

            // Check for implementation section
            if (IMPLEMENTATION_PATTERN.matcher(line).find()) {
                // Close interface section if open
                if (!blockStack.isEmpty() && blockStack.peek().type == BlockType.INTERFACE_SECTION) {
                    FoldBlock block = blockStack.pop();
                    createFoldRegion(block, lineStart, documentLength, root, descriptors, foldIndex++);
                }
                blockStack.push(new FoldBlock(BlockType.IMPLEMENTATION_SECTION, lineStart, lineEnd));
            }

            // Check for initialization/finalization (closes implementation)
            if (INITIALIZATION_PATTERN.matcher(line).find() || FINALIZATION_PATTERN.matcher(line).find()) {
                if (!blockStack.isEmpty() && blockStack.peek().type == BlockType.IMPLEMENTATION_SECTION) {
                    FoldBlock block = blockStack.pop();
                    createFoldRegion(block, lineStart, documentLength, root, descriptors, foldIndex++);
                }
            }

            // Check for class definition
            if (CLASS_PATTERN.matcher(line).find()) {
                blockStack.push(new FoldBlock(BlockType.CLASS, lineStart, lineEnd));
            }

            // Check for record definition
            if (RECORD_PATTERN.matcher(line).find()) {
                blockStack.push(new FoldBlock(BlockType.RECORD, lineStart, lineEnd));
            }

            // Check for try block
            if (TRY_PATTERN.matcher(line).find()) {
                blockStack.push(new FoldBlock(BlockType.TRY, lineStart, lineEnd));
            }

            // Check for except (closes try, opens except block)
            if (EXCEPT_PATTERN.matcher(line).find()) {
                if (!blockStack.isEmpty() && blockStack.peek().type == BlockType.TRY) {
                    FoldBlock block = blockStack.pop();
                    createFoldRegion(block, lineStart, documentLength, root, descriptors, foldIndex++);
                }
                blockStack.push(new FoldBlock(BlockType.TRY, lineStart, lineEnd)); // reuse TRY for except block
            }

            // Check for finally (closes try/except, opens finally block)
            if (FINALLY_PATTERN.matcher(line).find()) {
                if (!blockStack.isEmpty() && blockStack.peek().type == BlockType.TRY) {
                    FoldBlock block = blockStack.pop();
                    createFoldRegion(block, lineStart, documentLength, root, descriptors, foldIndex++);
                }
                blockStack.push(new FoldBlock(BlockType.TRY, lineStart, lineEnd)); // reuse TRY for finally block
            }

            // Check for case..of
            if (CASE_PATTERN.matcher(line).find()) {
                blockStack.push(new FoldBlock(BlockType.CASE, lineStart, lineEnd));
            }

            // Check for repeat
            if (REPEAT_PATTERN.matcher(line).find()) {
                blockStack.push(new FoldBlock(BlockType.REPEAT, lineStart, lineEnd));
            }

            // Check for asm
            if (ASM_PATTERN.matcher(line).find()) {
                blockStack.push(new FoldBlock(BlockType.ASM, lineStart, lineEnd));
            }

            // Check for begin
            if (BEGIN_PATTERN.matcher(line).find()) {
                blockStack.push(new FoldBlock(BlockType.BEGIN, lineStart, lineEnd));
            }

            // Check for until (closes repeat)
            if (UNTIL_PATTERN.matcher(line).find()) {
                if (!blockStack.isEmpty() && blockStack.peek().type == BlockType.REPEAT) {
                    FoldBlock block = blockStack.pop();
                    createFoldRegion(block, lineEnd, documentLength, root, descriptors, foldIndex++);
                }
            }

            // Check for end
            if (END_PATTERN.matcher(line).find()) {
                if (!blockStack.isEmpty()) {
                    BlockType topType = blockStack.peek().type;
                    // End closes most block types except interface/implementation sections
                    if (topType != BlockType.INTERFACE_SECTION && topType != BlockType.IMPLEMENTATION_SECTION) {
                        FoldBlock block = blockStack.pop();
                        createFoldRegion(block, lineStart, documentLength, root, descriptors, foldIndex++);
                    }
                    // Check for "end." which closes implementation section
                    if (trimmedLine.matches("(?i)end\\..*")) {
                        while (!blockStack.isEmpty()) {
                            FoldBlock block = blockStack.pop();
                            createFoldRegion(block, lineStart, documentLength, root, descriptors, foldIndex++);
                        }
                    }
                }
            }

            currentPos = lineEnd + 1;
        }
    }

    private void findCommentFolds(String text, int documentLength, PsiElement root,
                                  List<FoldingDescriptor> descriptors) {
        int foldIndex = 1000; // Start with different index to avoid conflicts
        int pos = 0;

        while (pos < documentLength) {
            char c = text.charAt(pos);

            // Block comment {  }
            if (c == '{') {
                int start = pos;
                pos++;
                while (pos < documentLength && text.charAt(pos) != '}') {
                    pos++;
                }
                if (pos < documentLength) {
                    pos++; // include closing }
                    // Only fold multi-line comments
                    if (text.substring(start, pos).contains("\n")) {
                        try {
                            TextRange range = new TextRange(start, pos);
                            PsiElement element = root.getContainingFile().findElementAt(start);
                            ASTNode node = (element != null) ? element.getNode() : root.getNode();
                            if (node == null) node = root.getNode();
                            FoldingGroup group = FoldingGroup.newGroup("pascal-comment-" + foldIndex++);
                            descriptors.add(new FoldingDescriptor(node, range, group));
                        } catch (Exception ignored) {
                        }
                    }
                }
                continue;
            }

            // Block comment (* *)
            if (c == '(' && pos + 1 < documentLength && text.charAt(pos + 1) == '*') {
                int start = pos;
                pos += 2;
                while (pos < documentLength - 1) {
                    if (text.charAt(pos) == '*' && text.charAt(pos + 1) == ')') {
                        pos += 2;
                        break;
                    }
                    pos++;
                }
                // Only fold multi-line comments
                if (text.substring(start, Math.min(pos, documentLength)).contains("\n")) {
                    try {
                        TextRange range = new TextRange(start, Math.min(pos, documentLength));
                        PsiElement element = root.getContainingFile().findElementAt(start);
                        ASTNode node = (element != null) ? element.getNode() : root.getNode();
                        if (node == null) node = root.getNode();
                        FoldingGroup group = FoldingGroup.newGroup("pascal-comment-" + foldIndex++);
                        descriptors.add(new FoldingDescriptor(node, range, group));
                    } catch (Exception ignored) {
                    }
                }
                continue;
            }

            pos++;
        }
    }

    private void createFoldRegion(FoldBlock block, int endLineStart, int documentLength,
                                  PsiElement root, List<FoldingDescriptor> descriptors, int foldIndex) {
        int foldStart = block.lineEndOffset;
        int foldEnd = endLineStart;

        if (foldStart < foldEnd && foldStart < documentLength && foldEnd <= documentLength) {
            if (foldEnd - foldStart > 1) {
                try {
                    TextRange range = new TextRange(foldStart, foldEnd);
                    PsiElement element = root.getContainingFile().findElementAt(block.startOffset);
                    ASTNode node = (element != null) ? element.getNode() : root.getNode();
                    if (node == null) node = root.getNode();
                    FoldingGroup group = FoldingGroup.newGroup("pascal-fold-" + foldIndex);
                    descriptors.add(new FoldingDescriptor(node, range, group));
                } catch (Exception ignored) {
                }
            }
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
