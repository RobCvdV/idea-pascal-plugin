package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.TypeKind;
import nl.akiar.pascal.stubs.PascalTypeStub;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import nl.akiar.pascal.reference.PascalTypeReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of PascalTypeDefinition PSI element.
 * Represents type definitions like: TMyClass = class, TMyRecord = record, IMyInterface = interface
 */
public class PascalTypeDefinitionImpl extends StubBasedPsiElementBase<PascalTypeStub> implements PascalTypeDefinition {

    public PascalTypeDefinitionImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PascalTypeDefinitionImpl(@NotNull PascalTypeStub stub, @NotNull IStubElementType<?, ?> nodeType) {
        super(stub, nodeType);
    }

    @Override
    @Nullable
    public String getName() {
        PascalTypeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getName();
        }

        // Parse from AST
        PsiElement nameElement = getNameIdentifier();
        if (nameElement != null) {
            return nameElement.getText();
        }
        return null;
    }

    @Override
    @NotNull
    public TypeKind getTypeKind() {
        PascalTypeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getTypeKind();
        }

        // Parse from AST - look for class, record, or interface keyword
        ASTNode node = getNode();
        
        // With Sonar parser, we should check children.
        // Usually it's: IDENTIFIER = [packed] class/record/interface ...
        
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();
            if (type == PascalTokenTypes.KW_CLASS) {
                return TypeKind.CLASS;
            } else if (type == PascalTokenTypes.KW_RECORD) {
                return TypeKind.RECORD;
            } else if (type == PascalTokenTypes.KW_INTERFACE || type == PascalTokenTypes.KW_DISPINTERFACE) {
                return TypeKind.INTERFACE;
            } else if (type == PascalTokenTypes.KW_REFERENCE || type == PascalTokenTypes.KW_PROCEDURE || type == PascalTokenTypes.KW_FUNCTION) {
                return TypeKind.PROCEDURAL;
            } else if (type == PascalTokenTypes.LPAREN) {
                return TypeKind.ENUM;
            } else if (type == PascalTokenTypes.KW_ARRAY) {
                return TypeKind.ALIAS; // or specific ARRAY kind if we had it
            }
        }
        
        return TypeKind.ALIAS; // Default for type T = ExistingType;
    }

    @Override
    @NotNull
    public List<String> getTypeParameters() {
        PascalTypeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getTypeParameters();
        }

        // With Sonar parser, generic parameters are mapped to GENERIC_PARAMETER element type
        List<String> results = new ArrayList<>();
        ASTNode node = getNode();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER) {
                // Collect all identifiers within the generic parameter node
                // (It might contain multiple names like <T, K>)
                collectIdentifiers(child, results);
            } else if (child.getElementType() == PascalTokenTypes.EQ) {
                // Generic parameters must appear before '='
                break;
            }
        }

        // Legacy fallback
        if (results.isEmpty()) {
            ASTNode nameNode = node.findChildByType(PascalTokenTypes.IDENTIFIER);
            if (nameNode != null) {
                ASTNode current = nameNode.getTreeNext();
                while (current != null && current.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    current = current.getTreeNext();
                }
                if (current != null && current.getElementType() == PascalTokenTypes.LT) {
                    current = current.getTreeNext();
                    while (current != null) {
                        IElementType type = current.getElementType();
                        if (type == PascalTokenTypes.GT || type == PascalTokenTypes.EQ) {
                            break;
                        }
                        if (type == PascalTokenTypes.IDENTIFIER) {
                            results.add(current.getText());
                        }
                        current = current.getTreeNext();
                    }
                }
            }
        }
        return results;
    }

    private void collectIdentifiers(ASTNode node, List<String> results) {
        if (node.getElementType() == PascalTokenTypes.IDENTIFIER) {
            results.add(node.getText());
        }
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            collectIdentifiers(child, results);
        }
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // The name identifier is the first IDENTIFIER token in the node
        ASTNode node = getNode();
        ASTNode identifierNode = node.findChildByType(PascalTokenTypes.IDENTIFIER);
        if (identifierNode != null) {
            return identifierNode.getPsi();
        }
        return null;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        // For now, we don't support renaming
        throw new IncorrectOperationException("Renaming not supported yet");
    }


    @Override
    @Nullable
    public String getDocComment() {
        // Look for comments immediately preceding this type definition
        // Pascal doc comments can be: { comment }, (* comment *), or // comment
        StringBuilder docBuilder = new StringBuilder();
        PsiElement prev = getPrevSibling();

        // Collect consecutive comments (skipping whitespace)
        while (prev != null) {
            IElementType type = prev.getNode().getElementType();
            if (type == PascalTokenTypes.WHITE_SPACE) {
                // Check if it's more than one newline (empty line) - stop collecting
                String text = prev.getText();
                long newlines = text.chars().filter(c -> c == '\n').count();
                if (newlines > 1) {
                    break; // Empty line separates doc comment from type
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
                // Hit something other than a comment or whitespace
                break;
            }
        }

        String result = docBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String extractCommentContent(String comment, IElementType type) {
        if (type == PascalTokenTypes.LINE_COMMENT) {
            // Remove leading //
            if (comment.startsWith("//")) {
                return comment.substring(2).trim();
            }
        } else if (type == PascalTokenTypes.BLOCK_COMMENT) {
            // Remove { } or (* *)
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
    public String getDeclarationHeader() {
        ASTNode node = getNode();
        StringBuilder sb = new StringBuilder();
        ASTNode child = node.getFirstChildNode();
        TypeKind kind = getTypeKind();
        boolean foundKindKeyword = false;

        while (child != null) {
            IElementType type = child.getElementType();

            // Stop at semicolon
            if (type == PascalTokenTypes.SEMI) {
                sb.append(";");
                break;
            }

            // For structured types, stop at keywords that start the body
            if (kind == TypeKind.CLASS || kind == TypeKind.RECORD || kind == TypeKind.INTERFACE) {
                if (foundKindKeyword) {
                    if (isBodyStartKeyword(type)) {
                        break;
                    }
                } else {
                    if (type == PascalTokenTypes.KW_CLASS || type == PascalTokenTypes.KW_RECORD || type == PascalTokenTypes.KW_INTERFACE) {
                        foundKindKeyword = true;
                    }
                }
            }

            sb.append(child.getText());
            child = child.getTreeNext();
        }

        return sb.toString().trim();
    }

    private boolean isBodyStartKeyword(IElementType type) {
        return type == PascalTokenTypes.KW_PRIVATE
                || type == PascalTokenTypes.KW_PROTECTED
                || type == PascalTokenTypes.KW_PUBLIC
                || type == PascalTokenTypes.KW_PUBLISHED
                || type == PascalTokenTypes.KW_STRICT
                || type == PascalTokenTypes.KW_VAR
                || type == PascalTokenTypes.KW_CONST
                || type == PascalTokenTypes.KW_TYPE
                || type == PascalTokenTypes.KW_PROCEDURE
                || type == PascalTokenTypes.KW_FUNCTION
                || type == PascalTokenTypes.KW_CONSTRUCTOR
                || type == PascalTokenTypes.KW_DESTRUCTOR
                || type == PascalTokenTypes.KW_PROPERTY
                || type == PascalTokenTypes.KW_OPERATOR
                || type == PascalTokenTypes.KW_BEGIN;
    }

    @Override
    public String toString() {
        return "PascalTypeDefinition(" + getName() + ", " + getTypeKind() + ")";
    }
}
