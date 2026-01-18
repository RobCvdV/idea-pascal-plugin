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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * XML-based parser for Delphi Project (.dproj) files.
 */
public class DprojParser {
    private static final Logger LOG = Logger.getInstance(DprojParser.class);

    public static class DprojInfo {
        public final Set<String> references = new LinkedHashSet<>();
        public final Set<String> optsets = new LinkedHashSet<>();
    }

    @NotNull
    public static DprojInfo parse(@NotNull VirtualFile dprojFile) {
        DprojInfo info = new DprojInfo();
        try {
            // Use direct I/O to avoid VFS locks during indexing
            byte[] bytes = Files.readAllBytes(Paths.get(dprojFile.getPath()));
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);

                // 1. DCCReference
                NodeList dccReferences = doc.getElementsByTagName("DCCReference");
                for (int i = 0; i < dccReferences.getLength(); i++) {
                    Element element = (Element) dccReferences.item(i);
                    String include = element.getAttribute("Include");
                    if (include != null && !include.isEmpty()) {
                        info.references.add(include);
                    }
                }

                // 2. Optsets (DependsOn or DependentOn in BuildConfiguration)
                NodeList buildConfigs = doc.getElementsByTagName("BuildConfiguration");
                for (int i = 0; i < buildConfigs.getLength(); i++) {
                    Element config = (Element) buildConfigs.item(i);
                    
                    // Try DependsOn
                    NodeList dependsOnList = config.getElementsByTagName("DependsOn");
                    for (int j = 0; j < dependsOnList.getLength(); j++) {
                        String optset = dependsOnList.item(j).getTextContent();
                        if (optset != null && !optset.isEmpty()) {
                            LOG.info("[DprojParser] Found optset reference (DependsOn): " + optset);
                            info.optsets.add(optset);
                        }
                    }
                    
                    // Try DependentOn
                    NodeList dependentOnList = config.getElementsByTagName("DependentOn");
                    for (int j = 0; j < dependentOnList.getLength(); j++) {
                        String optset = dependentOnList.item(j).getTextContent();
                        if (optset != null && !optset.isEmpty()) {
                            LOG.info("[DprojParser] Found optset reference (DependentOn): " + optset);
                            info.optsets.add(optset);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse .dproj file: " + dprojFile.getPath(), e);
        }
        return info;
    }
}
