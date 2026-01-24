package nl.akiar.pascal.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import nl.akiar.pascal.PascalFileType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-based index for Pascal unit names.
 * Maps unit names (lowercase) to Void (just indexing the existence and name).
 */
public class PascalUnitIndex extends ScalarIndexExtension<String> {
    public static final ID<String, Void> INDEX_ID = ID.create("nl.akiar.pascal.unit.index");

    private static final Pattern UNIT_NAME_PATTERN = Pattern.compile("^\\s*unit\\s+([^;]+);", Pattern.CASE_INSENSITIVE);

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return INDEX_ID;
    }

    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return inputData -> {
            CharSequence content = inputData.getContentAsText();
            // We only need to look at the beginning of the file to find 'unit Name;'
            // but we should be careful not to read too little if there are many comments.
            // 4096 bytes should be plenty for the header.
            String firstPart = content.subSequence(0, Math.min(content.length(), 4096)).toString();
            String[] lines = firstPart.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("{") || line.startsWith("(*")) {
                    continue;
                }
                Matcher matcher = UNIT_NAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    String unitName = nl.akiar.pascal.psi.PsiUtil.normalizeUnitName(matcher.group(1));
                    return Collections.singletonMap(unitName, null);
                }
                // Stop if we hit implementation, interface, or program keyword
                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith("interface") || lowerLine.startsWith("implementation") || lowerLine.startsWith("program") || lowerLine.startsWith("library")) {
                    break;
                }
            }
            
            // Fallback: use filename if no unit name found
            String fileName = inputData.getFileName();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                String name = fileName.substring(0, dotIndex).toLowerCase();
                if (!name.isEmpty()) {
                    return Collections.singletonMap(name, null);
                }
            }
            return Collections.emptyMap();
        };
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return file -> file.getFileType() == PascalFileType.INSTANCE;
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
