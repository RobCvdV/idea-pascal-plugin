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

        // Fallback for older parser or non-structured parts
        if (results.isEmpty()) {
            ASTNode langle = node.findChildByType(PascalTokenTypes.LT);
            if (langle != null) {
                ASTNode rangle = node.findChildByType(PascalTokenTypes.GT);
                if (rangle != null) {
                    ASTNode child = langle.getTreeNext();
                    while (child != null && child != rangle) {
                        if (child.getElementType() == PascalTokenTypes.IDENTIFIER) {
                            results.add(child.getText());
                        }
                        child = child.getTreeNext();
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
    public String toString() {
        return "PascalTypeDefinition(" + getName() + ", " + getTypeKind() + ")";
    }
}
