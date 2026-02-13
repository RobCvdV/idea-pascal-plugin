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
import nl.akiar.pascal.psi.PascalAttribute;
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
            return nl.akiar.pascal.psi.PsiUtil.stripEscapePrefix(nameElement.getText());
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
        if (nodeType == PascalTokenTypes.KW_ARRAY) return TypeKind.ALIAS;

        // Skip ATTRIBUTE_DEFINITION and ATTRIBUTE_LIST nodes - don't scan their children
        // Attributes can contain LPAREN tokens that shouldn't affect type classification
        if (nodeType == nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_DEFINITION ||
            nodeType == nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_LIST) {
            return TypeKind.UNKNOWN; // Skip attributes entirely
        }

        // LPAREN indicates enum type ONLY if we're past any attributes
        if (nodeType == PascalTokenTypes.LPAREN) return TypeKind.ENUM;

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
        // The name identifier is the IDENTIFIER that appears BEFORE '=' and AFTER any attribute brackets.
        // Attributes are in format: [AttrName] or [AttrName(args)]
        // When attributes are present, they appear as direct children of the type declaration node
        // because sonar-delphi doesn't wrap them in AttributeListNode for type declarations.
        //
        // Strategy: Find IDENTIFIER that is NOT inside brackets
        // - If we see '[', we're in an attribute - skip until ']'
        // - First IDENTIFIER after all ']' brackets is the type name

        ASTNode node = getNode();
        boolean inBracket = false;

        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();

            // Skip attribute composite nodes entirely — they contain identifiers
            // (like "BaseUrl") that are NOT the type name
            if (type == nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_DEFINITION ||
                type == nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_LIST) {
                continue;
            }

            if (type == PascalTokenTypes.LBRACKET) {
                inBracket = true;
            } else if (type == PascalTokenTypes.RBRACKET) {
                inBracket = false;
            } else if (!inBracket && nl.akiar.pascal.psi.PsiUtil.findFirstRecursiveAnyOf(child, nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES) != null) {
                // Check if this child itself is an identifier or contains one (for soft keywords)
                ASTNode idNode = nl.akiar.pascal.psi.PsiUtil.findFirstRecursiveAnyOf(child, nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES);
                if (idNode != null) return idNode.getPsi();
            } else if (type == PascalTokenTypes.EQ) {
                // Reached '=' without finding identifier - shouldn't happen in valid code
                break;
            }
        }

        // Fallback: use recursive search
        ASTNode identifierNode = nl.akiar.pascal.psi.PsiUtil.findFirstRecursiveAnyOf(
            getNode(),
            nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES
        );
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
        StringBuilder sb = new StringBuilder();
        boolean[] state = {false, false}; // [foundKindKeyword, stop]
        buildSignatureRec(getNode(), sb, getTypeKind(), state);
        return sb.toString().trim();
    }

    private void buildSignatureRec(ASTNode node, StringBuilder sb, TypeKind kind, boolean[] state) {
        if (state[1]) return;

        IElementType type = node.getElementType();
        
        if (type == PascalTokenTypes.SEMI) {
            sb.append(";");
            state[1] = true;
            return;
        }

        if (kind == TypeKind.CLASS || kind == TypeKind.RECORD || kind == TypeKind.INTERFACE) {
            if (state[0] && isBodyStartKeyword(type)) {
                state[1] = true;
                return;
            }
            if (isKindKeyword(type)) {
                state[0] = true;
            }
        }

        if (node.getFirstChildNode() == null) {
            sb.append(node.getText());
        } else {
            for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                buildSignatureRec(child, sb, kind, state);
                if (state[1]) break;
            }
        }
    }

    private boolean isKindKeyword(IElementType type) {
        if (type == null) return false;
        String s = type.toString();
        return s.endsWith(".KW_CLASS") || s.endsWith(".CLASS") || s.endsWith(".CLASS_TYPE") ||
               s.endsWith(".KW_RECORD") || s.endsWith(".RECORD") || s.endsWith(".RECORD_TYPE") ||
               s.endsWith(".KW_INTERFACE") || s.endsWith(".INTERFACE") || s.endsWith(".INTERFACE_TYPE");
    }

    private boolean isBodyStartKeyword(IElementType type) {
        if (type == null) return false;
        String s = type.toString();
        if (s.startsWith("PascalTokenType.")) {
            s = s.substring("PascalTokenType.".length());
        }
        return s.equals("PRIVATE") || s.equals("PROTECTED") || s.equals("PUBLIC") || s.equals("PUBLISHED") ||
               s.equals("STRICT") || s.equals("VAR") || s.equals("CONST") || s.equals("TYPE") ||
               s.equals("PROCEDURE") || s.equals("FUNCTION") || s.equals("CONSTRUCTOR") || s.equals("DESTRUCTOR") ||
               s.equals("PROPERTY") || s.equals("OPERATOR") || s.equals("BEGIN") ||
               s.equals("VISIBILITY_SECTION") || s.equals("CLASS_BODY") || s.equals("RECORD_BODY") || s.equals("INTERFACE_BODY");
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
            // Handle TYPE_REFERENCE elements created by parser
            if (type == nl.akiar.pascal.psi.PascalElementTypes.TYPE_REFERENCE) {
                PsiElement typeRefElement = next.getPsi();
                if (typeRefElement instanceof nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) {
                    String typeName = ((nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) typeRefElement).getReferencedTypeName();
                    if (typeName != null) {
                        return typeName;
                    }
                }
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
        // Use cached member list when possible
        return nl.akiar.pascal.resolution.MemberResolutionCache.INSTANCE.getOrComputeMembers(
            this,
            includeAncestors,
            () -> {
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
        );
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

    @Override
    @NotNull
    public List<PascalAttribute> getAttributes() {
        // Attributes can be either:
        // 1. Direct children of this type definition (synthesized by PascalSonarParser for type declarations)
        // 2. Preceding siblings (for some edge cases)
        //
        // First, look for DIRECT child ATTRIBUTE_LIST elements (not in nested routines)
        List<PascalAttribute> directAttrs = new ArrayList<>();
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_LIST) {
                // Found an attribute list, collect its attributes
                directAttrs.addAll(PsiTreeUtil.findChildrenOfType(child, PascalAttribute.class));
            }
            // Stop at major structural elements (we don't want to descend into the class body)
            if (child.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.CLASS_TYPE ||
                child.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.RECORD_TYPE ||
                child.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.INTERFACE_TYPE) {
                break;
            }
        }
        if (!directAttrs.isEmpty()) {
            return directAttrs;
        }

        // Fallback: look for preceding sibling attributes (original behavior)
        List<PascalAttribute> attributes = new ArrayList<>();
        PsiElement prev = getPrevSibling();
        while (prev != null) {
            if (prev instanceof PascalAttribute) {
                // Insert at beginning to maintain source order
                attributes.add(0, (PascalAttribute) prev);
            } else if (prev.getNode().getElementType() != PascalTokenTypes.WHITE_SPACE
                    && !(prev.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.ATTRIBUTE_LIST)) {
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

    @Override
    @Nullable
    public String getGUID() {
        // GUIDs only apply to interfaces
        if (getTypeKind() != TypeKind.INTERFACE) {
            return null;
        }

        // For interfaces, look for INTERFACE_GUID elements inside the interface body
        // The GUID looks like ['{GUID-STRING}']
        Collection<nl.akiar.pascal.psi.PascalInterfaceGuid> guids = PsiTreeUtil.findChildrenOfType(this, nl.akiar.pascal.psi.PascalInterfaceGuid.class);
        if (!guids.isEmpty()) {
            return guids.iterator().next().getGuidValue();
        }

        // Fallback: Check for old-style GUID attributes (for backward compatibility during transition)
        Collection<PascalAttribute> attrs = PsiTreeUtil.findChildrenOfType(this, PascalAttribute.class);
        for (PascalAttribute attr : attrs) {
            if (attr.isGUID()) {
                return attr.getGUIDValue();
            }
        }
        return null;
    }

    @Override
    public boolean isForwardDeclaration() {
        // Forward declarations only apply to class, record, and interface types
        TypeKind kind = getTypeKind();
        if (kind != TypeKind.CLASS && kind != TypeKind.RECORD && kind != TypeKind.INTERFACE) {
            return false;
        }

        // Check if there are body tokens
        boolean[] state = {false, false}; // [foundKindKeyword, foundBodyToken]
        containsBodyToken(getNode(), state);

        // If we found kind keyword but did NOT find any body tokens, it's a forward declaration
        return state[0] && !state[1];
    }

    private void containsBodyToken(ASTNode node, boolean[] state) {
        if (state[1]) return; // already found a body token

        IElementType type = node.getElementType();
        if (isKindKeyword(type)) {
            state[0] = true;
        } else if (state[0]) {
            // Check for body start OR other non-ignored tokens that indicate it's not a forward decl
            // (like parentheses for parents)
            if (isBodyStartKeyword(type) || type == PascalTokenTypes.LPAREN) {
                state[1] = true;
                return;
            }
        }
        
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            containsBodyToken(child, state);
            if (state[1]) break;
        }
    }
}
