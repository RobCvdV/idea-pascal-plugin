package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalTypeDefinition;

import java.util.ArrayList;
import java.util.List;
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
        return nameId != null ? nl.akiar.pascal.psi.PsiUtil.stripEscapePrefix(nameId.getText()) : null;
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
            if (next != null) {
                // Handle TYPE_REFERENCE PSI elements created by parser
                if (next.getElementType() == PascalElementTypes.TYPE_REFERENCE) {
                    PsiElement typeRefElement = next.getPsi();
                    if (typeRefElement instanceof nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) {
                        return ((nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) typeRefElement).getReferencedTypeName();
                    }
                }
                // Fallback to IDENTIFIER for keyword types that don't get TYPE_REFERENCE
                if (next.getElementType() == PascalTokenTypes.IDENTIFIER) {
                    return next.getText();
                }
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

        // Determine which keyword token to look for
        IElementType keywordType = null;
        if ("read".equalsIgnoreCase(specifier)) {
            keywordType = PascalTokenTypes.KW_READ;
        } else if ("write".equalsIgnoreCase(specifier)) {
            keywordType = PascalTokenTypes.KW_WRITE;
        } else if ("stored".equalsIgnoreCase(specifier)) {
            keywordType = PascalTokenTypes.KW_STORED;
        } else if ("default".equalsIgnoreCase(specifier)) {
            keywordType = PascalTokenTypes.KW_DEFAULT;
        }

        while (child != null) {
            // Check for keyword token (read, write, stored, default)
            boolean isSpecifierKeyword = (keywordType != null && child.getElementType() == keywordType);
            // Also check for identifier (fallback for older parsing)
            boolean isSpecifierIdentifier = (child.getElementType() == PascalTokenTypes.IDENTIFIER &&
                                             child.getText().equalsIgnoreCase(specifier));

            if (isSpecifierKeyword || isSpecifierIdentifier) {
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
}
