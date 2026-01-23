package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.stubs.PascalPropertyStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PascalPropertyImpl extends StubBasedPsiElementBase<PascalPropertyStub> implements PascalProperty {

    public PascalPropertyImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PascalPropertyImpl(@NotNull PascalPropertyStub stub, @NotNull IStubElementType<?, ?> nodeType) {
        super(stub, nodeType);
    }

    @Override
    @Nullable
    public String getName() {
        PascalPropertyStub stub = getGreenStub();
        if (stub != null) return stub.getName();
        PsiElement nameId = getNameIdentifier();
        return nameId != null ? nameId.getText() : null;
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        ASTNode identifierNode = nl.akiar.pascal.psi.PsiUtil.findFirstRecursive(getNode(), nl.akiar.pascal.PascalTokenTypes.IDENTIFIER);
        return identifierNode != null ? identifierNode.getPsi() : null;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Renaming not supported yet");
    }

    @Override
    @Nullable
    public String getTypeName() {
        PascalPropertyStub stub = getGreenStub();
        if (stub != null) return stub.getTypeName();

        ASTNode node = getNode();
        ASTNode colon = node.findChildByType(PascalTokenTypes.COLON);
        if (colon != null) {
            ASTNode next = colon.getTreeNext();
            while (next != null && (next.getElementType() == PascalTokenTypes.WHITE_SPACE)) {
                next = next.getTreeNext();
            }
            if (next != null && next.getElementType() == PascalTokenTypes.IDENTIFIER) {
                return next.getText();
            }
        }
        return null;
    }

    @Override
    @Nullable
    public String getContainingClassName() {
        PascalPropertyStub stub = getGreenStub();
        if (stub != null) return stub.getContainingClassName();

        PascalTypeDefinition containingClass = PsiTreeUtil.getParentOfType(this, PascalTypeDefinition.class);
        return containingClass != null ? containingClass.getName() : null;
    }

    @Override
    @Nullable
    public String getReadSpecifier() {
        return findSpecifierValue("read");
    }

    @Override
    @Nullable
    public String getWriteSpecifier() {
        return findSpecifierValue("write");
    }

    private String findSpecifierValue(String specifier) {
        ASTNode node = getNode();
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == PascalTokenTypes.IDENTIFIER && child.getText().equalsIgnoreCase(specifier)) {
                ASTNode value = child.getTreeNext();
                while (value != null && value.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    value = value.getTreeNext();
                }
                if (value != null && value.getElementType() == PascalTokenTypes.IDENTIFIER) {
                    return value.getText();
                }
            }
            child = child.getTreeNext();
        }
        return null;
    }

    @Override
    @NotNull
    public String getUnitName() {
        String fileName = getContainingFile().getName();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    @Override
    @Nullable
    public String getDocComment() {
        // Simple implementation for now
        return null;
    }

    @Override
    @Nullable
    public PascalTypeDefinition getContainingClass() {
        return PsiTreeUtil.getParentOfType(this, PascalTypeDefinition.class);
    }

    @Override
    @Nullable
    public String getVisibility() {
        return nl.akiar.pascal.psi.PsiUtil.getVisibility(this);
    }

    @Override
    public String toString() {
        return "PascalProperty(" + getName() + ")";
    }
}
