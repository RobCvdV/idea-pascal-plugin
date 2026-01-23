package nl.akiar.pascal.documentation;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import nl.akiar.pascal.PascalSyntaxHighlighter;
import nl.akiar.pascal.PascalTokenType;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.TypeKind;
import nl.akiar.pascal.psi.VariableKind;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.stubs.PascalVariableIndex;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.stubs.PascalPropertyIndex;
import nl.akiar.pascal.stubs.PascalRoutineIndex;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;

public class PascalDocumentationProvider extends AbstractDocumentationProvider {
    private static final Logger LOG = Logger.getInstance(PascalDocumentationProvider.class);

    @Override
    @Nullable
    public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement, int targetOffset) {
        // LOG.info("[PascalDoc] getCustomDocumentationElement called for contextElement: " + contextElement + " (text: " + (contextElement != null ? contextElement.getText() : "null") + ")");
        if (contextElement != null && contextElement.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            String name = contextElement.getText();
            
            // 0. Check if the context element itself is a name identifier of a declaration
            PsiElement parent = contextElement.getParent();
            if (parent instanceof nl.akiar.pascal.psi.PascalVariableDefinition ||
                parent instanceof nl.akiar.pascal.psi.PascalTypeDefinition ||
                parent instanceof nl.akiar.pascal.psi.PascalRoutine ||
                parent instanceof nl.akiar.pascal.psi.PascalProperty) {
                
                // If the identifier is the name identifier of its parent, return the parent
                if (((com.intellij.psi.PsiNameIdentifierOwner) parent).getNameIdentifier() == contextElement) {
                    return parent;
                }
            }

            // 1. Check if it resolves via reference (handles local vars, parameters, members)
            PsiReference[] refs = contextElement.getReferences();
            for (PsiReference ref : refs) {
                PsiElement resolved = ref.resolve();
                if (resolved != null) {
                    return resolved;
                }
            }

            // 2. Fallback to index lookup if not resolved via reference
            Collection<PascalTypeDefinition> types = PascalTypeIndex.findTypes(name, contextElement.getProject());
            if (!types.isEmpty()) {
                return types.iterator().next();
            }

            PsiFile currentFile = contextElement.getContainingFile();
            int offset = contextElement.getTextOffset();
            PascalVariableDefinition foundVar = PascalVariableIndex.findVariableAtPosition(name, currentFile, offset);
            if (foundVar != null) {
                return foundVar;
            }

            Collection<PascalProperty> props = PascalPropertyIndex.findProperties(name, contextElement.getProject());
            if (!props.isEmpty()) {
                return props.iterator().next();
            }

            Collection<PascalRoutine> routines = PascalRoutineIndex.findRoutines(name, contextElement.getProject());
            if (!routines.isEmpty()) {
                return routines.iterator().next();
            }
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

            EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
            Color bgColor = scheme.getDefaultBackground();
            Color fgColor = scheme.getDefaultForeground();
            String bgHex = colorToHex(bgColor != null ? bgColor : new Color(0xf7f7f7));
            String fgHex = colorToHex(fgColor != null ? fgColor : Color.BLACK);

            // Type signature (header)
            sb.append("<div style='background-color: ").append(bgHex).append("; color: ").append(fgHex)
              .append("; padding: 5px; border-radius: 3px; border: 1px solid #ddd; font-family: monospace;'>");
            appendHighlightedHeader(sb, typeDef, scheme);
            sb.append("</div>");

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

        // Generate documentation for variable definitions
        if (element instanceof PascalVariableDefinition) {
            PascalVariableDefinition varDef = (PascalVariableDefinition) element;
            LOG.info("[PascalDoc] Generating doc for variable definition: " + varDef.getName());
            return generateVariableDoc(varDef);
        }

        // Generate documentation for properties
        if (element instanceof PascalProperty) {
            PascalProperty prop = (PascalProperty) element;
            LOG.info("[PascalDoc] Generating doc for property: " + prop.getName());
            return generatePropertyDoc(prop);
        }

        // Generate documentation for routines
        if (element instanceof PascalRoutine) {
            PascalRoutine routine = (PascalRoutine) element;
            LOG.info("[PascalDoc] Generating doc for routine: " + routine.getName());
            return generateRoutineDoc(routine);
        }

        return null;
    }

    private String generatePropertyDoc(PascalProperty prop) {
        StringBuilder sb = new StringBuilder();
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        Color bgColor = scheme.getDefaultBackground();
        Color fgColor = scheme.getDefaultForeground();
        String bgHex = colorToHex(bgColor != null ? bgColor : new Color(0xf7f7f7));
        String fgHex = colorToHex(fgColor != null ? fgColor : Color.BLACK);

        sb.append("<div style='background-color: ").append(bgHex).append("; color: ").append(fgHex)
          .append("; padding: 5px; border-radius: 3px; border: 1px solid #ddd; font-family: monospace;'>");
        
        appendStyled(sb, "property ", PascalSyntaxHighlighter.KEYWORD, scheme, true);
        appendStyled(sb, prop.getName(), PascalSyntaxHighlighter.VAR_FIELD, scheme, true);
        
        String typeName = prop.getTypeName();
        if (typeName != null) {
            sb.append(": ");
            appendStyled(sb, typeName, PascalSyntaxHighlighter.IDENTIFIER, scheme, false);
        }
        
        String read = prop.getReadSpecifier();
        if (read != null) {
            sb.append(" ");
            appendStyled(sb, "read ", PascalSyntaxHighlighter.KEYWORD, scheme, false);
            sb.append(escapeHtml(read));
        }
        
        String write = prop.getWriteSpecifier();
        if (write != null) {
            sb.append(" ");
            appendStyled(sb, "write ", PascalSyntaxHighlighter.KEYWORD, scheme, false);
            sb.append(escapeHtml(write));
        }
        
        sb.append(";</div>");

        String visibility = prop.getVisibility();
        if (visibility != null && !visibility.isEmpty()) {
            sb.append("<br/>Visibility: <b>").append(visibility).append("</b>");
        }

        String className = prop.getContainingClassName();
        if (className != null) {
            sb.append("<br/>Class: <b>").append(escapeHtml(className)).append("</b>");
        }
        
        sb.append("<br/>Unit: <b>").append(escapeHtml(prop.getUnitName())).append("</b>");

        String doc = prop.getDocComment();
        if (doc != null && !doc.isEmpty()) {
            sb.append("<hr/>").append(escapeHtml(doc).replace("\n", "<br/>"));
        }

        return sb.toString();
    }

    private String generateRoutineDoc(PascalRoutine routine) {
        StringBuilder sb = new StringBuilder();
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        Color bgColor = scheme.getDefaultBackground();
        Color fgColor = scheme.getDefaultForeground();
        String bgHex = colorToHex(bgColor != null ? bgColor : new Color(0xf7f7f7));
        String fgHex = colorToHex(fgColor != null ? fgColor : Color.BLACK);

        sb.append("<div style='background-color: ").append(bgHex).append("; color: ").append(fgHex)
          .append("; padding: 5px; border-radius: 3px; border: 1px solid #ddd; font-family: monospace;'>");
        
        // Better signature extraction
        String text = routine.getText();
        String signature = text.split("begin|var|const|type|\\{|\\/\\/|\\(\\*", 2)[0].trim();
        sb.append(escapeHtml(signature));
        sb.append("</div>");

        String visibility = routine.getVisibility();
        if (visibility != null && !visibility.isEmpty()) {
            sb.append("<br/>Visibility: <b>").append(visibility).append("</b>");
        }

        PascalTypeDefinition containingClass = routine.getContainingClass();
        if (containingClass != null) {
            sb.append("<br/>Class: <b>").append(escapeHtml(containingClass.getName())).append("</b>");
        }

        String fileName = routine.getContainingFile().getName();
        sb.append("<br/>File: <i>").append(escapeHtml(fileName)).append("</i>");

        String doc = routine.getDocComment();
        if (doc == null) {
            // Check counterpart
            PascalRoutine counterpart = routine.isImplementation() ? routine.getDeclaration() : routine.getImplementation();
            if (counterpart != null) {
                doc = counterpart.getDocComment();
            }
        }

        if (doc != null && !doc.isEmpty()) {
            sb.append("<hr/>").append(escapeHtml(doc).replace("\n", "<br/>"));
        }

        return sb.toString();
    }

    private String generateVariableDoc(PascalVariableDefinition varDef) {
        StringBuilder sb = new StringBuilder();

        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        Color bgColor = scheme.getDefaultBackground();
        Color fgColor = scheme.getDefaultForeground();
        String bgHex = colorToHex(bgColor != null ? bgColor : new Color(0xf7f7f7));
        String fgHex = colorToHex(fgColor != null ? fgColor : Color.BLACK);

        // Variable signature (header)
        sb.append("<div style='background-color: ").append(bgHex).append("; color: ").append(fgHex)
          .append("; padding: 5px; border-radius: 3px; border: 1px solid #ddd; font-family: monospace;'>");
        appendVariableHeader(sb, varDef, scheme);
        sb.append("</div>");

        // Variable kind and visibility
        VariableKind kind = varDef.getVariableKind();
        String kindLabel = getVariableKindLabel(kind);
        String visibility = varDef.getVisibility();
        if (visibility != null && !visibility.isEmpty()) {
            sb.append("<br/>Kind: <b>").append(visibility).append(" ").append(kindLabel.toLowerCase()).append("</b>");
        } else {
            sb.append("<br/>Kind: <b>").append(kindLabel).append("</b>");
        }

        // Type name
        String typeName = varDef.getTypeName();
        if (typeName != null && !typeName.isEmpty()) {
            sb.append("<br/>Type: <code>").append(escapeHtml(typeName)).append("</code>");
        }

        // Owner information based on kind
        switch (kind) {
            case FIELD:
                String className = varDef.getContainingClassName();
                if (className != null && !className.isEmpty()) {
                    sb.append("<br/>Class: <b>").append(escapeHtml(className)).append("</b>");
                }
                break;

            case LOCAL:
            case LOOP_VAR:
                String funcName = varDef.getContainingFunctionName();
                if (funcName != null && !funcName.isEmpty()) {
                    sb.append("<br/>Function: <b>").append(escapeHtml(funcName)).append("</b>");
                }
                break;

            case PARAMETER:
                String paramFunc = varDef.getContainingScopeName();
                if (paramFunc != null && !paramFunc.isEmpty()) {
                    sb.append("<br/>Parameter of: <b>").append(escapeHtml(paramFunc)).append("</b>");
                }
                break;

            default:
                // Global, constant, threadvar - show scope if available
                String scopeName = varDef.getContainingScopeName();
                if (scopeName != null && !scopeName.isEmpty()) {
                    sb.append("<br/>Scope: <i>").append(escapeHtml(scopeName)).append("</i>");
                }
                break;
        }

        // Unit and file location
        String unitName = varDef.getUnitName();
        String fileName = varDef.getContainingFile().getName();
        sb.append("<br/>Unit: <b>").append(escapeHtml(unitName)).append("</b>");
        sb.append("<br/>File: <i>").append(escapeHtml(fileName)).append("</i>");

        // Documentation comment (if present)
        String docComment = varDef.getDocComment();
        if (docComment != null && !docComment.isEmpty()) {
            sb.append("<hr/>");
            String formattedDoc = escapeHtml(docComment).replace("\n", "<br/>");
            sb.append(formattedDoc);
        }

        // Add padding at bottom
        sb.append("<br/>&nbsp;");

        return sb.toString();
    }

    private void appendVariableHeader(StringBuilder sb, PascalVariableDefinition varDef, EditorColorsScheme scheme) {
        VariableKind kind = varDef.getVariableKind();
        TextAttributesKey colorKey = getColorForVariableKind(kind);

        // Variable name
        String name = varDef.getName();
        if (name != null) {
            appendStyled(sb, name, colorKey != null ? colorKey : PascalSyntaxHighlighter.IDENTIFIER, scheme, true);
        }

        // Type annotation
        String typeName = varDef.getTypeName();
        if (typeName != null && !typeName.isEmpty()) {
            sb.append(": ");
            appendStyled(sb, typeName, PascalSyntaxHighlighter.IDENTIFIER, scheme, false);
        }
    }

    private String getVariableKindLabel(VariableKind kind) {
        if (kind == null) return "Variable";
        switch (kind) {
            case GLOBAL:
                return "Global Variable";
            case LOCAL:
                return "Local Variable";
            case PARAMETER:
                return "Parameter";
            case FIELD:
                return "Field";
            case CONSTANT:
                return "Constant";
            case THREADVAR:
                return "Thread Variable";
            case LOOP_VAR:
                return "Loop Variable";
            default:
                return "Variable";
        }
    }

    private TextAttributesKey getColorForVariableKind(VariableKind kind) {
        if (kind == null) return null;
        switch (kind) {
            case GLOBAL:
                return PascalSyntaxHighlighter.VAR_GLOBAL;
            case LOCAL:
                return PascalSyntaxHighlighter.VAR_LOCAL;
            case PARAMETER:
                return PascalSyntaxHighlighter.VAR_PARAMETER;
            case FIELD:
                return PascalSyntaxHighlighter.VAR_FIELD;
            case CONSTANT:
                return PascalSyntaxHighlighter.VAR_CONSTANT;
            case THREADVAR:
                return PascalSyntaxHighlighter.VAR_THREADVAR;
            case LOOP_VAR:
                return PascalSyntaxHighlighter.VAR_LOCAL;
            default:
                return null;
        }
    }

    private void appendHighlightedHeader(StringBuilder sb, PascalTypeDefinition typeDef, EditorColorsScheme scheme) {
        // Re-implementing logic to iterate tokens for better highlighting:
        ASTNode node = typeDef.getNode();
        ASTNode child = node.getFirstChildNode();
        TypeKind kind = typeDef.getTypeKind();
        boolean foundKindKeyword = false;

        while (child != null) {
            IElementType type = child.getElementType();
            String text = child.getText();

            if (type == PascalTokenTypes.SEMI) {
                sb.append(";");
                break;
            }

            if (kind == TypeKind.CLASS || kind == TypeKind.RECORD || kind == TypeKind.INTERFACE) {
                if (foundKindKeyword) {
                    if (isBodyStartKeyword(type)) {
                        break;
                    }
                } else {
                    if (type == PascalTokenTypes.KW_CLASS || type == PascalTokenTypes.KW_RECORD || type == PascalTokenTypes.KW_INTERFACE) {
                        foundKindKeyword = true;
                    }
                }
            }

            if (isKeyword(type)) {
                appendStyled(sb, text, PascalSyntaxHighlighter.KEYWORD, scheme, true);
            } else if (type == PascalTokenTypes.IDENTIFIER) {
                if (text.equals(typeDef.getName())) {
                    TextAttributesKey colorKey = getColorForTypeKind(typeDef.getTypeKind());
                    appendStyled(sb, text, colorKey != null ? colorKey : PascalSyntaxHighlighter.IDENTIFIER, scheme, true);
                } else {
                    // Try to see if it's a known type
                    TextAttributesKey colorKey = getIdentifierColor(text, typeDef);
                    appendStyled(sb, text, colorKey, scheme, false);
                }
            } else if (type == PascalTokenTypes.STRING_LITERAL) {
                appendStyled(sb, text, PascalSyntaxHighlighter.STRING, scheme, false);
            } else {
                sb.append(escapeHtml(text));
            }

            child = child.getTreeNext();
        }
    }

    private TextAttributesKey getIdentifierColor(String text, PascalTypeDefinition context) {
        // Simple heuristic for common types if we don't want to do a full index lookup here
        if (text.equalsIgnoreCase("TObject")) return PascalSyntaxHighlighter.TYPE_CLASS;
        if (text.equalsIgnoreCase("Integer") || text.equalsIgnoreCase("String") || text.equalsIgnoreCase("Boolean")) return PascalSyntaxHighlighter.TYPE_SIMPLE;
        
        return PascalSyntaxHighlighter.IDENTIFIER;
    }

    private void appendStyled(StringBuilder sb, String text, TextAttributesKey key, EditorColorsScheme scheme, boolean bold) {
        TextAttributes attr = scheme.getAttributes(key);
        Color color = attr != null ? attr.getForegroundColor() : scheme.getDefaultForeground();
        if (color == null) color = Color.BLACK;

        sb.append("<span style='color: ").append(colorToHex(color)).append(";");
        if (bold || (attr != null && (attr.getFontType() & Font.BOLD) != 0)) {
            sb.append(" font-weight: bold;");
        }
        sb.append("'>").append(escapeHtml(text)).append("</span>");
    }

    private TextAttributesKey getColorForTypeKind(TypeKind kind) {
        if (kind == null) return null;
        switch (kind) {
            case CLASS:
                return PascalSyntaxHighlighter.TYPE_CLASS;
            case RECORD:
                return PascalSyntaxHighlighter.TYPE_RECORD;
            case INTERFACE:
                return PascalSyntaxHighlighter.TYPE_INTERFACE;
            case PROCEDURAL:
                return PascalSyntaxHighlighter.TYPE_PROCEDURAL;
            case ENUM:
                return PascalSyntaxHighlighter.TYPE_ENUM;
            case ALIAS:
                return PascalSyntaxHighlighter.TYPE_SIMPLE;
            default:
                return null;
        }
    }

    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private boolean isBodyStartKeyword(IElementType type) {
        return type == PascalTokenTypes.KW_PRIVATE
                || type == PascalTokenTypes.KW_PROTECTED
                || type == PascalTokenTypes.KW_PUBLIC
                || type == PascalTokenTypes.KW_PUBLISHED
                || type == PascalTokenTypes.KW_STRICT
                || type == PascalTokenTypes.KW_VAR
                || type == PascalTokenTypes.KW_CONST
                || type == PascalTokenTypes.KW_TYPE
                || type == PascalTokenTypes.KW_PROCEDURE
                || type == PascalTokenTypes.KW_FUNCTION
                || type == PascalTokenTypes.KW_CONSTRUCTOR
                || type == PascalTokenTypes.KW_DESTRUCTOR
                || type == PascalTokenTypes.KW_PROPERTY
                || type == PascalTokenTypes.KW_OPERATOR
                || type == PascalTokenTypes.KW_BEGIN;
    }

    private boolean isKeyword(IElementType type) {
        // Simple check: keywords are PascalTokenType but not IDENTIFIER, and not literals/comments
        if (!(type instanceof PascalTokenType)) return false;
        if (type == PascalTokenTypes.IDENTIFIER) return false;
        if (type == PascalTokenTypes.STRING_LITERAL) return false;
        if (type == PascalTokenTypes.INTEGER_LITERAL) return false;
        if (type == PascalTokenTypes.FLOAT_LITERAL) return false;
        if (type == PascalTokenTypes.HEX_LITERAL) return false;
        if (type == PascalTokenTypes.CHAR_LITERAL) return false;
        if (type == PascalTokenTypes.LINE_COMMENT) return false;
        if (type == PascalTokenTypes.BLOCK_COMMENT) return false;
        if (type == PascalTokenTypes.COMPILER_DIRECTIVE) return false;
        
        // Also exclude symbols
        String name = type.toString();
        if (name.equals("LPAREN") || name.equals("RPAREN") || name.equals("LBRACKET") || name.equals("RBRACKET")
            || name.equals("COMMA") || name.equals("SEMI") || name.equals("DOT") || name.equals("COLON")
            || name.equals("EQ") || name.equals("PLUS") || name.equals("MINUS") || name.equals("MULT") || name.equals("DIVIDE")) {
            return false;
        }

        return true;
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
        if (element instanceof PascalVariableDefinition) {
            PascalVariableDefinition varDef = (PascalVariableDefinition) element;
            String typeName = varDef.getTypeName();
            String kindLabel = getVariableKindLabel(varDef.getVariableKind()).toLowerCase();
            if (typeName != null && !typeName.isEmpty()) {
                return kindLabel + " " + varDef.getName() + ": " + typeName;
            }
            return kindLabel + " " + varDef.getName();
        }
        if (element instanceof PascalProperty) {
            PascalProperty prop = (PascalProperty) element;
            return "property " + prop.getName() + ": " + prop.getTypeName();
        }
        if (element instanceof PascalRoutine) {
            PascalRoutine routine = (PascalRoutine) element;
            return routine.getText().split("begin|var|const|type|\\{|\\/\\/|\\(\\*", 2)[0].trim();
        }
        return null;
    }
}
