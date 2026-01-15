package com.mendrix.dfm;

import com.intellij.lang.ASTNode;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Base PSI element for DFM language
 */
public class DfmPsiElement extends ASTWrapperPsiElement {
    public DfmPsiElement(@NotNull ASTNode node) {
        super(node);
    }
}

