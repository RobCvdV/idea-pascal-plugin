package com.mendrix.dfm;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * PSI File for DFM files
 */
public class DfmFile extends PsiFileBase {
    public DfmFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, DfmLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return DfmFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Delphi Form File";
    }
}

