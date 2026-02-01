package nl.akiar.pascal.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import nl.akiar.pascal.PascalSyntaxHighlighter;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.*;
import nl.akiar.pascal.resolution.DelphiBuiltIns;
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

        // Color enum elements (individual enum values like taLeftJustify, clRed)
        if (element.getNode() != null && element.getNode().getElementType() == PascalElementTypes.ENUM_ELEMENT) {
            annotateEnumElement(element, holder);
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

        // Handle identifiers (usages) - also handle keyword tokens that can be used as identifiers
        if (element.getNode() != null && isIdentifierLikeToken(element.getNode().getElementType())) {
            // Skip keyword tokens that are being used as actual keywords (not as identifiers)
            // e.g., 'read', 'write', 'default' in property declarations should stay as keywords
            if (isKeywordUsedAsKeyword(element)) {
                return; // Let the syntax highlighter handle keyword coloring
            }
            annotateUsage(element, holder);
        }
    }

    /**
     * Check if the given element type can serve as an identifier.
     * This includes the IDENTIFIER token and "soft keywords" that can be used as
     * variable/parameter names (like "Index", "Name", "Read", "Write", etc.)
     */
    private boolean isIdentifierLikeToken(com.intellij.psi.tree.IElementType type) {
        for (com.intellij.psi.tree.IElementType identifierType : PsiUtil.IDENTIFIER_LIKE_TYPES) {
            if (type == identifierType) return true;
        }
        return false;
    }

    private void annotateUsage(PsiElement element, AnnotationHolder holder) {
        // Skip if it's inside a unit reference, unit declaration or uses section
        if (PsiUtil.hasParent(element, PascalElementTypes.UNIT_REFERENCE) ||
            PsiUtil.hasParent(element, PascalElementTypes.UNIT_DECL_SECTION) ||
            PsiUtil.hasParent(element, PascalElementTypes.USES_SECTION)) {
            return;
        }

        // Handle property getter/setter identifiers specially
        if (isPropertySpecifierIdentifier(element)) {
            annotatePropertySpecifier(element, holder);
            return;
        }

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

        // Check for built-in functions (Assigned, Inc, Dec, Length, etc.)
        if (DelphiBuiltIns.isBuiltInFunction(text)) {
            applyHighlight(element, holder, PascalSyntaxHighlighter.ROUTINE_CALL);
            return;
        }

        // Check for built-in types (Exception, TObject, etc.)
        if (DelphiBuiltIns.isBuiltInType(text)) {
            // Determine the appropriate color based on naming convention
            if (text.startsWith("E") || text.toLowerCase().contains("exception")) {
                applyHighlight(element, holder, PascalSyntaxHighlighter.TYPE_CLASS);
            } else if (text.startsWith("I") && text.length() > 1 && Character.isUpperCase(text.charAt(1))) {
                applyHighlight(element, holder, PascalSyntaxHighlighter.TYPE_INTERFACE);
            } else if (text.startsWith("T")) {
                applyHighlight(element, holder, PascalSyntaxHighlighter.TYPE_CLASS);
            } else {
                applyHighlight(element, holder, PascalSyntaxHighlighter.TYPE_SIMPLE);
            }
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
        PsiElement nameElement = typeDef.getNameIdentifier();
        if (nameElement == null) return;

        // First try to determine type from child element types (more reliable)
        TextAttributesKey key = getColorFromChildElementTypes(typeDef);

        // Fall back to TypeKind if no specific element type found
        if (key == null) {
            key = getColorForTypeKind(typeDef.getTypeKind());
        }

        if (key != null) {
            applyHighlight(nameElement, holder, key);
        }
    }

    private TextAttributesKey getColorFromChildElementTypes(PsiElement parent) {
        for (PsiElement child : parent.getChildren()) {
            if (child.getNode() == null) continue;
            var elementType = child.getNode().getElementType();
            if (elementType == PascalElementTypes.CLASS_TYPE) {
                return PascalSyntaxHighlighter.TYPE_CLASS;
            } else if (elementType == PascalElementTypes.RECORD_TYPE) {
                return PascalSyntaxHighlighter.TYPE_RECORD;
            } else if (elementType == PascalElementTypes.INTERFACE_TYPE) {
                return PascalSyntaxHighlighter.TYPE_INTERFACE;
            } else if (elementType == PascalElementTypes.ENUM_TYPE) {
                return PascalSyntaxHighlighter.TYPE_ENUM;
            }
        }
        return null;
    }

    private void annotateGenericParameter(PsiElement element, AnnotationHolder holder) {
        PsiElement id = element.getFirstChild();
        if (id != null && id.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            applyHighlight(id, holder, PascalSyntaxHighlighter.TYPE_PARAMETER);
        }
    }

    private void annotateEnumElement(PsiElement element, AnnotationHolder holder) {
        // Find the identifier child within the enum element
        PsiElement id = element.getFirstChild();
        if (id != null && id.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            applyHighlight(id, holder, PascalSyntaxHighlighter.ENUM_ELEMENT);
        } else {
            // If the element itself is the identifier, highlight it directly
            applyHighlight(element, holder, PascalSyntaxHighlighter.ENUM_ELEMENT);
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

    /**
     * Check if a keyword token is being used as an actual keyword (not as an identifier).
     * For example, 'read' in "property X: Integer read FX" is a keyword,
     * but 'Index' in "function Get(Index: Integer)" is used as an identifier.
     */
    private boolean isKeywordUsedAsKeyword(PsiElement element) {
        com.intellij.psi.tree.IElementType type = element.getNode().getElementType();

        // Property specifier keywords: read, write, stored, default, nodefault, index, implements
        if (type == PascalTokenTypes.KW_READ ||
            type == PascalTokenTypes.KW_WRITE ||
            type == PascalTokenTypes.KW_STORED ||
            type == PascalTokenTypes.KW_DEFAULT ||
            type == PascalTokenTypes.KW_NODEFAULT ||
            type == PascalTokenTypes.KW_IMPLEMENTS) {

            // If inside a property definition and followed by an identifier, it's a keyword
            if (PsiUtil.hasParent(element, PascalElementTypes.PROPERTY_DEFINITION)) {
                PsiElement next = PsiUtil.getNextNoneIgnorableSibling(element);
                if (next != null) {
                    com.intellij.psi.tree.IElementType nextType = next.getNode().getElementType();
                    // If followed by identifier or semicolon (for nodefault), it's being used as keyword
                    if (nextType == PascalTokenTypes.IDENTIFIER ||
                        nextType == PascalTokenTypes.SEMI ||
                        nextType == PascalTokenTypes.KW_READ ||
                        nextType == PascalTokenTypes.KW_WRITE ||
                        nextType == PascalTokenTypes.KW_DEFAULT ||
                        nextType == PascalTokenTypes.KW_STORED) {
                        return true;
                    }
                }
            }
        }

        // INDEX keyword: if inside property definition and followed by identifier, it's a keyword
        // But if inside FORMAL_PARAMETER, it's used as an identifier (parameter name)
        if (type == PascalTokenTypes.KW_INDEX) {
            if (PsiUtil.hasParent(element, PascalElementTypes.FORMAL_PARAMETER)) {
                return false; // Used as identifier (parameter name)
            }
            if (PsiUtil.hasParent(element, PascalElementTypes.PROPERTY_DEFINITION)) {
                return true; // Used as keyword in property
            }
        }

        // NAME keyword: similar logic
        if (type == PascalTokenTypes.KW_NAME) {
            // In external declarations, NAME is a keyword
            // In variable/parameter declarations, it could be used as identifier
            if (PsiUtil.hasParent(element, PascalElementTypes.FORMAL_PARAMETER) ||
                PsiUtil.hasParent(element, PascalElementTypes.VARIABLE_DEFINITION)) {
                return false;
            }
        }

        return false;
    }

    private boolean isPropertySpecifierIdentifier(PsiElement element) {
        // Check if inside PROPERTY_DEFINITION
        if (!PsiUtil.hasParent(element, PascalElementTypes.PROPERTY_DEFINITION)) {
            return false;
        }

        // Check if preceded by KW_READ, KW_WRITE, KW_STORED, or KW_DEFAULT
        PsiElement prev = PsiUtil.getPrevNoneIgnorableSibling(element);
        if (prev == null) return false;

        com.intellij.psi.tree.IElementType prevType = prev.getNode().getElementType();
        return prevType == PascalTokenTypes.KW_READ ||
               prevType == PascalTokenTypes.KW_WRITE ||
               prevType == PascalTokenTypes.KW_STORED ||
               prevType == PascalTokenTypes.KW_DEFAULT;
    }

    private void annotatePropertySpecifier(PsiElement element, AnnotationHolder holder) {
        // Find containing class
        PascalTypeDefinition containingClass = PsiTreeUtil.getParentOfType(
            element, PascalTypeDefinition.class);
        if (containingClass == null) return;

        String name = element.getText();

        // Look up in class members (including inherited)
        for (PsiElement member : containingClass.getMembers(true)) {
            if (member instanceof com.intellij.psi.PsiNameIdentifierOwner) {
                String memberName = ((com.intellij.psi.PsiNameIdentifierOwner) member).getName();
                if (name.equalsIgnoreCase(memberName)) {
                    if (member instanceof PascalRoutine) {
                        // It's a getter/setter method
                        applyHighlight(element, holder, PascalSyntaxHighlighter.METHOD_CALL);
                    } else if (member instanceof PascalVariableDefinition) {
                        // It's a direct field access
                        applyHighlight(element, holder, PascalSyntaxHighlighter.VAR_FIELD);
                    }
                    return;
                }
            }
        }

        // If not found in class, don't highlight (will show as regular identifier)
    }
}
