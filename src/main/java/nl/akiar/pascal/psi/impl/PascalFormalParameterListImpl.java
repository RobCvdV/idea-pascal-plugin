package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalFormalParameterList;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of PascalFormalParameterList PSI element.
 */
public class PascalFormalParameterListImpl extends ASTWrapperPsiElement implements PascalFormalParameterList {

    public PascalFormalParameterListImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public List<PascalVariableDefinition> getParameters() {
        List<PascalVariableDefinition> params = new ArrayList<>();

        // Find all VARIABLE_DEFINITION descendants that are parameters
        for (PascalVariableDefinition varDef : PsiTreeUtil.findChildrenOfType(this, PascalVariableDefinition.class)) {
            if (varDef.getVariableKind() == VariableKind.PARAMETER) {
                params.add(varDef);
            }
        }

        return params;
    }

    @Override
    @Nullable
    public PsiElement getOpenParen() {
        // Iterate over AST node children to find tokens (getChildren() only returns composites)
        ASTNode node = getNode().getFirstChildNode();
        while (node != null) {
            if (node.getElementType() == PascalTokenTypes.LPAREN) {
                return node.getPsi();
            }
            node = node.getTreeNext();
        }
        return null;
    }

    @Override
    @Nullable
    public PsiElement getCloseParen() {
        // Find the last RPAREN token
        PsiElement lastParen = null;
        ASTNode node = getNode().getFirstChildNode();
        while (node != null) {
            if (node.getElementType() == PascalTokenTypes.RPAREN) {
                lastParen = node.getPsi();
            }
            node = node.getTreeNext();
        }
        return lastParen;
    }

    @Override
    public boolean isEmpty() {
        return getParameters().isEmpty();
    }

    @Override
    public int getParameterCount() {
        return getParameters().size();
    }

    @Override
    @NotNull
    public String getParameterListText() {
        return getText().trim();
    }

    @Override
    public String toString() {
        return "PascalFormalParameterList(" + getParameterCount() + " params)";
    }
}
