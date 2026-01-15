package com.mendrix.pascal;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * Base PSI element for Pascal language
 */
public class PascalPsiElement extends ASTWrapperPsiElement {
    public PascalPsiElement(@NotNull ASTNode node) {
        super(node);
    }
}
