package com.mendrix.pascal.dpr;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Delphi Project (.dpr) files.
 * Extracts file paths from 'uses' clauses.
 *
 * Example .dpr content:
 * <pre>
 * program MyProject;
 *
 * uses
 *   Forms,
 *   Unit1 in 'Unit1.pas' {Form1},
 *   Unit2 in '..\Common\Unit2.pas',
 *   SharedTypes in '..\..\Shared\SharedTypes.pas';
 *
 * begin
 *   Application.Run;
 * end.
 * </pre>
 */
public class DprParser {
    private static final Logger LOG = Logger.getInstance(DprParser.class);

    // Pattern to match: UnitName in 'path/to/file.pas'
    // Captures the path in group 1
    private static final Pattern UNIT_IN_PATTERN = Pattern.compile(
            "\\b(\\w+)\\s+in\\s+'([^']+)'",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Parse a .dpr file and extract all referenced file paths.
     *
     * @param dprFile The .dpr file to parse
     * @return List of absolute file paths referenced in the .dpr file
     */
    @NotNull
    public static List<String> parseReferencedFiles(@NotNull VirtualFile dprFile) {
        List<String> result = new ArrayList<>();

        try {
            String content = new String(dprFile.contentsToByteArray(), StandardCharsets.UTF_8);
            Path dprDir = Paths.get(dprFile.getParent().getPath());

            result.addAll(parseReferencedFiles(content, dprDir));
        } catch (IOException e) {
            LOG.warn("Failed to read .dpr file: " + dprFile.getPath(), e);
        }

        return result;
    }

    /**
     * Parse .dpr content and extract file paths.
     * This method is useful for testing without needing actual files.
     *
     * @param content The content of the .dpr file
     * @param dprDirectory The directory containing the .dpr file (for resolving relative paths)
     * @return List of absolute file paths
     */
    @NotNull
    public static List<String> parseReferencedFiles(@NotNull String content, @NotNull Path dprDirectory) {
        List<String> result = new ArrayList<>();

        // Find all "unit in 'path'" patterns
        Matcher matcher = UNIT_IN_PATTERN.matcher(content);

        while (matcher.find()) {
            String unitName = matcher.group(1);
            String relativePath = matcher.group(2);

            // Normalize path separators (Windows uses \, Unix uses /)
            relativePath = relativePath.replace('\\', '/');

            // Resolve relative path against .dpr directory
            Path absolutePath = dprDirectory.resolve(relativePath).normalize();
            String absolutePathStr = absolutePath.toString();

            LOG.info("[DprParser] Found unit: " + unitName + " -> " + absolutePathStr);
            result.add(absolutePathStr);
        }

        return result;
    }

    /**
     * Check if a file is a Delphi project file.
     */
    public static boolean isDprFile(@Nullable VirtualFile file) {
        return file != null && "dpr".equalsIgnoreCase(file.getExtension());
    }
}
