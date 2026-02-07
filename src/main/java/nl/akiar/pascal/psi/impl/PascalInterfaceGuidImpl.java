package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import nl.akiar.pascal.psi.PascalInterfaceGuid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of PascalInterfaceGuid PSI element.
 */
public class PascalInterfaceGuidImpl extends ASTWrapperPsiElement implements PascalInterfaceGuid {
    // Pattern to extract GUID from ['{GUID}'] format
    private static final Pattern GUID_PATTERN = Pattern.compile("\\[\\s*'\\{([0-9A-Fa-f-]+)\\}'\\s*\\]");

    public PascalInterfaceGuidImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @Nullable
    public String getGuidValue() {
        String text = getText();
        Matcher matcher = GUID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    @NotNull
    public String getFullText() {
        return getText();
    }

    @Override
    public String toString() {
        return "PascalInterfaceGuid: " + getText();
    }
}

