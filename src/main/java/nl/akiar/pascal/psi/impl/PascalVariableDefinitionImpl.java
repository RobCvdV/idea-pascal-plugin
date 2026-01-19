package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;
import nl.akiar.pascal.stubs.PascalVariableStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of PascalVariableDefinition PSI element.
 */
public class PascalVariableDefinitionImpl extends StubBasedPsiElementBase<PascalVariableStub>
        implements PascalVariableDefinition {

    public PascalVariableDefinitionImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PascalVariableDefinitionImpl(@NotNull PascalVariableStub stub, @NotNull IStubElementType<?, ?> nodeType) {
        super(stub, nodeType);
    }

    @Override
    @Nullable
    public String getName() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getName();
        }

        // Parse from AST - first IDENTIFIER is the name
        PsiElement nameElement = getNameIdentifier();
        if (nameElement != null) {
            return nameElement.getText();
        }
        return null;
    }

    @Override
    @Nullable
    public String getTypeName() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getTypeName();
        }

        // Parse from AST: look for IDENTIFIER after COLON
        ASTNode node = getNode();
        boolean foundColon = false;

        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();

            if (type == PascalTokenTypes.COLON) {
                foundColon = true;
            } else if (foundColon) {
                // Skip whitespace and comments
                if (type == PascalTokenTypes.WHITE_SPACE ||
                    type == PascalTokenTypes.LINE_COMMENT ||
                    type == PascalTokenTypes.BLOCK_COMMENT) {
                    continue;
                }

                // Found the type - could be simple identifier or qualified name
                if (type == PascalTokenTypes.IDENTIFIER ||
                    type == PascalTokenTypes.KW_STRING ||
                    type == PascalTokenTypes.KW_ARRAY ||
                    type == PascalTokenTypes.KW_SET ||
                    type == PascalTokenTypes.KW_FILE) {
                    // Build the full type name (might be qualified like System.Integer)
                    StringBuilder typeName = new StringBuilder(child.getText());
                    ASTNode next = child.getTreeNext();

                    // Handle qualified names (Type.SubType) and generics (TList<T>)
                    while (next != null) {
                        IElementType nextType = next.getElementType();
                        if (nextType == PascalTokenTypes.DOT) {
                            typeName.append(".");
                            next = next.getTreeNext();
                            if (next != null && next.getElementType() == PascalTokenTypes.IDENTIFIER) {
                                typeName.append(next.getText());
                                next = next.getTreeNext();
                            }
                        } else if (nextType == PascalTokenTypes.LT) {
                            // Generic type parameters - include them in type name
                            int depth = 1;
                            typeName.append("<");
                            next = next.getTreeNext();
                            while (next != null && depth > 0) {
                                if (next.getElementType() == PascalTokenTypes.LT) depth++;
                                else if (next.getElementType() == PascalTokenTypes.GT) depth--;
                                typeName.append(next.getText());
                                next = next.getTreeNext();
                            }
                        } else if (nextType == PascalTokenTypes.WHITE_SPACE) {
                            next = next.getTreeNext();
                        } else {
                            break;
                        }
                    }
                    return typeName.toString();
                }
            }
        }
        return null;
    }

    @Override
    @NotNull
    public VariableKind getVariableKind() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getVariableKind();
        }

        // Determine kind from context by examining preceding tokens/keywords
        return determineVariableKindFromContext();
    }

    /**
     * Determine the variable kind by examining the context in the file.
     * Uses text-based analysis to properly track nesting levels.
     */
    private VariableKind determineVariableKindFromContext() {
        // Get the file text up to this element
        String fileText = getContainingFile().getText();
        int elementOffset = getTextOffset();
        if (elementOffset > fileText.length()) {
            return VariableKind.UNKNOWN;
        }

        String textBefore = fileText.substring(0, elementOffset).toLowerCase();

        // Track nesting levels
        int classRecordDepth = 0;
        int procedureFunctionDepth = 0;
        String lastSectionKeyword = null;
        String lastVisibility = null;

        // Tokenize and analyze the text before this variable
        // We need to find:
        // 1. What section keyword (var/const/threadvar) precedes us
        // 2. Are we inside a class/record body?
        // 3. Are we inside a procedure/function body?

        java.util.regex.Pattern tokenPattern = java.util.regex.Pattern.compile(
            "\\b(class|record|interface|object|procedure|function|constructor|destructor|" +
            "var|const|threadvar|type|begin|end|private|protected|public|published|strict|" +
            "implementation|interface)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher matcher = tokenPattern.matcher(textBefore);
        java.util.List<String> tokens = new java.util.ArrayList<>();

        while (matcher.find()) {
            tokens.add(matcher.group(1).toLowerCase());
        }

        // Process tokens to understand context
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            switch (token) {
                case "class":
                case "record":
                case "object":
                    // Check if this is a type definition (not a class method/variable prefix)
                    // Class/record definitions increase depth, but "class procedure" doesn't
                    if (i + 1 < tokens.size()) {
                        String next = tokens.get(i + 1);
                        if (next.equals("procedure") || next.equals("function") ||
                            next.equals("var") || next.equals("const")) {
                            // This is "class procedure" or similar, don't increase depth
                            continue;
                        }
                    }
                    classRecordDepth++;
                    lastSectionKeyword = null; // Reset section inside new class
                    lastVisibility = null;
                    break;

                case "interface":
                    // "interface" at unit level is different from interface type
                    // Unit-level interface resets everything
                    if (classRecordDepth == 0 && procedureFunctionDepth == 0) {
                        lastSectionKeyword = null;
                    } else {
                        classRecordDepth++;
                        lastSectionKeyword = null;
                    }
                    break;

                case "implementation":
                    // Unit implementation section - reset
                    if (classRecordDepth == 0) {
                        lastSectionKeyword = null;
                    }
                    break;

                case "procedure":
                case "function":
                case "constructor":
                case "destructor":
                    // Only increase depth if followed by begin (has a body)
                    // For now, just track that we saw one
                    if (classRecordDepth == 0) {
                        procedureFunctionDepth++;
                    }
                    break;

                case "begin":
                    // Begin increases procedure depth (nested begin..end)
                    if (procedureFunctionDepth > 0) {
                        // Already in a procedure, this is nested
                    } else if (classRecordDepth == 0) {
                        // Top-level begin (main program)
                        procedureFunctionDepth++;
                    }
                    break;

                case "end":
                    // End decreases depth
                    if (classRecordDepth > 0) {
                        classRecordDepth--;
                        if (classRecordDepth == 0) {
                            lastSectionKeyword = null;
                            lastVisibility = null;
                        }
                    } else if (procedureFunctionDepth > 0) {
                        procedureFunctionDepth--;
                        lastSectionKeyword = null;
                    }
                    break;

                case "var":
                    lastSectionKeyword = "var";
                    break;

                case "const":
                    lastSectionKeyword = "const";
                    break;

                case "threadvar":
                    lastSectionKeyword = "threadvar";
                    break;

                case "type":
                    lastSectionKeyword = "type";
                    break;

                case "private":
                case "protected":
                case "public":
                case "published":
                    if (classRecordDepth > 0) {
                        lastVisibility = token;
                        lastSectionKeyword = null; // Visibility resets section
                    }
                    break;

                case "strict":
                    // "strict private" or "strict protected"
                    break;
            }
        }

        // Now determine the kind based on context
        if ("threadvar".equals(lastSectionKeyword)) {
            return VariableKind.THREADVAR;
        }

        if ("const".equals(lastSectionKeyword)) {
            if (classRecordDepth > 0) {
                return VariableKind.FIELD; // Class constant is still a field
            }
            return VariableKind.CONSTANT;
        }

        if ("var".equals(lastSectionKeyword)) {
            if (classRecordDepth > 0) {
                return VariableKind.FIELD;
            }
            if (procedureFunctionDepth > 0) {
                return VariableKind.LOCAL;
            }
            return VariableKind.GLOBAL;
        }

        // If we're in a class but no var/const section, might be a field
        if (classRecordDepth > 0 && lastVisibility != null) {
            return VariableKind.FIELD;
        }

        return VariableKind.UNKNOWN;
    }

    /**
     * Get the visibility modifier for this variable (private, protected, public, published).
     */
    @Nullable
    public String getVisibility() {
        String fileText = getContainingFile().getText();
        int elementOffset = getTextOffset();
        if (elementOffset > fileText.length()) {
            return null;
        }

        String textBefore = fileText.substring(0, elementOffset).toLowerCase();

        // Find the last visibility keyword before this variable within a class
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\b(private|protected|public|published|strict\\s+private|strict\\s+protected)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        String lastVisibility = null;
        java.util.regex.Matcher matcher = pattern.matcher(textBefore);

        // Find the last class/record start
        int lastClassStart = Math.max(
            textBefore.lastIndexOf(" class"),
            Math.max(textBefore.lastIndexOf("\nclass"),
            Math.max(textBefore.lastIndexOf("=class"),
            textBefore.lastIndexOf("= class")))
        );

        int searchFrom = Math.max(0, lastClassStart);

        while (matcher.find(searchFrom)) {
            lastVisibility = matcher.group(1).toLowerCase().replace("\\s+", " ");
            searchFrom = matcher.end();
        }

        return lastVisibility;
    }

    @Override
    @Nullable
    public String getContainingScopeName() {
        PascalVariableStub stub = getGreenStub();
        if (stub != null) {
            return stub.getContainingScopeName();
        }

        // Determine the containing scope by analyzing the file text
        return determineContainingScopeName();
    }

    /**
     * Determine the containing scope name (class name, function name, etc.)
     */
    @Nullable
    private String determineContainingScopeName() {
        VariableKind kind = getVariableKind();
        String fileText = getContainingFile().getText();
        int elementOffset = getTextOffset();
        if (elementOffset > fileText.length()) {
            return null;
        }

        String textBefore = fileText.substring(0, elementOffset);

        switch (kind) {
            case FIELD:
                // Find the class/record name
                return findContainingClassName(textBefore);

            case LOCAL:
                // Find the procedure/function name
                return findContainingFunctionName(textBefore);

            case GLOBAL:
            case CONSTANT:
            case THREADVAR:
                // Return the unit name
                return findUnitName(fileText);

            default:
                return null;
        }
    }

    /**
     * Find the class or record name that contains this variable.
     */
    @Nullable
    private String findContainingClassName(String textBefore) {
        // Pattern to find type definitions: TypeName = class/record
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?:<[^>]*>)?\\s*=\\s*(?:packed\\s+)?(?:class|record|object|interface)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        String lastClassName = null;
        java.util.regex.Matcher matcher = pattern.matcher(textBefore);

        // Track end keywords to know when we exit a class
        int classDepth = 0;
        int lastClassEnd = -1;

        // Simple approach: find the last class definition before this position
        // and check if we're still inside it (no matching 'end')
        while (matcher.find()) {
            lastClassName = matcher.group(1);
        }

        return lastClassName;
    }

    /**
     * Find the procedure or function name that contains this variable.
     */
    @Nullable
    private String findContainingFunctionName(String textBefore) {
        // Pattern to find function/procedure definitions
        // Handles: procedure Name, function Name, constructor Name.Create, etc.
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\b(?:procedure|function|constructor|destructor)\\s+(?:([A-Za-z_][A-Za-z0-9_]*)\\.)?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\(|;|:)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        String lastFunctionName = null;
        String lastClassName = null;
        java.util.regex.Matcher matcher = pattern.matcher(textBefore);

        while (matcher.find()) {
            lastClassName = matcher.group(1); // May be null for standalone functions
            lastFunctionName = matcher.group(2);
        }

        if (lastFunctionName != null) {
            if (lastClassName != null) {
                return lastClassName + "." + lastFunctionName;
            }
            return lastFunctionName;
        }

        return null;
    }

    /**
     * Find the unit name from the file.
     */
    @Nullable
    private String findUnitName(String fileText) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\bunit\\s+([A-Za-z_][A-Za-z0-9_.]*)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher matcher = pattern.matcher(fileText);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fall back to file name without extension
        String fileName = getContainingFile().getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    /**
     * Get the containing class name for fields.
     */
    @Nullable
    public String getContainingClassName() {
        if (getVariableKind() == VariableKind.FIELD) {
            String fileText = getContainingFile().getText();
            int elementOffset = getTextOffset();
            if (elementOffset <= fileText.length()) {
                return findContainingClassName(fileText.substring(0, elementOffset));
            }
        }
        return null;
    }

    /**
     * Get the containing function name for local variables.
     */
    @Nullable
    public String getContainingFunctionName() {
        if (getVariableKind() == VariableKind.LOCAL) {
            String fileText = getContainingFile().getText();
            int elementOffset = getTextOffset();
            if (elementOffset <= fileText.length()) {
                return findContainingFunctionName(fileText.substring(0, elementOffset));
            }
        }
        return null;
    }

    /**
     * Get the unit name for this variable's file.
     */
    @NotNull
    public String getUnitName() {
        return findUnitName(getContainingFile().getText());
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // The first IDENTIFIER token in the node is the variable name
        ASTNode node = getNode();
        ASTNode identifierNode = node.findChildByType(PascalTokenTypes.IDENTIFIER);
        if (identifierNode != null) {
            return identifierNode.getPsi();
        }
        return null;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Renaming not supported yet");
    }

    @Override
    @Nullable
    public String getDocComment() {
        // Look for comments immediately preceding this variable definition
        StringBuilder docBuilder = new StringBuilder();
        PsiElement prev = getPrevSibling();

        while (prev != null) {
            IElementType type = prev.getNode().getElementType();
            if (type == PascalTokenTypes.WHITE_SPACE) {
                String text = prev.getText();
                long newlines = text.chars().filter(c -> c == '\n').count();
                if (newlines > 1) {
                    break;
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
                break;
            }
        }

        String result = docBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String extractCommentContent(String comment, IElementType type) {
        if (type == PascalTokenTypes.LINE_COMMENT) {
            if (comment.startsWith("//")) {
                return comment.substring(2).trim();
            }
        } else if (type == PascalTokenTypes.BLOCK_COMMENT) {
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
    public String getDeclarationText() {
        // Build the declaration text from the node
        ASTNode node = getNode();
        StringBuilder sb = new StringBuilder();
        ASTNode child = node.getFirstChildNode();

        while (child != null) {
            IElementType type = child.getElementType();

            // Stop at semicolon or end
            if (type == PascalTokenTypes.SEMI) {
                break;
            }

            sb.append(child.getText());
            child = child.getTreeNext();
        }

        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return "PascalVariableDefinition(" + getName() + ": " + getTypeName() + ", " + getVariableKind() + ")";
    }
}
