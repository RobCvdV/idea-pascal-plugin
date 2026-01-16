package com.mendrix.pascal.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.mendrix.pascal.PascalSyntaxHighlighter;
import com.mendrix.pascal.PascalTokenTypes;
import com.mendrix.pascal.psi.PascalTypeDefinition;
import com.mendrix.pascal.psi.TypeKind;
import com.mendrix.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Semantic annotator for Pascal files.
 * Colors type definitions (class, record, interface) with different colors.
 */
public class PascalSemanticAnnotator implements Annotator {
    private static final Logger LOG = Logger.getInstance(PascalSemanticAnnotator.class);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Color type definition names
        if (element instanceof PascalTypeDefinition) {
            annotateTypeDefinition((PascalTypeDefinition) element, holder);
            // Also process all identifier tokens inside the type definition
            processIdentifiersInNode(element.getNode(), element, holder);
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

    private void annotateTypeReference(PsiElement element, AnnotationHolder holder) {
        String text = element.getText();

        // Only check type-like identifiers (start with T or I, or are known patterns)
        if (!text.startsWith("T") && !text.startsWith("I") && !text.startsWith("E")) {
            return;
        }

        // Skip the name identifier of a type definition (already colored by annotateTypeDefinition)
        PsiElement parent = element.getParent();
        if (parent instanceof PascalTypeDefinition) {
            PascalTypeDefinition typeDef = (PascalTypeDefinition) parent;
            if (element.equals(typeDef.getNameIdentifier())) {
                return; // Skip the type's own name
            }
        }

        // Look up this identifier in the type index
        Collection<PascalTypeDefinition> types = PascalTypeIndex.findTypes(text, element.getProject());

        // Debug: log misses for type-like names
        if (types.isEmpty()) {
            LOG.info("[PascalDebug] NOT FOUND in index: '" + text + "'");
        }

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

    private TextAttributesKey getColorForTypeKind(TypeKind kind) {
        switch (kind) {
            case CLASS:
                return PascalSyntaxHighlighter.TYPE_CLASS;
            case RECORD:
                return PascalSyntaxHighlighter.TYPE_RECORD;
            case INTERFACE:
                return PascalSyntaxHighlighter.TYPE_INTERFACE;
            default:
                return null;
        }
    }
}
