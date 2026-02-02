package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalProperty;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.TypeKind;
import nl.akiar.pascal.psi.VariableKind;
import nl.akiar.pascal.stubs.PascalTypeStub;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import nl.akiar.pascal.reference.PascalTypeReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of PascalTypeDefinition PSI element.
 * Represents type definitions like: TMyClass = class, TMyRecord = record, IMyInterface = interface
 */
public class PascalTypeDefinitionImpl extends StubBasedPsiElementBase<PascalTypeStub> implements PascalTypeDefinition {
    private static final Logger LOG = Logger.getInstance(PascalTypeDefinitionImpl.class);

    public PascalTypeDefinitionImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PascalTypeDefinitionImpl(@NotNull PascalTypeStub stub, @NotNull IStubElementType<?, ?> nodeType) {
        super(stub, nodeType);
    }

    @Override
    @Nullable
    public String getName() {
        PascalTypeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getName();
        }

        // Parse from AST
        PsiElement nameElement = getNameIdentifier();
        if (nameElement != null) {
            return nameElement.getText();
        }
        return null;
    }

    @Override
    @NotNull
    public TypeKind getTypeKind() {
        PascalTypeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getTypeKind();
        }

        // Parse from AST - look for class, record, or interface keyword recursively
        return findTypeKindInNode(getNode());
    }

    private TypeKind findTypeKindInNode(ASTNode node) {
        IElementType nodeType = node.getElementType();
        if (nodeType == PascalTokenTypes.KW_CLASS) return TypeKind.CLASS;
        if (nodeType == PascalTokenTypes.KW_RECORD) return TypeKind.RECORD;
        if (nodeType == PascalTokenTypes.KW_INTERFACE || nodeType == PascalTokenTypes.KW_DISPINTERFACE) return TypeKind.INTERFACE;
        if (nodeType == PascalTokenTypes.KW_REFERENCE || nodeType == PascalTokenTypes.KW_PROCEDURE || nodeType == PascalTokenTypes.KW_FUNCTION) return TypeKind.PROCEDURAL;
        if (nodeType == PascalTokenTypes.LPAREN) return TypeKind.ENUM;
        if (nodeType == PascalTokenTypes.KW_ARRAY) return TypeKind.ALIAS;

        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            TypeKind kind = findTypeKindInNode(child);
            if (kind != TypeKind.UNKNOWN && kind != TypeKind.ALIAS) {
                return kind;
            }
            // If we found an ALIAS (like TMyArray = array of...), keep looking in case there's something more specific
            // (though usually array is specific enough)
            if (kind == TypeKind.ALIAS && nodeType != nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION) {
                 return kind;
            }
        }
        
        return node.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION ? TypeKind.ALIAS : TypeKind.UNKNOWN;
    }

    @Override
    @NotNull
    public List<String> getTypeParameters() {
        PascalTypeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getTypeParameters();
        }

        // With Sonar parser, generic parameters are mapped to GENERIC_PARAMETER element type
        List<String> results = new ArrayList<>();
        ASTNode node = getNode();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER) {
                // Collect all identifiers within the generic parameter node
                // (It might contain multiple names like <T, K>)
                collectIdentifiers(child, results);
            } else if (child.getElementType() == PascalTokenTypes.EQ) {
                // Generic parameters must appear before '='
                break;
            }
        }

        // Legacy fallback
        if (results.isEmpty()) {
            ASTNode nameNode = node.findChildByType(PascalTokenTypes.IDENTIFIER);
            if (nameNode != null) {
                ASTNode current = nameNode.getTreeNext();
                while (current != null && current.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    current = current.getTreeNext();
                }
                if (current != null && current.getElementType() == PascalTokenTypes.LT) {
                    current = current.getTreeNext();
                    while (current != null) {
                        IElementType type = current.getElementType();
                        if (type == PascalTokenTypes.GT || type == PascalTokenTypes.EQ) {
                            break;
                        }
                        if (type == PascalTokenTypes.IDENTIFIER) {
                            results.add(current.getText());
                        }
                        current = current.getTreeNext();
                    }
                }
            }
        }
        return results;
    }

    private void collectIdentifiers(ASTNode node, List<String> results) {
        if (node.getElementType() == PascalTokenTypes.IDENTIFIER) {
            results.add(node.getText());
        }
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            collectIdentifiers(child, results);
        }
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // The name identifier is the first IDENTIFIER token in the node
        // Use recursive search because Sonar nests identifiers
        ASTNode identifierNode = nl.akiar.pascal.psi.PsiUtil.findFirstRecursive(getNode(), nl.akiar.pascal.PascalTokenTypes.IDENTIFIER);
        if (identifierNode != null) {
            return identifierNode.getPsi();
        }
        return null;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        // For now, we don't support renaming
        throw new IncorrectOperationException("Renaming not supported yet");
    }


    @Override
    @Nullable
    public String getDocComment() {
        // Look for comments immediately preceding this type definition
        // Pascal doc comments can be: { comment }, (* comment *), or // comment
        StringBuilder docBuilder = new StringBuilder();
        PsiElement prev = getPrevSibling();

        // Collect consecutive comments (skipping whitespace)
        while (prev != null) {
            IElementType type = prev.getNode().getElementType();
            if (type == PascalTokenTypes.WHITE_SPACE) {
                // Check if it's more than one newline (empty line) - stop collecting
                String text = prev.getText();
                long newlines = text.chars().filter(c -> c == '\n').count();
                if (newlines > 1) {
                    break; // Empty line separates doc comment from type
                }
                prev = prev.getPrevSibling();
                continue;
            }
            if (type == PascalTokenTypes.BLOCK_COMMENT || type == PascalTokenTypes.LINE_COMMENT) {
                String commentText = extractCommentContent(prev.getText(), type);
                if (docBuilder.length() > 0) {
                    docBuilder.insert(0, "\n");
                }
                docBuilder.insert(0, commentText);
                prev = prev.getPrevSibling();
            } else {
                // Hit something other than a comment or whitespace
                break;
            }
        }

        String result = docBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String extractCommentContent(String comment, IElementType type) {
        if (type == PascalTokenTypes.LINE_COMMENT) {
            // Remove leading //
            if (comment.startsWith("//")) {
                return comment.substring(2).trim();
            }
        } else if (type == PascalTokenTypes.BLOCK_COMMENT) {
            // Remove { } or (* *)
            if (comment.startsWith("{") && comment.endsWith("}")) {
                return comment.substring(1, comment.length() - 1).trim();
            } else if (comment.startsWith("(*") && comment.endsWith("*)")) {
                return comment.substring(2, comment.length() - 2).trim();
            }
        }
        return comment.trim();
    }

    @Override
    @NotNull
    public String getDeclarationHeader() {
        ASTNode node = getNode();
        StringBuilder sb = new StringBuilder();
        ASTNode child = node.getFirstChildNode();
        TypeKind kind = getTypeKind();
        boolean foundKindKeyword = false;

        while (child != null) {
            IElementType type = child.getElementType();

            // Stop at semicolon
            if (type == PascalTokenTypes.SEMI) {
                sb.append(";");
                break;
            }

            // For structured types, stop at keywords that start the body
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

            sb.append(child.getText());
            child = child.getTreeNext();
        }

        return sb.toString().trim();
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

    @Override
    @NotNull
    public List<PascalRoutine> getMethods() {
        return new ArrayList<>(PsiTreeUtil.findChildrenOfType(this, PascalRoutine.class));
    }

    @Override
    @NotNull
    public List<PascalProperty> getProperties() {
        return new ArrayList<>(PsiTreeUtil.findChildrenOfType(this, PascalProperty.class));
    }

    @Override
    @NotNull
    public List<PascalVariableDefinition> getFields() {
        List<PascalVariableDefinition> fields = new ArrayList<>();
        Collection<PascalVariableDefinition> vars = PsiTreeUtil.findChildrenOfType(this, PascalVariableDefinition.class);
        for (PascalVariableDefinition var : vars) {
            if (var.getVariableKind() == VariableKind.FIELD) {
                fields.add(var);
            }
        }
        return fields;
    }

    @Override
    @Nullable
    public String getSuperClassName() {
        // Stub-first approach: get from stub if available
        PascalTypeStub stub = getGreenStub();
        if (stub != null) {
            return stub.getSuperClassName();
        }

        // Fall back to AST parsing
        return extractSuperClassNameFromAST();
    }

    /**
     * Extract superclass name from AST when stub is not available.
     * The AST structure is:
     *   TYPE_DEFINITION
     *     IDENTIFIER (type name)
     *     CLASS_TYPE / RECORD_TYPE / INTERFACE_TYPE
     *       LPAREN
     *       IDENTIFIER (superclass name)
     *       ...
     */
    @Nullable
    private String extractSuperClassNameFromAST() {
        ASTNode node = getNode();
        if (node == null) return null;

        // Check that we're not an enum or alias type
        TypeKind kind = getTypeKind();
        if (kind == TypeKind.ENUM || kind == TypeKind.ALIAS || kind == TypeKind.PROCEDURAL) {
            return null;
        }

        // Look for CLASS_TYPE, RECORD_TYPE, or INTERFACE_TYPE child node
        ASTNode typeNode = node.findChildByType(nl.akiar.pascal.psi.PascalElementTypes.CLASS_TYPE);
        if (typeNode == null) {
            typeNode = node.findChildByType(nl.akiar.pascal.psi.PascalElementTypes.RECORD_TYPE);
        }
        if (typeNode == null) {
            typeNode = node.findChildByType(nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_TYPE);
        }

        // If no specific type node, search in the TYPE_DEFINITION itself
        if (typeNode == null) {
            typeNode = node;
        }

        // Look for LPAREN within the type node
        ASTNode lparen = typeNode.findChildByType(PascalTokenTypes.LPAREN);
        if (lparen == null) {
            // Also check recursively in children
            lparen = nl.akiar.pascal.psi.PsiUtil.findFirstRecursive(typeNode, PascalTokenTypes.LPAREN);
        }
        if (lparen == null) return null;

        // Find the first identifier after LPAREN - this is the superclass name
        ASTNode next = lparen.getTreeNext();
        StringBuilder superName = new StringBuilder();
        while (next != null) {
            IElementType type = next.getElementType();
            if (type == PascalTokenTypes.WHITE_SPACE) {
                next = next.getTreeNext();
                continue;
            }
            if (type == PascalTokenTypes.IDENTIFIER) {
                superName.append(next.getText());
                next = next.getTreeNext();
                // Handle dotted names like System.TObject
                while (next != null) {
                    if (next.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                        next = next.getTreeNext();
                        continue;
                    }
                    if (next.getElementType() == PascalTokenTypes.DOT) {
                        superName.append(".");
                        next = next.getTreeNext();
                    } else if (next.getElementType() == PascalTokenTypes.IDENTIFIER) {
                        superName.append(next.getText());
                        next = next.getTreeNext();
                    } else {
                        break;
                    }
                }
                return superName.length() > 0 ? superName.toString() : null;
            }
            // If we hit RPAREN or COMMA before identifier, no superclass
            if (type == PascalTokenTypes.RPAREN || type == PascalTokenTypes.COMMA) {
                return null;
            }
            next = next.getTreeNext();
        }
        return null;
    }

    @Override
    @Nullable
    public PascalTypeDefinition getSuperClass() {
        // Step 1: Get superclass name (fast path via stub, or slow path via AST)
        String superClassName = getSuperClassName();
        if (superClassName == null) {
            return null;
        }

        // Step 2a: First, try to find in the same file (fast path, no index needed)
        // This is important for test fixtures and when the superclass is in the same unit
        PsiFile containingFile = getContainingFile();
        if (containingFile != null) {
            Collection<PascalTypeDefinition> sameFileTypes =
                    PsiTreeUtil.findChildrenOfType(containingFile, PascalTypeDefinition.class);
            for (PascalTypeDefinition typeDef : sameFileTypes) {
                if (typeDef != this && superClassName.equalsIgnoreCase(typeDef.getName())) {
                    return typeDef;
                }
            }
        }

        // Step 2b: Fall back to transitive dependency resolution for cross-unit lookups
        nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult result =
                nl.akiar.pascal.stubs.PascalTypeIndex.findTypeWithTransitiveDeps(
                        superClassName, containingFile, getTextOffset());
        if (!result.getInScopeTypes().isEmpty()) {
            return result.getInScopeTypes().get(0);
        }

        return null;
    }

    @Override
    @NotNull
    public List<PsiElement> getMembers(boolean includeAncestors) {
        if (!includeAncestors) {
            List<PsiElement> members = new ArrayList<>();
            members.addAll(getMethods());
            members.addAll(getProperties());
            members.addAll(getFields());
            return members;
        }
        // Use visited set to detect circular references in inheritance
        return getMembersWithCircularDetection(new HashSet<>());
    }

    /**
     * Internal method that tracks visited types to prevent infinite loops
     * in case of circular inheritance (which shouldn't happen, but we handle it gracefully).
     */
    private List<PsiElement> getMembersWithCircularDetection(Set<String> visited) {
        List<PsiElement> members = new ArrayList<>();
        members.addAll(getMethods());
        members.addAll(getProperties());
        members.addAll(getFields());

        // Create a unique key for this type
        String myKey = getUnitName() + "." + getName();
        if (visited.contains(myKey)) {
            LOG.warn("[PascalType] Circular inheritance detected: " + myKey);
            return members;
        }
        visited.add(myKey);

        PascalTypeDefinition superClass = getSuperClass();
        if (superClass instanceof PascalTypeDefinitionImpl) {
            members.addAll(((PascalTypeDefinitionImpl) superClass).getMembersWithCircularDetection(visited));
        } else if (superClass != null) {
            // For non-impl types, just call getMembers (unlikely path)
            members.addAll(superClass.getMembers(true));
        }

        return members;
    }

    @Override
    public String toString() {
        return "PascalTypeDefinition(" + getName() + ", " + getTypeKind() + ")";
    }

    @Override
    @NotNull
    public String getUnitName() {
        return nl.akiar.pascal.psi.PsiUtil.getUnitName(this);
    }
}
