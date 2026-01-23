package nl.akiar.pascal.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import nl.akiar.pascal.PascalSyntaxHighlighter;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Semantic annotator for Pascal files.
 * Provides advanced highlighting for type definitions, variables, and usages (calls, types, parameters).
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

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Color generic parameters
        if (element.getNode() != null && element.getNode().getElementType() == PascalElementTypes.GENERIC_PARAMETER) {
            annotateGenericParameter(element, holder);
            return;
        }

        // Color unit references in uses clause
        if (element.getNode() != null && element.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
            applyHighlight(element, holder, PascalSyntaxHighlighter.UNIT_REFERENCE);
            return;
        }

        // Color type definition names
        if (element instanceof PascalTypeDefinition) {
            annotateTypeDefinition((PascalTypeDefinition) element, holder);
            return;
        }

        // Color variable definitions
        if (element instanceof PascalVariableDefinition) {
            annotateVariableDefinition((PascalVariableDefinition) element, holder);
            return;
        }

        // Color routine definitions
        if (element instanceof PascalRoutine) {
            annotateRoutine((PascalRoutine) element, holder);
            return;
        }

        // Color property definitions
        if (element instanceof PascalProperty) {
            annotateProperty((PascalProperty) element, holder);
            return;
        }

        // Handle identifiers (usages)
        if (element.getNode() != null && element.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            annotateUsage(element, holder);
        }
    }

    private void annotateUsage(PsiElement element, AnnotationHolder holder) {
        // Skip if it's the name identifier of a definition (handled by specific annotate methods)
        PsiElement parent = element.getParent();
        if (parent instanceof PascalTypeDefinition && ((PascalTypeDefinition) parent).getNameIdentifier() == element) return;
        if (parent instanceof PascalVariableDefinition && ((PascalVariableDefinition) parent).getNameIdentifier() == element) return;
        if (parent instanceof PascalRoutine && ((PascalRoutine) parent).getNameIdentifier() == element) return;
        if (parent instanceof PascalProperty && ((PascalProperty) parent).getNameIdentifier() == element) return;

        // Heuristic fallback for performance and if resolution fails
        String text = element.getText();
        if (SIMPLE_TYPES.stream().anyMatch(t -> t.equalsIgnoreCase(text))) {
            applyHighlight(element, holder, PascalSyntaxHighlighter.TYPE_SIMPLE);
            return;
        } else if ("TObject".equalsIgnoreCase(text)) {
            applyHighlight(element, holder, PascalSyntaxHighlighter.TYPE_CLASS);
            return;
        }

        // Use references to resolve usage
        com.intellij.psi.PsiReference[] refs = element.getReferences();
        if (refs.length == 0) {
            // Fallback for tests or if not picked up by platform
            refs = com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry.getReferencesFromProviders(element);
        }
        for (com.intellij.psi.PsiReference ref : refs) {
            PsiElement resolved = ref.resolve();
            if (resolved != null) {
                if (resolved instanceof PascalRoutine) {
                    PascalRoutine routine = (PascalRoutine) resolved;
                    TextAttributesKey key = routine.isMethod() ? 
                            PascalSyntaxHighlighter.METHOD_CALL : 
                            PascalSyntaxHighlighter.ROUTINE_CALL;
                    applyHighlight(element, holder, key);
                    return;
                } else if (resolved instanceof PascalTypeDefinition) {
                    PascalTypeDefinition typeDef = (PascalTypeDefinition) resolved;
                    applyHighlight(element, holder, getColorForTypeKind(typeDef.getTypeKind()));
                    return;
                } else if (resolved instanceof PascalVariableDefinition) {
                    PascalVariableDefinition varDef = (PascalVariableDefinition) resolved;
                    applyHighlight(element, holder, getColorForVariableKind(varDef.getVariableKind()));
                    return;
                } else if (resolved instanceof PascalProperty) {
                    applyHighlight(element, holder, PascalSyntaxHighlighter.METHOD_CALL);
                    return;
                }
            }
        }

        // Second fallback: simple type-like name check (T* or I*)
        if (text.length() > 1 && Character.isUpperCase(text.charAt(0))) {
            if (text.startsWith("T")) {
                applyHighlight(element, holder, PascalSyntaxHighlighter.TYPE_CLASS);
            } else if (text.startsWith("I")) {
                applyHighlight(element, holder, PascalSyntaxHighlighter.TYPE_INTERFACE);
            }
        }
    }

    private void applyHighlight(PsiElement element, AnnotationHolder holder, TextAttributesKey key) {
        if (key == null) return;
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(key)
                .create();
    }

    private void annotateVariableDefinition(PascalVariableDefinition varDef, AnnotationHolder holder) {
        PsiElement nameElement = varDef.getNameIdentifier();
        if (nameElement == null) return;

        TextAttributesKey key = getColorForVariableKind(varDef.getVariableKind());
        applyHighlight(nameElement, holder, key);
    }

    private void annotateRoutine(PascalRoutine routine, AnnotationHolder holder) {
        PsiElement nameElement = routine.getNameIdentifier();
        if (nameElement == null) return;

        TextAttributesKey key = routine.isMethod() ? 
                PascalSyntaxHighlighter.METHOD_DECLARATION : 
                PascalSyntaxHighlighter.ROUTINE_DECLARATION;

        applyHighlight(nameElement, holder, key);
    }

    private void annotateProperty(PascalProperty property, AnnotationHolder holder) {
        PsiElement nameElement = property.getNameIdentifier();
        if (nameElement == null) return;

        applyHighlight(nameElement, holder, PascalSyntaxHighlighter.PROPERTY_DECLARATION);
    }

    private void annotateTypeDefinition(PascalTypeDefinition typeDef, AnnotationHolder holder) {
        TextAttributesKey key = getColorForTypeKind(typeDef.getTypeKind());
        PsiElement nameElement = typeDef.getNameIdentifier();

        if (nameElement != null && key != null) {
            applyHighlight(nameElement, holder, key);
        }
    }

    private void annotateGenericParameter(PsiElement element, AnnotationHolder holder) {
        PsiElement id = element.getFirstChild();
        if (id != null && id.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            applyHighlight(id, holder, PascalSyntaxHighlighter.TYPE_PARAMETER);
        }
    }

    private TextAttributesKey getColorForTypeKind(TypeKind kind) {
        if (kind == null) return null;
        switch (kind) {
            case CLASS: return PascalSyntaxHighlighter.TYPE_CLASS;
            case RECORD: return PascalSyntaxHighlighter.TYPE_RECORD;
            case INTERFACE: return PascalSyntaxHighlighter.TYPE_INTERFACE;
            case PROCEDURAL: return PascalSyntaxHighlighter.TYPE_PROCEDURAL;
            case ENUM: return PascalSyntaxHighlighter.TYPE_ENUM;
            case ALIAS: return PascalSyntaxHighlighter.TYPE_SIMPLE;
            default: return null;
        }
    }

    private TextAttributesKey getColorForVariableKind(VariableKind kind) {
        if (kind == null) return null;
        switch (kind) {
            case GLOBAL: return PascalSyntaxHighlighter.VAR_GLOBAL;
            case LOCAL: return PascalSyntaxHighlighter.VAR_LOCAL;
            case PARAMETER: return PascalSyntaxHighlighter.VAR_PARAMETER;
            case FIELD: return PascalSyntaxHighlighter.VAR_FIELD;
            case CONSTANT: return PascalSyntaxHighlighter.VAR_CONSTANT;
            case THREADVAR: return PascalSyntaxHighlighter.VAR_THREADVAR;
            case LOOP_VAR: return PascalSyntaxHighlighter.VAR_LOCAL;
            default: return null;
        }
    }
}
