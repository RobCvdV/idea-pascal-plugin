package nl.akiar.pascal.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import nl.akiar.pascal.PascalPsiElement;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.TypeReferenceKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI element for type references in Pascal code.
 * <p>
 * Wraps identifiers that refer to types in contexts such as:
 * <ul>
 *   <li>Variable type annotations: {@code var X: Integer}</li>
 *   <li>Parameter types: {@code procedure F(A: String)}</li>
 *   <li>Return types: {@code function G: TObject}</li>
 *   <li>Inheritance: {@code class(TBase)}</li>
 *   <li>Generic type arguments: {@code TList<Integer>}</li>
 *   <li>Array element types: {@code array of Integer}</li>
 * </ul>
 * <p>
 * This element categorizes type references at parse time, enabling:
 * <ul>
 *   <li>Zero-cost highlighting for built-in types (no resolution needed)</li>
 *   <li>Optimized reference resolution (skip variable index lookups)</li>
 *   <li>Better error messages (type-specific vs. generic identifier errors)</li>
 * </ul>
 */
public class PascalTypeReferenceElement extends PascalPsiElement {
    /**
     * Cached kind to avoid recomputation.
     * Computed once on first access and then reused.
     */
    private TypeReferenceKind cachedKind = null;

    public PascalTypeReferenceElement(@NotNull ASTNode node) {
        super(node);
    }

    /**
     * Get the kind of this type reference (simple, user-defined, keyword, etc.).
     * The result is cached after first computation for performance.
     *
     * @return The type reference kind, never null
     */
    @NotNull
    public TypeReferenceKind getKind() {
        if (cachedKind != null) {
            return cachedKind;
        }

        String typeName = getReferencedTypeName();
        if (typeName == null) {
            cachedKind = TypeReferenceKind.UNKNOWN;
            return cachedKind;
        }

        // Check if it's a keyword type (string, array, etc.)
        if (nl.akiar.pascal.parser.PascalBuiltInTypes.INSTANCE.isKeywordType(typeName)) {
            cachedKind = TypeReferenceKind.KEYWORD_TYPE;
            return cachedKind;
        }

        // Check if it's a built-in simple type
        if (nl.akiar.pascal.parser.PascalBuiltInTypes.INSTANCE.isSimpleType(typeName)) {
            cachedKind = TypeReferenceKind.SIMPLE_TYPE;
            return cachedKind;
        }

        // Check naming conventions for user types (T*, I*, E* with uppercase second char)
        if (looksLikeUserType(typeName)) {
            cachedKind = TypeReferenceKind.USER_TYPE;
            return cachedKind;
        }

        // Fallback for unconventional names
        cachedKind = TypeReferenceKind.UNKNOWN;
        return cachedKind;
    }

    /**
     * Get the full type name being referenced.
     * May be qualified like "System.Integer" or simple like "Integer".
     *
     * @return The type name, or null if no identifiers found
     */
    @Nullable
    public String getReferencedTypeName() {
        StringBuilder name = new StringBuilder();
        collectTypeNameParts(getNode(), name);
        return name.length() > 0 ? name.toString() : null;
    }

    private void collectTypeNameParts(com.intellij.lang.ASTNode parent, StringBuilder name) {
        for (com.intellij.lang.ASTNode child = parent.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == PascalTokenTypes.IDENTIFIER) {
                if (name.length() > 0) {
                    name.append(".");
                }
                name.append(child.getText());
            } else if (child.getElementType() == PascalTokenTypes.DOT) {
                // Handled by appending dot before next identifier
            } else if (child.getElementType() == PascalTokenTypes.KW_STRING) {
                // Handle lowercase "string" keyword
                if (name.length() > 0) {
                    name.append(".");
                }
                name.append(child.getText());
            } else if (child.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.NAME_REFERENCE) {
                // NAME_REFERENCE wraps IDENTIFIERs in some contexts (e.g., anonymous routine
                // parameter types where sonar-delphi produces NameReferenceNode inside TypeReferenceNode)
                collectTypeNameParts(child, name);
            }
        }
    }

    /**
     * Get the first (or only) identifier element for reference creation.
     *
     * @return The first identifier PSI element, or null if none found
     */
    @Nullable
    public PsiElement getNameIdentifier() {
        for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == PascalTokenTypes.IDENTIFIER ||
                child.getElementType() == PascalTokenTypes.KW_STRING) {
                return child.getPsi();
            }
        }
        return null;
    }

    /**
     * Check if name follows Pascal type naming conventions.
     * Types typically start with T (TMyClass), I (IMyInterface), or E (EMyException)
     * followed by an uppercase letter.
     *
     * @param name The type name to check
     * @return true if it looks like a user-defined type
     */
    private boolean looksLikeUserType(String name) {
        if (name.length() < 2) return false;

        // Handle qualified names - check last component
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0) {
            name = name.substring(dotIndex + 1);
            if (name.length() < 2) return false;
        }

        char first = name.charAt(0);
        char second = name.charAt(1);

        // Check T*, I*, E* prefix with uppercase second character
        return (first == 'T' || first == 'I' || first == 'E') && Character.isUpperCase(second);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        // References are created by PascalReferenceContributor
        return super.getReferences();
    }
}
