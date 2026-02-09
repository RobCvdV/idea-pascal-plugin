package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;

import java.util.ArrayList;
import java.util.List;
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

        // 1. Try to parse from AST children (standard case: VarName: Type)
        String typeNameFromChildren = parseTypeNameFromNode(getNode());
        if (typeNameFromChildren != null) {
            return typeNameFromChildren;
        }

        // 2. If no type found in children, check if we are in a combined declaration (A, B: Type)
        // Look at following siblings within the same parent
        PsiElement parent = getParent();
        if (parent != null) {
            // For parameters, parent is usually FORMAL_PARAMETER
            // For variables, parent is usually NameDeclarationList or VarDeclaration
            PsiElement current = getNextSibling();
            while (current != null) {
                if (current instanceof PascalVariableDefinition) {
                    // Another variable definition in the list, skip
                } else if (current.getNode().getElementType() == PascalTokenTypes.COLON) {
                    // Found the colon, type should follow
                    return parseTypeNameFromSiblings(current);
                }
                current = current.getNextSibling();
            }
        }

        return null;
    }

    @Nullable
    private String parseTypeNameFromNode(ASTNode node) {
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

                // Found the type
                return buildTypeName(child);
            }
        }
        return null;
    }

    @Nullable
    private String parseTypeNameFromSiblings(PsiElement colonElement) {
        PsiElement current = colonElement.getNextSibling();
        while (current != null) {
            IElementType type = current.getNode().getElementType();
            if (type == PascalTokenTypes.WHITE_SPACE ||
                type == PascalTokenTypes.LINE_COMMENT ||
                type == PascalTokenTypes.BLOCK_COMMENT) {
                current = current.getNextSibling();
                continue;
            }
            return buildTypeName(current.getNode());
        }
        return null;
    }

    @Nullable
    private String buildTypeName(ASTNode child) {
        IElementType type = child.getElementType();

        // Handle TYPE_REFERENCE PSI elements created by parser
        if (type == PascalElementTypes.TYPE_REFERENCE) {
            PsiElement typeRefElement = child.getPsi();
            if (typeRefElement instanceof nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) {
                return ((nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) typeRefElement).getReferencedTypeName();
            }
        }

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
        return null;
    }

    @Override
    @NotNull
    public VariableKind getVariableKind() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getVariableKind();
        }

        // 1. Check if we are a parameter - Check this FIRST because parameters of methods are also inside TYPE_DEFINITION
        if (isInsideFormalParameter()) {
            return VariableKind.PARAMETER;
        }

        // 2. Check if we are inside a TYPE_DEFINITION (class/record field)
        if (isInsideTypeDefinition()) {
            return VariableKind.FIELD;
        }

        // 3. Check for constant
        if (isInsideConstDeclaration()) {
            return VariableKind.CONSTANT;
        }

        // 4. Check containing section
        PsiElement parent = getParent();
        if (parent == null) return VariableKind.UNKNOWN;

        PsiElement section = parent;
        while (section != null) {
            ASTNode sectionNode = section.getNode();
            if (sectionNode != null) {
                IElementType type = sectionNode.getElementType();
                if (type == nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION) {
                    if (sectionNode.findChildByType(nl.akiar.pascal.PascalTokenTypes.KW_THREADVAR) != null) {
                        return VariableKind.THREADVAR;
                    }

                    if (isInsideRoutine()) {
                        return VariableKind.LOCAL;
                    }
                    return VariableKind.GLOBAL;
                }
            }
            section = section.getParent();
        }

        return VariableKind.UNKNOWN;
    }

    private boolean isInsideFormalParameter() {
        PsiElement parent = getParent();
        while (parent != null) {
            ASTNode parentNode = parent.getNode();
            if (parentNode != null && parentNode.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER) {
                return true;
            }
            // Stop at routine or variable section to avoid unnecessary traversal
            if (parentNode != null) {
                IElementType type = parentNode.getElementType();
                if (type == nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION ||
                        type == nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION ||
                        type == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
                    break;
                }
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

    private boolean isInsideConstDeclaration() {
        PsiElement parent = getParent();
        while (parent != null) {
            ASTNode node = parent.getNode();
            if (node != null) {
                IElementType type = node.getElementType();
                if (type == nl.akiar.pascal.psi.PascalElementTypes.CONST_SECTION) {
                    return true;
                }
                if (type == nl.akiar.pascal.psi.PascalElementTypes.VARIABLE_SECTION) {
                    if (node.findChildByType(nl.akiar.pascal.PascalTokenTypes.KW_CONST) != null) {
                        return true;
                    }
                }
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

    @Nullable
    public String getVisibility() {
        if (getVariableKind() != VariableKind.FIELD) return null;
        return nl.akiar.pascal.psi.PsiUtil.getVisibility(this);
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
                return getUnitName();
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
        return nl.akiar.pascal.psi.PsiUtil.getUnitName(this);
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // Use recursive search because Sonar nests identifiers under IdentifierNodeImpl
        // Also accept keywords that can be used as identifiers (like "Index", "Name", "Read", etc.)
        ASTNode identifierNode = nl.akiar.pascal.psi.PsiUtil.findFirstRecursiveAnyOf(
            getNode(),
            nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES
        );
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
    @Nullable
    public PascalTypeDefinition getContainingClass() {
        if (getVariableKind() != VariableKind.FIELD) return null;
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent instanceof PascalTypeDefinition) {
                return (PascalTypeDefinition) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Override
    public String toString() {
        return "PascalVariableDefinition(" + getName() + ": " + getTypeName() + ", " + getVariableKind() + ")";
    }

    @Override
    @NotNull
    public List<PascalAttribute> getAttributes() {
        List<PascalAttribute> attributes = new ArrayList<>();
        PsiElement prev = getPrevSibling();
        while (prev != null) {
            if (prev instanceof PascalAttribute) {
                // Insert at beginning to maintain source order
                attributes.add(0, (PascalAttribute) prev);
            } else if (prev.getNode().getElementType() != PascalTokenTypes.WHITE_SPACE
                    && !(prev.getNode().getElementType() == PascalElementTypes.ATTRIBUTE_LIST)) {
                // Stop at non-whitespace, non-attribute elements
                break;
            }
            prev = prev.getPrevSibling();
        }
        return attributes;
    }

    @Override
    @Nullable
    public PascalAttribute findAttribute(@NotNull String name) {
        for (PascalAttribute attr : getAttributes()) {
            if (name.equalsIgnoreCase(attr.getName())) {
                return attr;
            }
        }
        return null;
    }

    @Override
    @NotNull
    public nl.akiar.pascal.psi.ParameterModifier getParameterModifier() {
        // Only parameters can have modifiers
        if (getVariableKind() != VariableKind.PARAMETER) {
            return nl.akiar.pascal.psi.ParameterModifier.VALUE;
        }

        // Scan backwards from this element to find modifier keyword
        // Pattern: [var|const|out|in] ParameterName: Type
        PsiElement sibling = getPrevSibling();
        while (sibling != null) {
            IElementType elementType = sibling.getNode().getElementType();

            // Check for modifier keywords
            if (elementType == PascalTokenTypes.KW_VAR) {
                return nl.akiar.pascal.psi.ParameterModifier.VAR;
            } else if (elementType == PascalTokenTypes.KW_CONST) {
                return nl.akiar.pascal.psi.ParameterModifier.CONST;
            } else if (elementType == PascalTokenTypes.KW_OUT) {
                return nl.akiar.pascal.psi.ParameterModifier.OUT;
            } else if (elementType == PascalTokenTypes.KW_IN) {
                return nl.akiar.pascal.psi.ParameterModifier.IN;
            }

            // Stop at semicolon, comma, or lparen (reached boundary)
            if (elementType == PascalTokenTypes.SEMI ||
                elementType == PascalTokenTypes.COMMA ||
                elementType == PascalTokenTypes.LPAREN) {
                break;
            }

            // Skip whitespace and continue
            if (elementType != PascalTokenTypes.WHITE_SPACE) {
                // Hit another token that's not a modifier keyword or boundary
                // Check if it's another variable definition (multiple params: A, B, C: Type)
                if (sibling instanceof PascalVariableDefinition) {
                    // Continue looking backwards
                    sibling = sibling.getPrevSibling();
                    continue;
                }
            }

            sibling = sibling.getPrevSibling();
        }

        // No modifier found - default to VALUE (pass by value)
        return nl.akiar.pascal.psi.ParameterModifier.VALUE;
    }

    @Override
    public boolean isVarParameter() {
        return getParameterModifier() == nl.akiar.pascal.psi.ParameterModifier.VAR;
    }

    @Override
    public boolean isConstParameter() {
        return getParameterModifier() == nl.akiar.pascal.psi.ParameterModifier.CONST;
    }

    @Override
    public boolean isOutParameter() {
        return getParameterModifier() == nl.akiar.pascal.psi.ParameterModifier.OUT;
    }

    @Override
    public boolean isValueParameter() {
        return getParameterModifier() == nl.akiar.pascal.psi.ParameterModifier.VALUE;
    }
}
