package nl.akiar.pascal.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * XML-based parser for Delphi Option Set (.optset) files.
 */
public class OptsetParser {
    private static final Logger LOG = Logger.getInstance(OptsetParser.class);

    @NotNull
    public static List<String> parseSearchPaths(@NotNull VirtualFile optsetFile) {
        List<String> searchPaths = new ArrayList<>();
        try {
            // Use direct I/O to avoid VFS locks during indexing
            byte[] bytes = Files.readAllBytes(Paths.get(optsetFile.getPath()));
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);

                NodeList properties = doc.getElementsByTagName("DCC_UnitSearchPath");
                for (int i = 0; i < properties.getLength(); i++) {
                    String value = properties.item(i).getTextContent();
                    if (value != null && !value.isEmpty()) {
                        // Search paths are usually semicolon separated
                        String[] paths = value.split(";");
                        for (String p : paths) {
                            String trimmed = p.trim();
                            if (!trimmed.isEmpty() && !trimmed.startsWith("$(")) {
                                searchPaths.add(trimmed);
                            } else if (trimmed.startsWith("$(PROJECTDIR)")) {
                                // Keep $(PROJECTDIR) as it is handled by PascalProjectService
                                searchPaths.add(trimmed);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse .optset file: " + optsetFile.getPath(), e);
        }
        return searchPaths;
    }
}
