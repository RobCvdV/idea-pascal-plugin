package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalTypeDefinition;

import java.util.ArrayList;
import java.util.List;
import nl.akiar.pascal.stubs.PascalPropertyStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PascalPropertyImpl extends StubBasedPsiElementBase<PascalPropertyStub> implements PascalProperty {

    public PascalPropertyImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PascalPropertyImpl(@NotNull PascalPropertyStub stub, @NotNull IStubElementType<?, ?> nodeType) {
        super(stub, nodeType);
    }

    @Override
    @Nullable
    public String getName() {
        PascalPropertyStub stub = getGreenStub();
        if (stub != null) return stub.getName();
        PsiElement nameId = getNameIdentifier();
        return nameId != null ? nl.akiar.pascal.psi.PsiUtil.stripEscapePrefix(nameId.getText()) : null;
    }

    @Override
    public int getTextOffset() {
        PsiElement nameId = getNameIdentifier();
        return nameId != null ? nameId.getTextRange().getStartOffset() : super.getTextOffset();
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // Find the identifier AFTER the 'property' keyword, skipping any attribute nodes.
        // Without this, properties with attributes like [Map('id')] would return 'Map' as the name
        // because findFirstRecursiveAnyOf would find the attribute's identifier first.
        ASTNode node = getNode();
        boolean afterPropertyKeyword = false;
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == PascalTokenTypes.KW_PROPERTY) {
                afterPropertyKeyword = true;
                continue;
            }
            if (!afterPropertyKeyword) continue;
            // Skip whitespace
            if (child.getElementType() == PascalTokenTypes.WHITE_SPACE) continue;
            // Check for identifier-like tokens (includes soft keywords like Name, Index, etc.)
            for (com.intellij.psi.tree.IElementType type : nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES) {
                if (child.getElementType() == type) {
                    return child.getPsi();
                }
            }
            // If we find a non-identifier, non-whitespace token after 'property', stop
            break;
        }
        // Fallback: find first identifier-like token (skipping attribute subtrees)
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            // Skip ATTRIBUTE_LIST and ATTRIBUTE_DEFINITION subtrees
            if (child.getElementType() == PascalElementTypes.ATTRIBUTE_LIST ||
                child.getElementType() == PascalElementTypes.ATTRIBUTE_DEFINITION) {
                continue;
            }
            for (com.intellij.psi.tree.IElementType type : nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES) {
                if (child.getElementType() == type) {
                    return child.getPsi();
                }
            }
        }
        return null;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Renaming not supported yet");
    }

    @Override
    @Nullable
    public String getTypeName() {
        PascalPropertyStub stub = getGreenStub();
        if (stub != null) return stub.getTypeName();

        ASTNode node = getNode();

        // Approach 1: Find COLON as direct child, then look for TYPE_REFERENCE or IDENTIFIER after it
        ASTNode colon = node.findChildByType(PascalTokenTypes.COLON);
        if (colon != null) {
            String result = extractTypeAfterColon(colon);
            if (result != null) return result;
        }

        // Approach 2: Recursive COLON search — handles cases where COLON is nested in an intermediate AST node.
        // For indexed properties (property Items[Index: Integer]: Pointer), skip colons inside brackets.
        if (colon == null) {
            boolean pastBracket = true; // true if no brackets seen yet (non-indexed property)
            for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                if (child.getElementType() == PascalTokenTypes.LBRACKET) pastBracket = false;
                if (child.getElementType() == PascalTokenTypes.RBRACKET) pastBracket = true;
                if (pastBracket && child.getElementType() == PascalElementTypes.TYPE_REFERENCE) {
                    PsiElement typeRefElement = child.getPsi();
                    if (typeRefElement instanceof nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) {
                        return ((nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) typeRefElement).getFullTypeName();
                    }
                }
            }
        }

        // Approach 3: Text-based fallback — extract type from property text
        String text = node.getText();
        if (text != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                ":\\s*([A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*(<[^>]+>)?)\\s*(read|write|stored|default|;|$)",
                java.util.regex.Pattern.CASE_INSENSITIVE
            ).matcher(text);
            // For indexed properties, find the last match (after the bracket parameters)
            String lastMatch = null;
            while (m.find()) {
                lastMatch = m.group(1);
            }
            if (lastMatch != null) return lastMatch;
        }

        return null;
    }

    @Nullable
    private String extractTypeAfterColon(ASTNode colon) {
        ASTNode next = colon.getTreeNext();
        while (next != null && (next.getElementType() == PascalTokenTypes.WHITE_SPACE)) {
            next = next.getTreeNext();
        }
        if (next != null) {
            if (next.getElementType() == PascalElementTypes.TYPE_REFERENCE) {
                PsiElement typeRefElement = next.getPsi();
                if (typeRefElement instanceof nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) {
                    return ((nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) typeRefElement).getFullTypeName();
                }
            }
            if (next.getElementType() == PascalTokenTypes.IDENTIFIER) {
                return next.getText();
            }
        }
        return null;
    }

    @Override
    @Nullable
    public String getContainingClassName() {
        PascalPropertyStub stub = getGreenStub();
        if (stub != null) return stub.getContainingClassName();

        PascalTypeDefinition containingClass = PsiTreeUtil.getParentOfType(this, PascalTypeDefinition.class);
        return containingClass != null ? containingClass.getName() : null;
    }

    @Override
    @Nullable
    public String getReadSpecifier() {
        return findSpecifierValue("read");
    }

    @Override
    @Nullable
    public String getWriteSpecifier() {
        return findSpecifierValue("write");
    }

    private String findSpecifierValue(String specifier) {
        ASTNode node = getNode();
        ASTNode child = node.getFirstChildNode();

        // Determine which keyword token to look for
        IElementType keywordType = null;
        if ("read".equalsIgnoreCase(specifier)) {
            keywordType = PascalTokenTypes.KW_READ;
        } else if ("write".equalsIgnoreCase(specifier)) {
            keywordType = PascalTokenTypes.KW_WRITE;
        } else if ("stored".equalsIgnoreCase(specifier)) {
            keywordType = PascalTokenTypes.KW_STORED;
        } else if ("default".equalsIgnoreCase(specifier)) {
            keywordType = PascalTokenTypes.KW_DEFAULT;
        }

        while (child != null) {
            // Check for keyword token (read, write, stored, default)
            boolean isSpecifierKeyword = (keywordType != null && child.getElementType() == keywordType);
            // Also check for identifier (fallback for older parsing)
            boolean isSpecifierIdentifier = (child.getElementType() == PascalTokenTypes.IDENTIFIER &&
                                             child.getText().equalsIgnoreCase(specifier));

            if (isSpecifierKeyword || isSpecifierIdentifier) {
                ASTNode value = child.getTreeNext();
                while (value != null && value.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    value = value.getTreeNext();
                }
                if (value != null && value.getElementType() == PascalTokenTypes.IDENTIFIER) {
                    return value.getText();
                }
            }
            child = child.getTreeNext();
        }
        return null;
    }

    @Override
    @NotNull
    public String getUnitName() {
        String fileName = getContainingFile().getName();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    @Override
    @Nullable
    public String getDocComment() {
        // Simple implementation for now
        return null;
    }

    @Override
    @Nullable
    public PascalTypeDefinition getContainingClass() {
        return PsiTreeUtil.getParentOfType(this, PascalTypeDefinition.class);
    }

    @Override
    @Nullable
    public String getVisibility() {
        return nl.akiar.pascal.psi.PsiUtil.getVisibility(this);
    }

    @Override
    public String toString() {
        return "PascalProperty(" + getName() + ")";
    }

    @Override
    @NotNull
    public List<PascalAttribute> getAttributes() {
        List<PascalAttribute> attributes = new ArrayList<>();
        PsiElement prev = getPrevSibling();
        while (prev != null) {
            if (prev instanceof PascalAttribute) {
                // Insert at beginning to maintain source order
                attributes.add(0, (PascalAttribute) prev);
            } else if (prev.getNode().getElementType() != PascalTokenTypes.WHITE_SPACE
                    && !(prev.getNode().getElementType() == PascalElementTypes.ATTRIBUTE_LIST)) {
                // Stop at non-whitespace, non-attribute elements
                break;
            }
            prev = prev.getPrevSibling();
        }
        return attributes;
    }

    @Override
    @Nullable
    public PascalAttribute findAttribute(@NotNull String name) {
        for (PascalAttribute attr : getAttributes()) {
            if (name.equalsIgnoreCase(attr.getName())) {
                return attr;
            }
        }
        return null;
    }
}
