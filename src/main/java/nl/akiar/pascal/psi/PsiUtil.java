package nl.akiar.pascal.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PsiUtil {
    @Nullable
    public static ASTNode findFirstRecursive(@NotNull ASTNode node, @NotNull IElementType type) {
        if (node.getElementType() == type) return node;
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            ASTNode found = findFirstRecursive(child, type);
            if (found != null) return found;
        }
        return null;
    }

    @Nullable
    public static ASTNode findLastRecursive(@NotNull ASTNode node, @NotNull IElementType type) {
        ASTNode last = null;
        if (node.getElementType() == type) last = node;
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            ASTNode found = findLastRecursive(child, type);
            if (found != null) last = found;
        }
        return last;
    }

    @NotNull
    public static List<ASTNode> findAllRecursive(@NotNull ASTNode node, @NotNull IElementType type) {
        List<ASTNode> result = new ArrayList<>();
        findAllRecursive(node, type, result);
        return result;
    }

    private static void findAllRecursive(@NotNull ASTNode node, @NotNull IElementType type, @NotNull List<ASTNode> result) {
        if (node.getElementType() == type) result.add(node);
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            findAllRecursive(child, type, result);
        }
    }

    @Nullable
    public static String getVisibility(@NotNull PsiElement element) {
        // Find the containing TYPE_DEFINITION
        PsiElement parent = element.getParent();
        while (parent != null && parent.getNode().getElementType() != nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
            parent = parent.getParent();
        }

        if (parent == null) return null;

        // Traverse children of TYPE_DEFINITION to find the last visibility keyword before this element
        String lastVisibility = "public"; // default
        ASTNode node = parent.getNode();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (isAncestor(child.getPsi(), element)) break;

            IElementType type = child.getElementType();
            if (type == nl.akiar.pascal.PascalTokenTypes.KW_PRIVATE) {
                // Check if preceded by STRICT
                ASTNode prev = child.getTreePrev();
                while (prev != null && (prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE)) {
                    prev = prev.getTreePrev();
                }
                if (prev != null && prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.KW_STRICT) {
                    lastVisibility = "strict private";
                } else {
                    lastVisibility = "private";
                }
            } else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PROTECTED) {
                // Check if preceded by STRICT
                ASTNode prev = child.getTreePrev();
                while (prev != null && (prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE)) {
                    prev = prev.getTreePrev();
                }
                if (prev != null && prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.KW_STRICT) {
                    lastVisibility = "strict protected";
                } else {
                    lastVisibility = "protected";
                }
            } else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PUBLIC) lastVisibility = "public";
            else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PUBLISHED) lastVisibility = "published";
        }
        return lastVisibility;
    }

    public static boolean isAncestor(@NotNull PsiElement ancestor, @NotNull PsiElement element) {
        if (ancestor == element) return true;
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (parent == ancestor) return true;
            parent = parent.getParent();
        }
        return false;
    }

    public static boolean hasParent(@NotNull PsiElement element, @NotNull IElementType type) {
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (parent.getNode() != null && parent.getNode().getElementType() == type) return true;
            parent = parent.getParent();
        }
        return false;
    }
}
