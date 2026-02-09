package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalRoutineSignature;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of PascalRoutineSignature PSI element.
 */
public class PascalRoutineSignatureImpl extends ASTWrapperPsiElement implements PascalRoutineSignature {

    public PascalRoutineSignatureImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public List<PascalVariableDefinition> getParameters() {
        // Find all VARIABLE_DEFINITION children that are parameters
        List<PascalVariableDefinition> params = new ArrayList<>();
        for (PsiElement child : getChildren()) {
            if (child instanceof PascalVariableDefinition) {
                PascalVariableDefinition varDef = (PascalVariableDefinition) child;
                if (varDef.getVariableKind() == nl.akiar.pascal.psi.VariableKind.PARAMETER) {
                    params.add(varDef);
                }
            }
        }
        return params;
    }

    @Override
    @Nullable
    public PsiElement getReturnType() {
        // Look for TYPE_REFERENCE after RPAREN and COLON
        boolean foundRParen = false;
        boolean foundColon = false;

        for (PsiElement child : getChildren()) {
            if (child.getNode().getElementType() == PascalTokenTypes.RPAREN) {
                foundRParen = true;
            } else if (foundRParen && child.getNode().getElementType() == PascalTokenTypes.COLON) {
                foundColon = true;
            } else if (foundColon && child.getNode().getElementType() == PascalElementTypes.TYPE_REFERENCE) {
                return child;
            }
        }

        return null;
    }

    @Override
    public boolean isFunction() {
        return getReturnType() != null;
    }

    @Override
    @Nullable
    public String getReturnTypeName() {
        PsiElement returnType = getReturnType();
        if (returnType != null) {
            return returnType.getText().trim();
        }
        return null;
    }

    @Override
    @NotNull
    public String getSignatureText() {
        return getText().trim();
    }

    @Override
    public int getParameterCount() {
        return getParameters().size();
    }

    @Override
    public String toString() {
        return "PascalRoutineSignature(" + getSignatureText() + ")";
    }
}

