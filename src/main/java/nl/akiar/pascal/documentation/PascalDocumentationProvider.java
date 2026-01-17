package nl.akiar.pascal.documentation;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class PascalDocumentationProvider extends AbstractDocumentationProvider {
    private static final Logger LOG = Logger.getInstance(PascalDocumentationProvider.class);

    @Override
    @Nullable
    public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement, int targetOffset) {
        LOG.info("[PascalDoc] getCustomDocumentationElement called for contextElement: " + contextElement + " (text: " + (contextElement != null ? contextElement.getText() : "null") + ")");
        if (contextElement != null && contextElement.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            // Skip if this identifier IS a type definition name (don't show docs for the definition itself)
            PsiElement parent = contextElement.getParent();
            if (parent instanceof PascalTypeDefinition) {
                if (((PascalTypeDefinition) parent).getNameIdentifier() == contextElement) {
                    LOG.info("[PascalDoc]  -> Skipping: this is the definition name itself");
                    return null;
                }
            }

            // Look up the type directly in the index (bypass reference system)
            String typeName = contextElement.getText();
            Collection<PascalTypeDefinition> types = PascalTypeIndex.findTypes(typeName, contextElement.getProject());
            if (!types.isEmpty()) {
                PascalTypeDefinition found = types.iterator().next();
                LOG.info("[PascalDoc]  -> Found type in index: " + found.getName() + " in " + found.getContainingFile().getName());
                return found;
            }
            LOG.info("[PascalDoc]  -> Type not found in index: " + typeName);
        }
        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset);
    }

    @Override
    @Nullable
    public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        LOG.info("[PascalDoc] generateDoc called for element: " + element + " class: " + (element != null ? element.getClass().getName() : "null") + " (original: " + originalElement + ")");
        if (element instanceof PascalTypeDefinition) {
            PascalTypeDefinition typeDef = (PascalTypeDefinition) element;
            LOG.info("[PascalDoc] Generating doc for type definition: " + typeDef.getName());
            StringBuilder sb = new StringBuilder();

            // Type signature
            sb.append("<b>").append(escapeHtml(typeDef.getName())).append("</b>");
            sb.append(" (").append(typeDef.getTypeKind().name().toLowerCase()).append(")");

            // Generic parameters (only if present)
            List<String> typeParams = typeDef.getTypeParameters();
            if (!typeParams.isEmpty()) {
                sb.append("<br/>Generic parameters: &lt;");
                sb.append(escapeHtml(String.join(", ", typeParams)));
                sb.append("&gt;");
            }

            // Unit and file location
            String fileName = element.getContainingFile().getName();
            String unitName = fileName;
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                unitName = fileName.substring(0, dotIndex);
            }
            sb.append("<br/>Unit: <b>").append(escapeHtml(unitName)).append("</b>");
            sb.append("<br/>File: <i>").append(escapeHtml(fileName)).append("</i>");

            // Documentation comment (if present)
            String docComment = typeDef.getDocComment();
            if (docComment != null && !docComment.isEmpty()) {
                sb.append("<hr/>");
                // Convert newlines to <br/> and escape HTML
                String formattedDoc = escapeHtml(docComment).replace("\n", "<br/>");
                sb.append(formattedDoc);
            }

            // Add padding at bottom to prevent edit button overlap
            sb.append("<br/>&nbsp;");

            return sb.toString();
        }
        return null;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    @Override
    @Nullable
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        LOG.info("[PascalDoc] getQuickNavigateInfo called for element: " + element);
        if (element instanceof PascalTypeDefinition) {
            PascalTypeDefinition typeDef = (PascalTypeDefinition) element;
            return typeDef.getTypeKind().name().toLowerCase() + " " + typeDef.getName();
        }
        return null;
    }
}
