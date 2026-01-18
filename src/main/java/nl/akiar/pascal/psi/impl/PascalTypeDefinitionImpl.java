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
        com.intellij.openapi.diagnostic.Logger.getInstance(PascalTypeDefinitionImpl.class).info("[PascalPSI] Created PascalTypeDefinitionImpl for: " + node.getElementType());
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

        // Parse from AST - look for class, record, or interface keyword after =
        ASTNode node = getNode();
        ASTNode child = node.getFirstChildNode();
        boolean foundEquals = false;

        while (child != null) {
            if (child.getElementType() == PascalTokenTypes.EQ) {
                foundEquals = true;
            } else if (foundEquals) {
                IElementType type = child.getElementType();
                if (type == PascalTokenTypes.WHITE_SPACE || type == PascalTokenTypes.LINE_COMMENT || type == PascalTokenTypes.BLOCK_COMMENT) {
                    child = child.getTreeNext();
                    continue;
                }
                if (type == PascalTokenTypes.KW_TYPE || type == PascalTokenTypes.KW_PACKED || type == PascalTokenTypes.COMPILER_DIRECTIVE) {
                    // Skip 'type' modifier, 'packed' keyword, and compiler directives
                    child = child.getTreeNext();
                    continue;
                }
                if (type == PascalTokenTypes.KW_CLASS) {
                    return TypeKind.CLASS;
                } else if (type == PascalTokenTypes.KW_RECORD) {
                    return TypeKind.RECORD;
                } else if (type == PascalTokenTypes.KW_INTERFACE) {
                    return TypeKind.INTERFACE;
                } else if (type == PascalTokenTypes.KW_REFERENCE || type == PascalTokenTypes.KW_PROCEDURE || type == PascalTokenTypes.KW_FUNCTION) {
                    return TypeKind.PROCEDURAL;
                } else {
                    // If we found something else after equals, it's not one of our main kinds
                    // but we should keep looking just in case (though unlikely in valid Pascal)
                }
            }
            child = child.getTreeNext();
        }
        return TypeKind.UNKNOWN;
    }

    @Override
    @NotNull
    public List<String> getTypeParameters() {
        PascalTypeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getTypeParameters();
        }

        // Parse from AST: TMyClass<T, K> = class
        // Generic parameters MUST appear immediately after the type name, before '='
        List<String> results = new ArrayList<>();
        ASTNode node = getNode();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER) {
                ASTNode identifierNode = child.findChildByType(PascalTokenTypes.IDENTIFIER);
                if (identifierNode != null) {
                    results.add(identifierNode.getText());
                }
            } else if (child.getElementType() == PascalTokenTypes.EQ) {
                // Generic parameters must appear before '='
                break;
            }
        }

        // Fallback: look for <...> ONLY between the type name and '='
        if (results.isEmpty()) {
            ASTNode nameNode = node.findChildByType(PascalTokenTypes.IDENTIFIER);
            if (nameNode != null) {
                ASTNode current = nameNode.getTreeNext();
                // Look for '<' immediately after the name (skipping whitespace)
                while (current != null && current.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    current = current.getTreeNext();
                }
                if (current != null && current.getElementType() == PascalTokenTypes.LT) {
                    // Found '<' right after name - collect identifiers until '>' or '='
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
    public PsiReference getReference() {
        // A type definition itself doesn't have a reference unless we consider it referencing its own name
        // but usually we return the reference from the identifier.
        // However, some IDE features look at the element itself.
        return null;
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
    public String toString() {
        return "PascalTypeDefinition(" + getName() + ", " + getTypeKind() + ")";
    }
}
