package nl.akiar.pascal;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * File type for Object Pascal files (.pas, .dpr, .dpk)
 */
public class PascalFileType extends LanguageFileType {
    public static final PascalFileType INSTANCE = new PascalFileType();

    private PascalFileType() {
        super(PascalLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Object Pascal";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Object Pascal source file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "pas";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return IconLoader.getIcon("/icons/pascal.svg", PascalFileType.class);
    }
}
