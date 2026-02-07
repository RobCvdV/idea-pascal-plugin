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

    /**
     * Find the first node matching any of the given element types.
     * Useful for finding identifiers that might be tokenized as keywords (like "Index").
     */
    @Nullable
    public static ASTNode findFirstRecursiveAnyOf(@NotNull ASTNode node, @NotNull IElementType... types) {
        for (IElementType type : types) {
            if (node.getElementType() == type) return node;
        }
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            ASTNode found = findFirstRecursiveAnyOf(child, types);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Find all nodes matching any of the given element types.
     */
    @NotNull
    public static List<ASTNode> findAllRecursiveAnyOf(@NotNull ASTNode node, @NotNull IElementType... types) {
        List<ASTNode> result = new ArrayList<>();
        findAllRecursiveAnyOf(node, result, types);
        return result;
    }

    private static void findAllRecursiveAnyOf(@NotNull ASTNode node, @NotNull List<ASTNode> result, @NotNull IElementType... types) {
        for (IElementType type : types) {
            if (node.getElementType() == type) {
                result.add(node);
                break;
            }
        }
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            findAllRecursiveAnyOf(child, result, types);
        }
    }

    /**
     * Element types that can serve as identifiers in Pascal.
     * These are "soft keywords" that have special meaning in certain contexts
     * but can also be used as regular identifiers (variable names, parameter names, etc.)
     */
    public static final IElementType[] IDENTIFIER_LIKE_TYPES = {
        nl.akiar.pascal.PascalTokenTypes.IDENTIFIER,
        // Property specifier keywords that can be used as identifiers
        nl.akiar.pascal.PascalTokenTypes.KW_INDEX,
        nl.akiar.pascal.PascalTokenTypes.KW_NAME,
        nl.akiar.pascal.PascalTokenTypes.KW_READ,
        nl.akiar.pascal.PascalTokenTypes.KW_WRITE,
        nl.akiar.pascal.PascalTokenTypes.KW_DEFAULT,
        nl.akiar.pascal.PascalTokenTypes.KW_STORED,
        nl.akiar.pascal.PascalTokenTypes.KW_NODEFAULT,
        nl.akiar.pascal.PascalTokenTypes.KW_IMPLEMENTS,
        // Other soft keywords
        nl.akiar.pascal.PascalTokenTypes.KW_MESSAGE,
        nl.akiar.pascal.PascalTokenTypes.KW_DISPID,
    };

    @Nullable
    public static String getVisibility(@NotNull PsiElement element) {
        // Find the containing TYPE_DEFINITION
        PsiElement typeDefParent = element.getParent();
        while (typeDefParent != null) {
            ASTNode node = typeDefParent.getNode();
            if (node != null && node.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
                break;
            }
            typeDefParent = typeDefParent.getParent();
        }

        if (typeDefParent == null) return null;

        // Strategy 1: Check if element is inside a VISIBILITY_SECTION and extract visibility from there
        PsiElement visSection = element.getParent();
        while (visSection != null && visSection != typeDefParent) {
            if (visSection.getNode() != null &&
                visSection.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.VISIBILITY_SECTION) {
                // Found the visibility section, extract the visibility keyword from it
                return extractVisibilityFromSection(visSection.getNode());
            }
            visSection = visSection.getParent();
        }

        // Strategy 2: Traverse TYPE_DEFINITION children to find visibility keywords
        // This handles flat structures where visibility keywords are direct children
        String lastVisibility = "public"; // default
        ASTNode node = typeDefParent.getNode();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            // If element is inside this child, check this child for visibility then break
            if (isAncestor(child.getPsi(), element)) {
                // Check if this child contains visibility keywords
                String foundVis = extractVisibilityFromNode(child);
                if (foundVis != null) {
                    return foundVis;
                }
                break;
            }

            // Check for visibility keywords at this level
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

    @Nullable
    private static String extractVisibilityFromSection(ASTNode sectionNode) {
        // Look for the first visibility keyword in this section
        for (ASTNode child = sectionNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();
            if (type == nl.akiar.pascal.PascalTokenTypes.KW_PRIVATE) {
                // Check if preceded by STRICT
                ASTNode prev = child.getTreePrev();
                while (prev != null && (prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE)) {
                    prev = prev.getTreePrev();
                }
                if (prev != null && prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.KW_STRICT) {
                    return "strict private";
                }
                return "private";
            } else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PROTECTED) {
                ASTNode prev = child.getTreePrev();
                while (prev != null && (prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE)) {
                    prev = prev.getTreePrev();
                }
                if (prev != null && prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.KW_STRICT) {
                    return "strict protected";
                }
                return "protected";
            } else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PUBLIC) {
                return "public";
            } else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PUBLISHED) {
                return "published";
            }
        }
        return null;
    }

    @Nullable
    private static String extractVisibilityFromNode(ASTNode node) {
        // Recursively search for visibility keywords in this node
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();
            if (type == nl.akiar.pascal.PascalTokenTypes.KW_PRIVATE) {
                ASTNode prev = child.getTreePrev();
                while (prev != null && (prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE)) {
                    prev = prev.getTreePrev();
                }
                if (prev != null && prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.KW_STRICT) {
                    return "strict private";
                }
                return "private";
            } else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PROTECTED) {
                ASTNode prev = child.getTreePrev();
                while (prev != null && (prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE)) {
                    prev = prev.getTreePrev();
                }
                if (prev != null && prev.getElementType() == nl.akiar.pascal.PascalTokenTypes.KW_STRICT) {
                    return "strict protected";
                }
                return "protected";
            } else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PUBLIC) {
                return "public";
            } else if (type == nl.akiar.pascal.PascalTokenTypes.KW_PUBLISHED) {
                return "published";
            }
            // Recurse into children
            String fromChild = extractVisibilityFromNode(child);
            if (fromChild != null) return fromChild;
        }
        return null;
    }

    @Nullable
    public static String getSection(@NotNull PsiElement element) {
        // Walk up the PSI tree to find containing interface or implementation section
        PsiElement current = element;
        while (current != null) {
            ASTNode node = current.getNode();
            if (node != null) {
                IElementType type = node.getElementType();
                // Check for interface and implementation section markers
                if (type == nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_SECTION) {
                    return "interface";
                }
                if (type == nl.akiar.pascal.psi.PascalElementTypes.IMPLEMENTATION_SECTION) {
                    return "implementation";
                }
            }
            current = current.getParent();
        }
        return null;
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

    @Nullable
    public static PsiElement getNextNoneIgnorableSibling(@NotNull PsiElement element) {
        PsiElement next = element.getNextSibling();
        while (next != null) {
            IElementType type = next.getNode().getElementType();
            if (type != nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE &&
                type != nl.akiar.pascal.PascalTokenTypes.LINE_COMMENT &&
                type != nl.akiar.pascal.PascalTokenTypes.BLOCK_COMMENT &&
                type != nl.akiar.pascal.PascalTokenTypes.COMPILER_DIRECTIVE) {
                return next;
            }
            next = next.getNextSibling();
        }
        return null;
    }

    @Nullable
    public static PsiElement getPrevNoneIgnorableSibling(@NotNull PsiElement element) {
        PsiElement prev = element.getPrevSibling();
        while (prev != null) {
            IElementType type = prev.getNode().getElementType();
            if (type != nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE &&
                type != nl.akiar.pascal.PascalTokenTypes.LINE_COMMENT &&
                type != nl.akiar.pascal.PascalTokenTypes.BLOCK_COMMENT &&
                type != nl.akiar.pascal.PascalTokenTypes.COMPILER_DIRECTIVE) {
                return prev;
            }
            prev = prev.getPrevSibling();
        }
        return null;
    }

    @NotNull
    public static String getUnitName(@NotNull com.intellij.psi.PsiElement element) {
        // Avoid traversing PSI tree to prevent AST loading during stub-based operations.
        // Use containing file's name without extension as unit name.
        com.intellij.psi.PsiFile file = element.getContainingFile();
        if (file != null) {
            String fileName = file.getName();
            int dotIndex = fileName.lastIndexOf('.');
            String unit = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
            return normalizeUnitName(unit);
        }
        return "";
    }

    @NotNull
    public static String normalizeUnitName(@NotNull String name) {
        // Remove comments { ... }
        String result = name.replaceAll("\\{.*?\\}", "");
        // Remove comments (* ... *)
        result = result.replaceAll("\\(\\*.*?\\*\\)", "");
        // Remove comments // ... (to end of string or newline)
        result = result.replaceAll("//.*", "");
        // Remove all whitespace
        result = result.replaceAll("\\s+", "");
        return result.toLowerCase();
    }

    @NotNull
    public static String extractUnitNameFromSection(@NotNull ASTNode sectionNode) {
        StringBuilder sb = new StringBuilder();
        boolean foundUnitKeyword = false;

        for (ASTNode child = sectionNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();
            if (type == nl.akiar.pascal.PascalTokenTypes.KW_UNIT ||
                type == nl.akiar.pascal.PascalTokenTypes.KW_PROGRAM ||
                type == nl.akiar.pascal.PascalTokenTypes.KW_LIBRARY) {
                foundUnitKeyword = true;
                continue;
            }

            if (foundUnitKeyword) {
                if (type == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER ||
                    type == nl.akiar.pascal.PascalTokenTypes.DOT ||
                    type == nl.akiar.pascal.psi.PascalElementTypes.UNIT_REFERENCE) {
                    sb.append(child.getText());
                } else if (type == nl.akiar.pascal.PascalTokenTypes.SEMI) {
                    break;
                }
            }
        }

        String result = sb.toString().trim();
        if (result.isEmpty()) {
            // Fallback: just find the first identifier if the above logic fails
            ASTNode idNode = sectionNode.findChildByType(nl.akiar.pascal.PascalTokenTypes.IDENTIFIER);
            if (idNode != null) return normalizeUnitName(idNode.getText());
        }
        return normalizeUnitName(result);
    }
}
