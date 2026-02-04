package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.*;
import nl.akiar.pascal.stubs.PascalAttributeStub;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Implementation of PascalAttribute.
 */
public class PascalAttributeImpl extends StubBasedPsiElementBase<PascalAttributeStub> implements PascalAttribute {

    public PascalAttributeImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PascalAttributeImpl(@NotNull PascalAttributeStub stub, @NotNull IStubElementType<?, ?> nodeType) {
        super(stub, nodeType);
    }

    @Override
    @NotNull
    public String getName() {
        PascalAttributeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getName();
        }
        return extractNameFromAST();
    }

    @NotNull
    private String extractNameFromAST() {
        // Find the first identifier after LBRACKET
        ASTNode node = getNode();
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == PascalTokenTypes.IDENTIFIER) {
                return child.getText();
            }
            // For GUID attributes like ['{...}'], the name is the string literal
            if (child.getElementType() == PascalTokenTypes.STRING_LITERAL) {
                return child.getText();
            }
            child = child.getTreeNext();
        }
        return "";
    }

    @Override
    @Nullable
    public String getArguments() {
        PascalAttributeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getArguments();
        }
        return extractArgumentsFromAST();
    }

    @Nullable
    private String extractArgumentsFromAST() {
        // Find content between LPAREN and RPAREN
        ASTNode node = getNode();
        ASTNode lparen = null;
        ASTNode rparen = null;

        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == PascalTokenTypes.LPAREN) {
                lparen = child;
            } else if (child.getElementType() == PascalTokenTypes.RPAREN) {
                rparen = child;
                break;
            }
            child = child.getTreeNext();
        }

        if (lparen != null && rparen != null) {
            int start = lparen.getStartOffset() + 1 - getNode().getStartOffset();
            int end = rparen.getStartOffset() - getNode().getStartOffset();
            String text = getNode().getText();
            if (start >= 0 && end <= text.length() && start < end) {
                return text.substring(start, end).trim();
            }
        }
        return null;
    }

    @Override
    @NotNull
    public String getFullText() {
        return getText();
    }

    @Override
    @Nullable
    public PascalTypeDefinition resolveAttributeClass() {
        if (isGUID()) {
            return null; // GUIDs don't resolve to a class
        }

        String className = getAttributeClassName();
        Collection<PascalTypeDefinition> types = PascalTypeIndex.findTypes(className, getProject());

        // Prefer types from units in scope
        // For now, just return the first match
        return types.isEmpty() ? null : types.iterator().next();
    }

    @Override
    @Nullable
    public PsiElement getTarget() {
        // The target is the next sibling that is a type, routine, property, or field
        PsiElement sibling = getNextSibling();
        while (sibling != null) {
            if (sibling instanceof PascalTypeDefinition ||
                sibling instanceof PascalRoutine ||
                sibling instanceof PascalProperty ||
                sibling instanceof PascalVariableDefinition) {
                return sibling;
            }
            // Skip other attributes and whitespace
            if (!(sibling instanceof PascalAttribute) &&
                sibling.getNode().getElementType() != PascalTokenTypes.WHITE_SPACE) {
                break;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    @Override
    @NotNull
    public AttributeTargetType getTargetType() {
        PascalAttributeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getTargetType();
        }
        return computeTargetType();
    }

    @NotNull
    private AttributeTargetType computeTargetType() {
        PsiElement target = getTarget();
        if (target instanceof PascalTypeDefinition) {
            return AttributeTargetType.TYPE;
        } else if (target instanceof PascalRoutine) {
            return AttributeTargetType.ROUTINE;
        } else if (target instanceof PascalProperty) {
            return AttributeTargetType.PROPERTY;
        } else if (target instanceof PascalVariableDefinition) {
            PascalVariableDefinition var = (PascalVariableDefinition) target;
            if (var.getVariableKind() == VariableKind.FIELD) {
                return AttributeTargetType.FIELD;
            }
        }
        return AttributeTargetType.UNKNOWN;
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // Return the identifier node
        ASTNode node = getNode();
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == PascalTokenTypes.IDENTIFIER ||
                child.getElementType() == PascalTokenTypes.STRING_LITERAL) {
                return child.getPsi();
            }
            child = child.getTreeNext();
        }
        return null;
    }

    @Override
    public PsiElement setName(@NotNull String name) {
        // Renaming attributes is not typically supported
        throw new UnsupportedOperationException("Cannot rename attributes");
    }

    @Override
    public String toString() {
        return "PascalAttribute(" + getName() + ")";
    }
}
