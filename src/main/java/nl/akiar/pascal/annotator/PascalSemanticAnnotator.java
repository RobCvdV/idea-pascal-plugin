package nl.akiar.pascal.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import nl.akiar.pascal.PascalSyntaxHighlighter;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.TypeKind;
import nl.akiar.pascal.psi.VariableKind;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Semantic annotator for Pascal files.
 * Colors type definitions (class, record, interface) with different colors.
 */
public class PascalSemanticAnnotator implements Annotator {
    private static final Set<String> SIMPLE_TYPES = new HashSet<>();
    static {
        SIMPLE_TYPES.add("Integer");
        SIMPLE_TYPES.add("Cardinal");
        SIMPLE_TYPES.add("ShortInt");
        SIMPLE_TYPES.add("SmallInt");
        SIMPLE_TYPES.add("LongInt");
        SIMPLE_TYPES.add("Int64");
        SIMPLE_TYPES.add("Byte");
        SIMPLE_TYPES.add("Word");
        SIMPLE_TYPES.add("LongWord");
        SIMPLE_TYPES.add("UInt64");
        SIMPLE_TYPES.add("Boolean");
        SIMPLE_TYPES.add("ByteBool");
        SIMPLE_TYPES.add("WordBool");
        SIMPLE_TYPES.add("LongBool");
        SIMPLE_TYPES.add("Char");
        SIMPLE_TYPES.add("AnsiChar");
        SIMPLE_TYPES.add("WideChar");
        SIMPLE_TYPES.add("String");
        SIMPLE_TYPES.add("AnsiString");
        SIMPLE_TYPES.add("WideString");
        SIMPLE_TYPES.add("UnicodeString");
        SIMPLE_TYPES.add("ShortString");
        SIMPLE_TYPES.add("Single");
        SIMPLE_TYPES.add("Double");
        SIMPLE_TYPES.add("Extended");
        SIMPLE_TYPES.add("Currency");
        SIMPLE_TYPES.add("Comp");
        SIMPLE_TYPES.add("Real");
        SIMPLE_TYPES.add("Real48");
        SIMPLE_TYPES.add("Pointer");
        SIMPLE_TYPES.add("Variant");
        SIMPLE_TYPES.add("NativeInt");
        SIMPLE_TYPES.add("NativeUInt");
    }

    private static final Set<String> BASE_CLASSES = new HashSet<>();
    static {
        BASE_CLASSES.add("TObject");
        BASE_CLASSES.add("Exception");
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Color generic parameters
        if (element.getNode() != null && element.getNode().getElementType() == PascalElementTypes.GENERIC_PARAMETER) {
            annotateGenericParameter(element, holder);
            return;
        }

        // Color unit references in uses clause
        if (element.getNode() != null && element.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(PascalSyntaxHighlighter.UNIT_REFERENCE)
                    .create();
            return;
        }

        // Color type definition names
        if (element instanceof PascalTypeDefinition) {
            annotateTypeDefinition((PascalTypeDefinition) element, holder);
            // Also process all identifier tokens inside the type definition
            processIdentifiersInNode(element.getNode(), element, holder);
            return;
        }

        // Color variable definitions
        if (element instanceof PascalVariableDefinition) {
            annotateVariableDefinition((PascalVariableDefinition) element, holder);
            return;
        }

        // Color type references (identifiers that match known types)
        if (element.getNode() != null && element.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            annotateTypeReference(element, holder);
        }
    }

    private void processIdentifiersInNode(ASTNode node, PsiElement context, AnnotationHolder holder) {
        if (node == null) return;

        if (node.getElementType() == PascalTokenTypes.IDENTIFIER) {
            PsiElement psi = node.getPsi();
            if (psi != null) {
                annotateTypeReference(psi, holder);
            }
        }

        // Recurse into children
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            processIdentifiersInNode(child, context, holder);
        }
    }

    private void annotateTypeDefinition(PascalTypeDefinition typeDef, AnnotationHolder holder) {
        TextAttributesKey key = getColorForTypeKind(typeDef.getTypeKind());
        PsiElement nameElement = typeDef.getNameIdentifier();

        if (nameElement != null && key != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameElement)
                    .textAttributes(key)
                    .create();
        }
    }

    private void annotateGenericParameter(PsiElement element, AnnotationHolder holder) {
        PsiElement id = element.getFirstChild();
        if (id != null && id.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(id)
                    .textAttributes(PascalSyntaxHighlighter.TYPE_PARAMETER)
                    .create();
        }
    }

    private void annotateTypeReference(PsiElement element, AnnotationHolder holder) {
        // Skip if inside a unit reference in uses clause
        PsiElement parent = element.getParent();
        if (parent != null && parent.getNode() != null && parent.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
            return;
        }

        String text = element.getText();

        // Check for TObject - specifically requested to be class color
        if ("TObject".equalsIgnoreCase(text)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(PascalSyntaxHighlighter.TYPE_CLASS)
                    .create();
            return;
        }

        // Check for simple types
        if (SIMPLE_TYPES.stream().anyMatch(t -> t.equalsIgnoreCase(text))) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(PascalSyntaxHighlighter.TYPE_SIMPLE)
                    .create();
            return;
        }

        // Check if it's a generic parameter of the current class/interface/type first
        PsiElement context = element;
        while (context != null) {
            if (context instanceof PascalTypeDefinition) {
                if (isGenericParameterOf((PascalTypeDefinition) context, text)) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(element)
                            .textAttributes(PascalSyntaxHighlighter.TYPE_PARAMETER)
                            .create();
                    return;
                }
            }
            context = context.getParent();
        }

        // Only check type-like identifiers (start with T or I, or are known patterns)
        // If it's already identified as a generic parameter (above), we don't reach here.
        // But for other types, we still want the T/I prefix check for performance.
        if (!text.startsWith("T") && !text.startsWith("I") && !text.startsWith("E")) {
            return;
        }

        // Skip the name identifier of a type definition (already colored by annotateTypeDefinition)
        if (parent instanceof PascalTypeDefinition) {
            PascalTypeDefinition typeDef = (PascalTypeDefinition) parent;
            if (element.equals(typeDef.getNameIdentifier())) {
                return; // Skip the type's own name
            }
        }

        // Look up this identifier in the type index
        Collection<PascalTypeDefinition> types = PascalTypeIndex.findTypes(text, element.getProject());

        if (!types.isEmpty()) {
            PascalTypeDefinition typeDef = types.iterator().next();
            TextAttributesKey key = getColorForTypeKind(typeDef.getTypeKind());

            if (key != null) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(element)
                        .textAttributes(key)
                        .create();
            }
        }
    }

    private boolean isGenericParameterOf(PascalTypeDefinition typeDef, String name) {
        List<String> params = typeDef.getTypeParameters();
        for (String param : params) {
            if (name.equalsIgnoreCase(param)) {
                return true;
            }
        }
        return false;
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

    private void annotateVariableDefinition(PascalVariableDefinition varDef, AnnotationHolder holder) {
        TextAttributesKey key = getColorForVariableKind(varDef.getVariableKind());
        PsiElement nameElement = varDef.getNameIdentifier();

        if (nameElement != null && key != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameElement)
                    .textAttributes(key)
                    .create();
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
}
