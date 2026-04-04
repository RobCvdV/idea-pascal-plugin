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
import nl.akiar.pascal.psi.VariableKind;
import nl.akiar.pascal.stubs.PascalRoutineStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PascalRoutineImpl extends StubBasedPsiElementBase<PascalRoutineStub> implements PascalRoutine {
    private static final ThreadLocal<Set<String>> DECL_IMPL_VISITED = ThreadLocal.withInitial(HashSet::new);

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
        return nameId != null ? nl.akiar.pascal.psi.PsiUtil.stripEscapePrefix(nameId.getText()) : null;
    }

    @Override
    public int getTextOffset() {
        PsiElement nameId = getNameIdentifier();
        return nameId != null ? nameId.getTextRange().getStartOffset() : super.getTextOffset();
    }

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

        // Find the method name: the last identifier-like token after the routine keyword,
        // before any structural boundary (LT, LPAREN, COLON, SEMI, FORMAL_PARAMETER_LIST, etc.).
        // Handles qualified names like TClass.Method (keeps the last identifier)
        // and soft keywords used as method names (e.g. "Register" tokenized as KW_REGISTER).
        // For qualified names, the parser may wrap parts in composite nodes
        // (CLASS_TYPE_REFERENCE, METHOD_NAME_REFERENCE) — we descend into those.
        ASTNode bestId = findNameInChildren(node, routineKeywordOffset);
        return bestId != null ? bestId.getPsi() : null;
    }

    /**
     * Scan children of the given node for the last identifier-like token after minOffset,
     * stopping at structural boundaries. Descends into composite nodes like
     * CLASS_TYPE_REFERENCE and METHOD_NAME_REFERENCE which wrap parts of qualified names.
     *
     * In Delphi, many keywords can be used as method names (soft keywords like Register,
     * Name, Index, etc.), so we accept any token that could be part of a name. We stop
     * at tokens that definitely mark the end of the name region (BEGIN, SEMI, COLON, etc.).
     */
    @Nullable
    private ASTNode findNameInChildren(ASTNode parent, int minOffset) {
        ASTNode bestId = null;
        for (ASTNode child = parent.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getStartOffset() < minOffset) continue;

            IElementType type = child.getElementType();

            // Stop at structural boundaries that mark end of routine name region
            if (type == PascalTokenTypes.LPAREN || type == PascalTokenTypes.LT ||
                type == PascalTokenTypes.COLON || type == PascalTokenTypes.SEMI ||
                type == PascalTokenTypes.KW_BEGIN || type == PascalTokenTypes.KW_END ||
                type == PascalTokenTypes.KW_VAR || type == PascalTokenTypes.KW_CONST ||
                type == nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER_LIST ||
                type == nl.akiar.pascal.psi.PascalElementTypes.RETURN_TYPE ||
                type == nl.akiar.pascal.psi.PascalElementTypes.GENERIC_PARAMETER) {
                break;
            }

            // Skip whitespace and dots
            if (type == PascalTokenTypes.WHITE_SPACE || type == PascalTokenTypes.DOT) continue;

            // Descend into composite nodes that may contain name identifiers
            // (CLASS_TYPE_REFERENCE wraps the class part, METHOD_NAME_REFERENCE wraps the method name,
            //  NAME_REFERENCE wraps identifiers in some contexts)
            if (type == nl.akiar.pascal.psi.PascalElementTypes.CLASS_TYPE_REFERENCE ||
                type == nl.akiar.pascal.psi.PascalElementTypes.METHOD_NAME_REFERENCE ||
                type == nl.akiar.pascal.psi.PascalElementTypes.NAME_REFERENCE ||
                type == nl.akiar.pascal.psi.PascalElementTypes.TYPE_REFERENCE) {
                ASTNode innerBest = findNameInChildren(child, minOffset);
                if (innerBest != null) {
                    bestId = innerBest;
                }
                continue;
            }

            // Accept any leaf token as a potential name part. In Delphi, many keywords
            // can be used as routine names (Register, Name, Read, Write, etc.) and the
            // sonar-delphi parser tokenizes them with their keyword types (KW_REGISTER etc.).
            // For qualified names (TClass.Method), keep the last one.
            if (child.getFirstChildNode() == null) {
                // It's a leaf token — treat it as a potential name
                bestId = child;
            }
            // Non-leaf composite nodes that aren't in our known list — skip them
        }
        return bestId;
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
            // Skip identifiers inside return type
            if (nl.akiar.pascal.psi.PsiUtil.hasParent(psi, nl.akiar.pascal.psi.PascalElementTypes.RETURN_TYPE)) {
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
            ASTNode node = parent.getNode();
            if (node == null) {
                parent = parent.getParent();
                continue;
            }
            if (node.getElementType() == PascalElementTypes.IMPLEMENTATION_SECTION) return true;
            if (node.getElementType() == PascalElementTypes.INTERFACE_SECTION) return false;
            parent = parent.getParent();
        }
        return false;
    }

    private static List<PascalRoutine> filterByClass(List<PascalRoutine> routines, @Nullable String className, @Nullable String unitName) {
        if (routines.isEmpty()) return routines;
        List<PascalRoutine> filtered = new ArrayList<>();
        for (PascalRoutine r : routines) {
            // Skip routines from different units when unit is specified
            if (unitName != null && !unitName.equalsIgnoreCase(r.getUnitName())) continue;
            if (className == null) {
                // For global routines (no class), only include routines that also have no class
                if (r.getContainingClassName() == null) filtered.add(r);
            } else {
                if (className.equalsIgnoreCase(r.getContainingClassName())) filtered.add(r);
            }
        }
        return filtered;
    }

    @Override
    @Nullable
    public PascalRoutine getDeclaration() {
        if (!isImplementation()) return this;
        String name = getName();
        String keySig = getUnitName() + "#" + normalize(getContainingClassName()) + "#" + normalize(name) + "#" + normalize(getSignatureHash());
        Set<String> visited = DECL_IMPL_VISITED.get();
        if (visited.contains(keySig)) return null;
        visited.add(keySig);
        try {
            String unit = getUnitName();
            String owner = getContainingClassName();
            if (name == null) return null;

            // 1. Scoped index: exact match with signature
            if (unit != null && owner != null) {
                String key = (unit + "#" + owner + "#" + name).toLowerCase();
                java.util.Collection<nl.akiar.pascal.psi.PascalRoutine> candidates = nl.akiar.pascal.stubs.PascalScopedRoutineIndex.find(key, getProject());
                String sig = getSignatureHash();
                // First try exact signature match
                for (nl.akiar.pascal.psi.PascalRoutine r : candidates) {
                    if (!r.isImplementation()) {
                        String otherSig = ((PascalRoutineImpl) r).getSignatureHash();
                        if (normalize(sig).equals(normalize(otherSig))) {
                            return r;
                        }
                    }
                }
                // If no exact signature match, try matchesSignature as secondary check
                nl.akiar.pascal.psi.PascalRoutine declSigMatch = null;
                int declCount = 0;
                nl.akiar.pascal.psi.PascalRoutine singleDecl = null;
                for (nl.akiar.pascal.psi.PascalRoutine r : candidates) {
                    if (!r.isImplementation()) {
                        declCount++;
                        singleDecl = r;
                        if (matchesSignature(r)) {
                            declSigMatch = r;
                        }
                    }
                }
                if (declSigMatch != null) return declSigMatch;
                // Only accept without signature match if exactly 1 declaration candidate
                if (declCount == 1) return singleDecl;
            }

            // 2. Routine index fallback: filter by class
            com.intellij.psi.PsiFile file = getContainingFile();
            nl.akiar.pascal.stubs.PascalRoutineIndex.RoutineLookupResult result = nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutinesWithUsesValidation(name, file, getTextOffset());
            java.util.List<nl.akiar.pascal.psi.PascalRoutine> inScope = filterByClass(result.getInScopeRoutines(), owner, unit);
            for (nl.akiar.pascal.psi.PascalRoutine r : inScope) {
                if (!r.isImplementation()) return r;
            }
            return null;
        } finally {
            visited.remove(keySig);
        }
    }

    @Override
    @Nullable
    public PascalRoutine getImplementation() {
        if (isImplementation()) return this;
        String name = getName();
        String keySig = getUnitName() + "#" + normalize(getContainingClassName()) + "#" + normalize(name) + "#" + normalize(getSignatureHash()) + "#impl";
        Set<String> visited = DECL_IMPL_VISITED.get();
        if (visited.contains(keySig)) return null;
        visited.add(keySig);
        try {
            String unit = getUnitName();
            String owner = getContainingClassName();
            if (name == null) return null;

            // 1. Scoped index: exact match with signature
            if (unit != null && owner != null) {
                String key = (unit + "#" + owner + "#" + name).toLowerCase();
                java.util.Collection<nl.akiar.pascal.psi.PascalRoutine> candidates = nl.akiar.pascal.stubs.PascalScopedRoutineIndex.find(key, getProject());
                String sig = getSignatureHash();
                // First try exact signature match
                for (nl.akiar.pascal.psi.PascalRoutine r : candidates) {
                    if (r.isImplementation()) {
                        String otherSig = ((PascalRoutineImpl) r).getSignatureHash();
                        if (normalize(sig).equals(normalize(otherSig))) {
                            return r;
                        }
                    }
                }
                // If no exact signature match, try matchesSignature as secondary check
                nl.akiar.pascal.psi.PascalRoutine sigMatch = null;
                int implCount = 0;
                nl.akiar.pascal.psi.PascalRoutine singleImpl = null;
                for (nl.akiar.pascal.psi.PascalRoutine r : candidates) {
                    if (r.isImplementation()) {
                        implCount++;
                        singleImpl = r;
                        if (matchesSignature(r)) {
                            sigMatch = r;
                        }
                    }
                }
                if (sigMatch != null) return sigMatch;
                // Only accept without signature match if exactly 1 implementation candidate
                if (implCount == 1) return singleImpl;
            }

            // 2. Routine index fallback: filter by class
            com.intellij.psi.PsiFile file = getContainingFile();
            nl.akiar.pascal.stubs.PascalRoutineIndex.RoutineLookupResult result = nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutinesWithUsesValidation(name, file, getTextOffset());
            java.util.List<nl.akiar.pascal.psi.PascalRoutine> inScope = filterByClass(result.getInScopeRoutines(), owner, unit);
            for (nl.akiar.pascal.psi.PascalRoutine r : inScope) {
                if (r.isImplementation()) return r;
            }
            return null;
        } finally {
            visited.remove(keySig);
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    @Override
    @NotNull
    public String getUnitName() {
        PascalRoutineStub stub = getGreenStub();
        if (stub != null && stub.getUnitName() != null) {
            return stub.getUnitName().toLowerCase(); // Normalize to lowercase
        }
        PsiFile file = getContainingFile();
        String base = file.getName();
        int dot = base.lastIndexOf('.');
        String unitName = dot > 0 ? base.substring(0, dot) : base;
        return unitName.toLowerCase();
    }

    public String getSignatureHash() {
        PascalRoutineStub stub = getGreenStub();
        if (stub != null && stub.getSignatureHash() != null) return stub.getSignatureHash();
        StringBuilder sb = new StringBuilder();
        // Only consider parameters from THIS routine's own FORMAL_PARAMETER_LIST,
        // not parameters from nested routines/lambdas inside the implementation body.
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == PascalElementTypes.FORMAL_PARAMETER_LIST) {
                for (PascalVariableDefinition p : PsiTreeUtil.findChildrenOfType(child, PascalVariableDefinition.class)) {
                    if (p.getVariableKind() == VariableKind.PARAMETER) {
                        String tn = p.getTypeName();
                        if (tn != null) sb.append(tn.toLowerCase()).append(";");
                    }
                }
                break; // only the first (own) parameter list
            }
        }
        return sb.toString();
    }

    private boolean matchesSignature(PascalRoutine other) {
        String name = getName();
        String otherName = other.getName();
        if (name == null || otherName == null || !name.equalsIgnoreCase(otherName)) return false;

        // Compare only own formal parameters (not nested routine params)
        java.util.List<PascalVariableDefinition> myFormalParams = getOwnParameters();
        java.util.List<PascalVariableDefinition> otherFormalParams =
                (other instanceof PascalRoutineImpl ri) ? ri.getOwnParameters() :
                PsiTreeUtil.findChildrenOfType(other, PascalVariableDefinition.class).stream()
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

    /**
     * Get only this routine's own formal parameters (from its direct FORMAL_PARAMETER_LIST),
     * excluding parameters from nested routines/lambdas in the implementation body.
     */
    @NotNull
    List<PascalVariableDefinition> getOwnParameters() {
        List<PascalVariableDefinition> params = new ArrayList<>();
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == PascalElementTypes.FORMAL_PARAMETER_LIST) {
                for (PascalVariableDefinition p : PsiTreeUtil.findChildrenOfType(child, PascalVariableDefinition.class)) {
                    if (p.getVariableKind() == VariableKind.PARAMETER) {
                        params.add(p);
                    }
                }
                break;
            }
        }
        return params;
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
            if (comment.startsWith("///")) {
                return comment.substring(3).trim();
            } else if (comment.startsWith("//")) {
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

        // First check for RETURN_TYPE composite node (created by parser)
        com.intellij.lang.ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.RETURN_TYPE) {
                com.intellij.psi.PsiElement psi = child.getPsi();
                if (psi instanceof nl.akiar.pascal.psi.PascalReturnType) {
                    return ((nl.akiar.pascal.psi.PascalReturnType) psi).getTypeName();
                }
            }
            child = child.getTreeNext();
        }

        // Fallback: find the colon after the parameter list, then find the identifier after the colon
        child = node.getFirstChildNode();
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
                    // Handle qualified names like System.TObject and generics like IPromise<TEntityList<TMutation>>
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
                        } else if (nextType == nl.akiar.pascal.PascalTokenTypes.LT) {
                            // Collect the full generic argument list: <Type1, Type2<Nested>>
                            typeName.append("<");
                            int depth = 1;
                            child = child.getTreeNext();
                            while (child != null && depth > 0) {
                                nextType = child.getElementType();
                                if (nextType == nl.akiar.pascal.PascalTokenTypes.LT) { depth++; typeName.append("<"); }
                                else if (nextType == nl.akiar.pascal.PascalTokenTypes.GT) { depth--; typeName.append(">"); }
                                else if (nextType == nl.akiar.pascal.PascalTokenTypes.COMMA) { typeName.append(", "); }
                                else if (nextType == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) { typeName.append(child.getText()); }
                                else if (nextType == nl.akiar.pascal.PascalTokenTypes.DOT) { typeName.append("."); }
                                // Skip whitespace and TYPE_REFERENCE composites (their children are handled individually)
                                if (depth > 0) child = child.getTreeNext();
                            }
                            // After closing >, continue to check for more DOT-qualified parts
                            if (child != null) child = child.getTreeNext();
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
    public com.intellij.navigation.ItemPresentation getPresentation() {
        return new com.intellij.navigation.ItemPresentation() {
            @Override
            public String getPresentableText() {
                StringBuilder sb = new StringBuilder();
                String className = getContainingClassName();
                if (className != null) {
                    sb.append(className).append(".");
                }
                String name = getName();
                sb.append(name != null ? name : "?");
                // Append parameter signature for overload distinction
                String sig = buildParameterSignature();
                if (sig != null) {
                    sb.append(sig);
                }
                return sb.toString();
            }

            @Override
            public String getLocationString() {
                return getContainingFile().getName();
            }

            @Override
            public javax.swing.Icon getIcon(boolean unused) {
                return PascalRoutineImpl.this.getIcon(0);
            }
        };
    }

    /**
     * Build a human-readable parameter signature like "(AOperator: TOperatorId; AActive: Boolean)".
     * Only considers this routine's own formal parameters, not those in nested routines.
     */
    @Nullable
    private String buildParameterSignature() {
        java.util.List<String> params = new java.util.ArrayList<>();
        for (PascalVariableDefinition v : getOwnParameters()) {
            String pName = v.getName();
            String pType = v.getTypeName();
            if (pName != null && pType != null) {
                params.add(pName + ": " + pType);
            } else if (pName != null) {
                params.add(pName);
            }
        }
        if (params.isEmpty()) return "()";
        return "(" + String.join("; ", params) + ")";
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public String getContainingClassName() {
        nl.akiar.pascal.stubs.PascalRoutineStub stub = getGreenStub();
        if (stub != null) return stub.getContainingClassName();

        // Fallback: traverse parent chain to find containing type definition (local AST only - no getContainingClass() call!)
        PsiElement parent = getParent();
        while (parent != null) {
            if (parent instanceof nl.akiar.pascal.psi.PascalTypeDefinition) {
                return ((nl.akiar.pascal.psi.PascalTypeDefinition) parent).getName();
            }
            parent = parent.getParent();
        }

        // Check for CLASS_TYPE_REFERENCE child (implementation methods like TClass.Method or TClass<T>.Method)
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == PascalElementTypes.CLASS_TYPE_REFERENCE) {
                String refText = child.getText().trim();
                // Strip generic parameters: "THelper<T>" -> "THelper"
                int ltIdx = refText.indexOf('<');
                return ltIdx > 0 ? refText.substring(0, ltIdx) : refText;
            }
        }

        // Fallback for implementation methods: extract from qualified name (TClass.Method pattern)
        // Use prevLeaf to handle cases where IDENTIFIER/DOT may be inside composite nodes
        PsiElement nameId = getNameIdentifier();
        if (nameId != null) {
            PsiElement prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(nameId);
            while (prev != null && prev instanceof com.intellij.psi.PsiWhiteSpace) {
                prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev);
            }
            if (prev != null && prev.getNode().getElementType() == PascalTokenTypes.DOT) {
                prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev);
                while (prev != null && prev instanceof com.intellij.psi.PsiWhiteSpace) {
                    prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev);
                }
                if (prev != null && prev.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
                    return nl.akiar.pascal.psi.PsiUtil.stripEscapePrefix(prev.getText());
                }
            }
        }
        return null;
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
