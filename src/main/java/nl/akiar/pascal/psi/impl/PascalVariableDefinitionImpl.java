package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;
import nl.akiar.pascal.stubs.PascalVariableStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalVariableDefinition PSI element.
 */
public class PascalVariableDefinitionImpl extends StubBasedPsiElementBase<PascalVariableStub>
        implements PascalVariableDefinition {

    public PascalVariableDefinitionImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PascalVariableDefinitionImpl(@NotNull PascalVariableStub stub, @NotNull IStubElementType<?, ?> nodeType) {
        super(stub, nodeType);
    }

    @Override
    @Nullable
    public String getName() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getName();
        }

        // Parse from AST - first IDENTIFIER is the name
        PsiElement nameElement = getNameIdentifier();
        if (nameElement != null) {
            return nameElement.getText();
        }
        return null;
    }

    @Override
    @Nullable
    public String getTypeName() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getTypeName();
        }

        // Parse from AST: look for IDENTIFIER after COLON
        ASTNode node = getNode();
        boolean foundColon = false;

        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();

            if (type == PascalTokenTypes.COLON) {
                foundColon = true;
            } else if (foundColon) {
                // Skip whitespace and comments
                if (type == PascalTokenTypes.WHITE_SPACE ||
                    type == PascalTokenTypes.LINE_COMMENT ||
                    type == PascalTokenTypes.BLOCK_COMMENT) {
                    continue;
                }

                // Found the type - could be simple identifier or qualified name
                if (type == PascalTokenTypes.IDENTIFIER ||
                    type == PascalTokenTypes.KW_STRING ||
                    type == PascalTokenTypes.KW_ARRAY ||
                    type == PascalTokenTypes.KW_SET ||
                    type == PascalTokenTypes.KW_FILE) {
                    // Build the full type name (might be qualified like System.Integer)
                    StringBuilder typeName = new StringBuilder(child.getText());
                    ASTNode next = child.getTreeNext();

                    // Handle qualified names (Type.SubType) and generics (TList<T>)
                    while (next != null) {
                        IElementType nextType = next.getElementType();
                        if (nextType == PascalTokenTypes.DOT) {
                            typeName.append(".");
                            next = next.getTreeNext();
                            if (next != null && next.getElementType() == PascalTokenTypes.IDENTIFIER) {
                                typeName.append(next.getText());
                                next = next.getTreeNext();
                            }
                        } else if (nextType == PascalTokenTypes.LT) {
                            // Generic type parameters - include them in type name
                            int depth = 1;
                            typeName.append("<");
                            next = next.getTreeNext();
                            while (next != null && depth > 0) {
                                if (next.getElementType() == PascalTokenTypes.LT) depth++;
                                else if (next.getElementType() == PascalTokenTypes.GT) depth--;
                                typeName.append(next.getText());
                                next = next.getTreeNext();
                            }
                        } else if (nextType == PascalTokenTypes.WHITE_SPACE) {
                            next = next.getTreeNext();
                        } else {
                            break;
                        }
                    }
                    return typeName.toString();
                }
            }
        }
        return null;
    }

    @Override
    @NotNull
    public VariableKind getVariableKind() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getVariableKind();
        }

        PsiElement parent = getParent();
        if (parent == null) return VariableKind.UNKNOWN;

        // Parent should be a Section or a ListNode or FormalParameter
        IElementType parentType = parent.getNode().getElementType();

        // 1. Check if we are inside a TYPE_DEFINITION (class/record field)
        if (isInsideTypeDefinition()) {
            return VariableKind.FIELD;
        }

        // 2. Check if we are a parameter
        if (isInsideFormalParameter()) {
            return VariableKind.PARAMETER;
        }

        // 3. Check containing section
        PsiElement section = parent;
        while (section != null) {
            IElementType type = section.getNode().getElementType();
            if (type == nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION) {
                ASTNode node = section.getNode();
                if (node.findChildByType(nl.akiar.pascal.PascalTokenTypes.KW_CONST) != null) {
                    return VariableKind.CONSTANT;
                }
                if (node.findChildByType(nl.akiar.pascal.PascalTokenTypes.KW_THREADVAR) != null) {
                    return VariableKind.THREADVAR;
                }

                if (isInsideRoutine()) {
                    return VariableKind.LOCAL;
                }
                return VariableKind.GLOBAL;
            }
            section = section.getParent();
        }

        return VariableKind.UNKNOWN;
    }

    private boolean isInsideFormalParameter() {
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent.getNode() != null && parent.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER) {
                return true;
            }
            // Stop at routine or variable section to avoid unnecessary traversal
            IElementType type = parent.getNode().getElementType();
            if (type == nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION ||
                type == nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION ||
                type == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
                break;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isInsideTypeDefinition() {
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent.getNode() != null && parent.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isInsideRoutine() {
        PsiElement parent = getParent();
        while (parent != null) {
            IElementType type = (parent.getNode() != null) ? parent.getNode().getElementType() : null;
            if (type == nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION) {
                return true;
            }
            // If we hit a type definition, we are in a field/method context, not a standalone routine
            if (type == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
                return false;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isInsideRoutineHeader() {
        // Parameters usually have a parenthesis as a child in our current mapping
        return getNode().findChildByType(PascalTokenTypes.LPAREN) != null;
    }

    /**
     * Get the visibility modifier for this variable (private, protected, public, published).
     */
    @Nullable
    public String getVisibility() {
        if (getVariableKind() != VariableKind.FIELD) return null;

        // Find the containing TYPE_DEFINITION
        PsiElement parent = getParent();
        while (parent != null && parent.getNode().getElementType() != nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
            parent = parent.getParent();
        }

        if (parent == null) return null;

        // Traverse children of TYPE_DEFINITION to find the last visibility keyword before this element
        String lastVisibility = "public"; // default
        ASTNode node = parent.getNode();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (isAncestor(child.getPsi(), this)) break;

            IElementType type = child.getElementType();
            if (type == PascalTokenTypes.KW_PRIVATE) {
                // Check if preceded by STRICT
                ASTNode prev = child.getTreePrev();
                while (prev != null && (prev.getElementType() == PascalTokenTypes.WHITE_SPACE)) {
                    prev = prev.getTreePrev();
                }
                if (prev != null && prev.getElementType() == PascalTokenTypes.KW_STRICT) {
                    lastVisibility = "strict private";
                } else {
                    lastVisibility = "private";
                }
            } else if (type == PascalTokenTypes.KW_PROTECTED) {
                // Check if preceded by STRICT
                ASTNode prev = child.getTreePrev();
                while (prev != null && (prev.getElementType() == PascalTokenTypes.WHITE_SPACE)) {
                    prev = prev.getTreePrev();
                }
                if (prev != null && prev.getElementType() == PascalTokenTypes.KW_STRICT) {
                    lastVisibility = "strict protected";
                } else {
                    lastVisibility = "protected";
                }
            } else if (type == PascalTokenTypes.KW_PUBLIC) lastVisibility = "public";
            else if (type == PascalTokenTypes.KW_PUBLISHED) lastVisibility = "published";
            // Ignore KW_STRICT here, we handle it when we see PRIVATE/PROTECTED
        }
        return lastVisibility;
    }

    private boolean isAncestor(PsiElement ancestor, PsiElement element) {
        if (ancestor == element) return true;
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (parent == ancestor) return true;
            parent = parent.getParent();
        }
        return false;
    }

    @Override
    @Nullable
    public String getContainingScopeName() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getContainingScopeName();
        }

        return determineContainingScopeName();
    }

    @Nullable
    private String determineContainingScopeName() {
        VariableKind kind = getVariableKind();
        switch (kind) {
            case FIELD:
                return findContainingClassName();
            case LOCAL:
            case PARAMETER:
                return findContainingRoutineName();
            case GLOBAL:
            case CONSTANT:
            case THREADVAR:
                return findUnitName();
            default:
                return null;
        }
    }

    @Nullable
    private String findContainingClassName() {
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent.getNode() != null && parent.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
                if (parent instanceof nl.akiar.pascal.psi.PascalTypeDefinition) {
                    return ((nl.akiar.pascal.psi.PascalTypeDefinition) parent).getName();
                }
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Nullable
    private String findContainingRoutineName() {
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent.getNode() != null && parent.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION) {
                ASTNode idNode = parent.getNode().findChildByType(PascalTokenTypes.IDENTIFIER);
                if (idNode != null) return idNode.getText();
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Nullable
    private String findUnitName() {
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent.getNode() != null && parent.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION) {
                ASTNode idNode = parent.getNode().findChildByType(PascalTokenTypes.IDENTIFIER);
                if (idNode != null) return idNode.getText();
            }
            parent = parent.getParent();
        }

        // Search for it in children of file as a backup
        PsiElement file = getContainingFile();
        for (PsiElement child : file.getChildren()) {
            if (child.getNode() != null && child.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.UNIT_DECL_SECTION) {
                ASTNode idNode = child.getNode().findChildByType(PascalTokenTypes.IDENTIFIER);
                if (idNode != null) return idNode.getText();
            }
        }

        // Fall back to file name without extension
        String fileName = getContainingFile().getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    @Nullable
    public String getContainingClassName() {
        if (getVariableKind() == VariableKind.FIELD) {
            return findContainingClassName();
        }
        return null;
    }

    @Nullable
    public String getContainingFunctionName() {
        VariableKind kind = getVariableKind();
        if (kind == VariableKind.LOCAL || kind == VariableKind.PARAMETER) {
            return findContainingRoutineName();
        }
        return null;
    }

    @NotNull
    public String getUnitName() {
        String unitName = findUnitName();
        return unitName != null ? unitName : "";
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // The first IDENTIFIER token in the node is the variable name
        ASTNode node = getNode();
        ASTNode identifierNode = node.findChildByType(PascalTokenTypes.IDENTIFIER);
        if (identifierNode != null) {
            return identifierNode.getPsi();
        }
        return null;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Renaming not supported yet");
    }

    @Override
    @Nullable
    public String getDocComment() {
        // Look for comments immediately preceding this variable definition
        StringBuilder docBuilder = new StringBuilder();
        PsiElement prev = getPrevSibling();

        while (prev != null) {
            IElementType type = prev.getNode().getElementType();
            if (type == PascalTokenTypes.WHITE_SPACE) {
                String text = prev.getText();
                long newlines = text.chars().filter(c -> c == '\n').count();
                if (newlines > 1) {
                    break;
                }
                prev = prev.getPrevSibling();
                continue;
            }
            if (type == PascalTokenTypes.BLOCK_COMMENT || type == PascalTokenTypes.LINE_COMMENT) {
                String commentText = extractCommentContent(prev.getText(), type);
                if (docBuilder.length() > 0) {
                    docBuilder.insert(0, "\n");
                }
                docBuilder.insert(0, commentText);
                prev = prev.getPrevSibling();
            } else {
                break;
            }
        }

        String result = docBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String extractCommentContent(String comment, IElementType type) {
        if (type == PascalTokenTypes.LINE_COMMENT) {
            if (comment.startsWith("//")) {
                return comment.substring(2).trim();
            }
        } else if (type == PascalTokenTypes.BLOCK_COMMENT) {
            if (comment.startsWith("{") && comment.endsWith("}")) {
                return comment.substring(1, comment.length() - 1).trim();
            } else if (comment.startsWith("(*") && comment.endsWith("*)")) {
                return comment.substring(2, comment.length() - 2).trim();
            }
        }
        return comment.trim();
    }

    @Override
    @NotNull
    public String getDeclarationText() {
        // Build the declaration text from the node
        ASTNode node = getNode();
        StringBuilder sb = new StringBuilder();
        ASTNode child = node.getFirstChildNode();

        while (child != null) {
            IElementType type = child.getElementType();

            // Stop at semicolon or end
            if (type == PascalTokenTypes.SEMI) {
                break;
            }

            sb.append(child.getText());
            child = child.getTreeNext();
        }

        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return "PascalVariableDefinition(" + getName() + ": " + getTypeName() + ", " + getVariableKind() + ")";
    }
}
