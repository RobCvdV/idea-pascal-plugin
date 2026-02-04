package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalAttribute;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.stubs.PascalRoutineStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PascalRoutineImpl extends StubBasedPsiElementBase<PascalRoutineStub> implements PascalRoutine {

    public PascalRoutineImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PascalRoutineImpl(@NotNull PascalRoutineStub stub, @NotNull IStubElementType<?, ?> nodeType) {
        super(stub, nodeType);
    }

    @Override
    @Nullable
    public String getName() {
        PascalRoutineStub stub = getGreenStub();
        if (stub != null) return stub.getName();
        PsiElement nameId = getNameIdentifier();
        return nameId != null ? nameId.getText() : null;
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // The routine name is the IDENTIFIER that appears AFTER the routine keyword
        // (procedure, function, constructor, destructor) and BEFORE the parameter list.
        //
        // When attributes are present, they appear BEFORE the routine keyword:
        // [Authenticate(...)]
        // function PackagingBalancesByClientIds(...)
        //
        // Strategy:
        // 1. Find the routine keyword (procedure, function, constructor, destructor)
        // 2. Find the first identifier AFTER the routine keyword

        ASTNode node = getNode();
        int routineKeywordOffset = -1;

        // Find the position of the routine keyword
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();
            if (type == PascalTokenTypes.KW_PROCEDURE ||
                type == PascalTokenTypes.KW_FUNCTION ||
                type == PascalTokenTypes.KW_CONSTRUCTOR ||
                type == PascalTokenTypes.KW_DESTRUCTOR) {
                routineKeywordOffset = child.getStartOffset() + child.getTextLength();
                break;
            }
        }

        if (routineKeywordOffset < 0) {
            // No routine keyword found - fall back to original logic
            return findNameIdentifierFallback();
        }

        // Find the first identifier AFTER the routine keyword
        List<ASTNode> allIds = nl.akiar.pascal.psi.PsiUtil.findAllRecursiveAnyOf(
            node,
            nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES
        );

        // Find cutoff: first LPAREN or COLON or SEMI AFTER the routine keyword
        int cutoffOffset = Integer.MAX_VALUE;
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();
            int offset = child.getStartOffset();
            if (offset > routineKeywordOffset) {
                if (type == PascalTokenTypes.LPAREN || type == PascalTokenTypes.COLON || type == PascalTokenTypes.SEMI) {
                    cutoffOffset = offset;
                    break;
                }
            }
        }

        ASTNode bestId = null;
        for (ASTNode idNode : allIds) {
            int idOffset = idNode.getStartOffset();

            // Must be after routine keyword
            if (idOffset < routineKeywordOffset) {
                continue;
            }

            // Must be before cutoff
            if (idOffset >= cutoffOffset) {
                continue;
            }

            // Skip identifiers inside parameters
            PsiElement psi = idNode.getPsi();
            if (nl.akiar.pascal.psi.PsiUtil.hasParent(psi, nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER)) {
                continue;
            }

            // For qualified names (TClass.Method), keep the last one
            bestId = idNode;
        }

        return bestId != null ? bestId.getPsi() : null;
    }

    /**
     * Fallback method for finding name identifier when no routine keyword is found.
     */
    @Nullable
    private PsiElement findNameIdentifierFallback() {
        ASTNode node = getNode();
        List<ASTNode> allIds = nl.akiar.pascal.psi.PsiUtil.findAllRecursiveAnyOf(
            node,
            nl.akiar.pascal.psi.PsiUtil.IDENTIFIER_LIKE_TYPES
        );

        int parenOffset = findFirstTokenOffset(node, nl.akiar.pascal.PascalTokenTypes.LPAREN);
        int colonOffset = findFirstTokenOffset(node, nl.akiar.pascal.PascalTokenTypes.COLON);
        int semiOffset = findFirstTokenOffset(node, nl.akiar.pascal.PascalTokenTypes.SEMI);

        int cutoffOffset = Integer.MAX_VALUE;
        if (parenOffset >= 0) cutoffOffset = Math.min(cutoffOffset, parenOffset);
        if (colonOffset >= 0) cutoffOffset = Math.min(cutoffOffset, colonOffset);
        if (semiOffset >= 0) cutoffOffset = Math.min(cutoffOffset, semiOffset);

        ASTNode bestId = null;
        for (ASTNode idNode : allIds) {
            PsiElement psi = idNode.getPsi();
            if (nl.akiar.pascal.psi.PsiUtil.hasParent(psi, nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER)) {
                continue;
            }
            int idOffset = idNode.getStartOffset();
            if (idOffset >= cutoffOffset) {
                continue;
            }
            bestId = idNode;
        }

        return bestId != null ? bestId.getPsi() : null;
    }

    /**
     * Find the offset of the first occurrence of a specific token type.
     * Returns -1 if not found.
     */
    private int findFirstTokenOffset(ASTNode parent, IElementType tokenType) {
        ASTNode found = nl.akiar.pascal.psi.PsiUtil.findFirstRecursive(parent, tokenType);
        return found != null ? found.getStartOffset() : -1;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Renaming not supported yet");
    }

    @Override
    public boolean isImplementation() {
        PascalRoutineStub stub = getGreenStub();
        if (stub != null) return stub.isImplementation();

        // Heuristic: check if we are in implementation section
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent.getNode().getElementType() == PascalElementTypes.IMPLEMENTATION_SECTION) return true;
            if (parent.getNode().getElementType() == PascalElementTypes.INTERFACE_SECTION) return false;
            parent = parent.getParent();
        }
        return false;
    }

    @Override
    @Nullable
    public PascalRoutine getDeclaration() {
        if (!isImplementation()) return null;
        String name = getName();
        if (name == null) return null;

        // Search in interface section of the same file
        PsiFile file = getContainingFile();
        Collection<PascalRoutine> routines = PsiTreeUtil.findChildrenOfType(file, PascalRoutine.class);
        for (PascalRoutine routine : routines) {
            if (!routine.isImplementation() && matchesSignature(routine)) {
                return routine;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public PascalRoutine getImplementation() {
        if (isImplementation()) return null;
        String name = getName();
        if (name == null) return null;

        // Search in implementation section of the same file
        PsiFile file = getContainingFile();
        Collection<PascalRoutine> routines = PsiTreeUtil.findChildrenOfType(file, PascalRoutine.class);
        for (PascalRoutine routine : routines) {
            if (routine.isImplementation() && matchesSignature(routine)) {
                return routine;
            }
        }
        return null;
    }

    private boolean matchesSignature(PascalRoutine other) {
        String name = getName();
        String otherName = other.getName();
        if (name == null || otherName == null || !name.equalsIgnoreCase(otherName)) return false;

        // Compare parameters
        Collection<PascalVariableDefinition> myParams = PsiTreeUtil.findChildrenOfType(this, PascalVariableDefinition.class);
        Collection<PascalVariableDefinition> otherParams = PsiTreeUtil.findChildrenOfType(other, PascalVariableDefinition.class);

        // Filter only parameters
        java.util.List<PascalVariableDefinition> myFormalParams = myParams.stream()
                .filter(v -> v.getVariableKind() == nl.akiar.pascal.psi.VariableKind.PARAMETER)
                .collect(java.util.stream.Collectors.toList());
        java.util.List<PascalVariableDefinition> otherFormalParams = otherParams.stream()
                .filter(v -> v.getVariableKind() == nl.akiar.pascal.psi.VariableKind.PARAMETER)
                .collect(java.util.stream.Collectors.toList());

        if (myFormalParams.size() != otherFormalParams.size()) return false;

        for (int i = 0; i < myFormalParams.size(); i++) {
            PascalVariableDefinition myP = myFormalParams.get(i);
            PascalVariableDefinition otherP = otherFormalParams.get(i);

            String myType = myP.getTypeName();
            String otherType = otherP.getTypeName();
            if (myType == null || otherType == null || !myType.equalsIgnoreCase(otherType)) return false;

            // Also compare modifiers (const, var, out)
            if (getModifier(myP) != getModifier(otherP)) return false;
        }

        return true;
    }

    private int getModifier(PascalVariableDefinition param) {
        PsiElement parent = param.getParent();
        while (parent != null && parent.getNode().getElementType() != nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER) {
            parent = parent.getParent();
        }
        if (parent == null) return 0;
        ASTNode node = parent.getNode();
        if (node.findChildByType(nl.akiar.pascal.PascalTokenTypes.KW_CONST) != null) return 1;
        if (node.findChildByType(nl.akiar.pascal.PascalTokenTypes.KW_VAR) != null) return 2;
        if (node.findChildByType(nl.akiar.pascal.PascalTokenTypes.KW_OUT) != null) return 3;
        return 0;
    }

    @Override
    public boolean isMethod() {
        return getContainingClass() != null;
    }

    @Override
    @Nullable
    public PascalTypeDefinition getContainingClass() {
        // Case 1: Declaration inside a type definition (stub-safe parent walk)
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent instanceof PascalTypeDefinition) {
                return (PascalTypeDefinition) parent;
            }
            // Stop at routine boundary if we somehow ended up inside another routine
            if (parent instanceof PascalRoutine && parent != this) {
                break;
            }
            parent = parent.getParent();
        }

        // Case 2: Implementation: consult counterpart declaration only (stub-safe)
        if (isImplementation()) {
            PascalRoutine declaration = getDeclaration();
            if (declaration != null) {
                return declaration.getContainingClass();
            }
            // Avoid AST-based fallback scanning to prevent stub/AST mismatch
            return null;
        }

        return null;
    }

    @Override
    @Nullable
    public String getVisibility() {
        if (!isMethod()) return null;
        return nl.akiar.pascal.psi.PsiUtil.getVisibility(this);
    }

    @Override
    @Nullable
    public String getDocComment() {
        // Look for comments immediately preceding this routine
        StringBuilder docBuilder = new StringBuilder();
        PsiElement prev = getPrevSibling();

        while (prev != null) {
            IElementType type = prev.getNode().getElementType();
            if (type == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                String text = prev.getText();
                long newlines = text.chars().filter(c -> c == '\n').count();
                if (newlines > 1) {
                    break;
                }
                prev = prev.getPrevSibling();
                continue;
            }
            if (type == nl.akiar.pascal.PascalTokenTypes.BLOCK_COMMENT || type == nl.akiar.pascal.PascalTokenTypes.LINE_COMMENT) {
                String commentText = extractCommentContent(prev.getText(), type);
                if (docBuilder.length() > 0) {
                    docBuilder.insert(0, "\n");
                }
                docBuilder.insert(0, commentText);
                prev = prev.getPrevSibling();
            } else {
                break;
            }
        }

        String result = docBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String extractCommentContent(String comment, IElementType type) {
        if (type == nl.akiar.pascal.PascalTokenTypes.LINE_COMMENT) {
            if (comment.startsWith("//")) {
                return comment.substring(2).trim();
            }
        } else if (type == nl.akiar.pascal.PascalTokenTypes.BLOCK_COMMENT) {
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
    public String getUnitName() {
        return nl.akiar.pascal.psi.PsiUtil.getUnitName(this);
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public String getReturnTypeName() {
        // Stub-first approach: get from stub if available
        nl.akiar.pascal.stubs.PascalRoutineStub stub = getGreenStub();
        if (stub != null) {
            return stub.getReturnTypeName();
        }

        // Fall back to AST parsing
        return extractReturnTypeNameFromAST();
    }

    /**
     * Extract return type name from AST when stub is not available.
     * Functions have the syntax: function Name(...): ReturnType;
     */
    @org.jetbrains.annotations.Nullable
    private String extractReturnTypeNameFromAST() {
        com.intellij.lang.ASTNode node = getNode();
        if (node == null) return null;

        // Find the colon after the parameter list, then find the identifier after the colon
        com.intellij.lang.ASTNode child = node.getFirstChildNode();
        boolean foundColon = false;

        while (child != null) {
            com.intellij.psi.tree.IElementType type = child.getElementType();

            // Look for COLON
            if (type == nl.akiar.pascal.PascalTokenTypes.COLON) {
                foundColon = true;
                child = child.getTreeNext();
                continue;
            }

            // After colon, look for the type identifier
            if (foundColon) {
                // Skip whitespace
                if (type == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                    child = child.getTreeNext();
                    continue;
                }

                // Found the return type identifier
                if (type == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                    StringBuilder typeName = new StringBuilder(child.getText());
                    // Handle qualified names like System.TObject
                    child = child.getTreeNext();
                    while (child != null) {
                        com.intellij.psi.tree.IElementType nextType = child.getElementType();
                        if (nextType == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                            child = child.getTreeNext();
                            continue;
                        }
                        if (nextType == nl.akiar.pascal.PascalTokenTypes.DOT) {
                            typeName.append(".");
                            child = child.getTreeNext();
                        } else if (nextType == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                            typeName.append(child.getText());
                            child = child.getTreeNext();
                        } else {
                            break;
                        }
                    }
                    return typeName.toString();
                }

                // If we hit semicolon or other tokens, stop
                if (type == nl.akiar.pascal.PascalTokenTypes.SEMI) {
                    break;
                }
            }

            child = child.getTreeNext();
        }

        return null;
    }

    @Override
    public String toString() {
        return "PascalRoutine(" + getName() + ")";
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public String getContainingClassName() {
        nl.akiar.pascal.stubs.PascalRoutineStub stub = getGreenStub();
        if (stub != null) return stub.getContainingClassName();
        // Fallback: if we have an interface declaration inside a type, use the type's name
        nl.akiar.pascal.psi.PascalTypeDefinition td = getContainingClass();
        return td != null ? td.getName() : null;
    }

    @Override
    @NotNull
    public List<PascalAttribute> getAttributes() {
        // Attributes can be either:
        // 1. Direct children in an ATTRIBUTE_LIST (synthesized by PascalSonarParser when
        //    sonar-delphi doesn't create AttributeListNode)
        // 2. Preceding siblings (for methods in visibility sections where sonar-delphi
        //    properly creates AttributeListNode)
        //
        // First, look for DIRECT child ATTRIBUTE_LIST elements
        List<PascalAttribute> directAttrs = new ArrayList<>();
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == PascalElementTypes.ATTRIBUTE_LIST) {
                // Found an attribute list, collect its attributes
                directAttrs.addAll(PsiTreeUtil.findChildrenOfType(child, PascalAttribute.class));
            }
            // Stop at routine keywords - anything after is the routine itself
            IElementType type = child.getNode().getElementType();
            if (type == PascalTokenTypes.KW_PROCEDURE || type == PascalTokenTypes.KW_FUNCTION ||
                type == PascalTokenTypes.KW_CONSTRUCTOR || type == PascalTokenTypes.KW_DESTRUCTOR) {
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
