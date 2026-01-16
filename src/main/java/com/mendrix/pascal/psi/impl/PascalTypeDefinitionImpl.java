package com.mendrix.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import com.mendrix.pascal.PascalTokenTypes;
import com.mendrix.pascal.psi.PascalElementTypes;
import com.mendrix.pascal.psi.PascalTypeDefinition;
import com.mendrix.pascal.psi.TypeKind;
import com.mendrix.pascal.stubs.PascalTypeStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalTypeDefinition PSI element.
 * Represents type definitions like: TMyClass = class, TMyRecord = record, IMyInterface = interface
 */
public class PascalTypeDefinitionImpl extends StubBasedPsiElementBase<PascalTypeStub> implements PascalTypeDefinition {
    private static final Logger LOG = Logger.getInstance(PascalTypeDefinitionImpl.class);

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
                if (child.getElementType() == PascalTokenTypes.KW_CLASS) {
                    return TypeKind.CLASS;
                } else if (child.getElementType() == PascalTokenTypes.KW_RECORD) {
                    return TypeKind.RECORD;
                } else if (child.getElementType() == PascalTokenTypes.KW_INTERFACE) {
                    return TypeKind.INTERFACE;
                }
            }
            child = child.getTreeNext();
        }
        return TypeKind.UNKNOWN;
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
