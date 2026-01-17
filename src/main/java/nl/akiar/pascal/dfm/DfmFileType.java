package nl.akiar.pascal.dfm;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * File type definition for Delphi Form (DFM) files
 */
public class DfmFileType extends LanguageFileType {
    private static final Logger LOG = Logger.getInstance("#nl.akiar.pascal.dfm.DfmFileType");
    public static final DfmFileType INSTANCE = new DfmFileType();

    static {
        LOG.info("DFM-PLUGIN: DfmFileType class loaded");
    }

    private DfmFileType() {
        super(DfmLanguage.INSTANCE);
        LOG.info("DFM-PLUGIN: DfmFileType instance created");
    }

    @NotNull
    @Override
    public String getName() {
        return "Delphi Form";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Delphi Form File";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "dfm";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return IconLoader.getIcon("/icons/dfm.svg", DfmFileType.class);
    }
}

