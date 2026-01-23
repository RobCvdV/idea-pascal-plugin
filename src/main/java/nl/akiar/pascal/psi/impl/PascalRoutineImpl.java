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
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.stubs.PascalRoutineStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        // For qualified names TClass.Method, we want the last identifier before the parameters/body starts.
        // We must avoid identifiers inside parameters or the return type.
        
        ASTNode node = getNode();
        List<ASTNode> allIds = nl.akiar.pascal.psi.PsiUtil.findAllRecursive(node, nl.akiar.pascal.PascalTokenTypes.IDENTIFIER);
        
        ASTNode bestId = null;
        for (ASTNode idNode : allIds) {
            PsiElement psi = idNode.getPsi();
            // Skip identifiers inside parameters
            if (nl.akiar.pascal.psi.PsiUtil.hasParent(psi, nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER)) {
                continue;
            }
            
            // In Delphi, the routine name comes before '(' or ';' or ':' (for functions)
            // But with qualified names, we might have multiple identifiers (TClass . Method)
            // We want the last identifier that is part of the routine name itself.
            bestId = idNode;
            
            // If we hit any of these, we are past the routine name
            ASTNode next = idNode.getTreeNext();
            while (next != null && next.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                next = next.getTreeNext();
            }
            if (next != null) {
                IElementType type = next.getElementType();
                if (type == nl.akiar.pascal.PascalTokenTypes.LPAREN || 
                    type == nl.akiar.pascal.PascalTokenTypes.SEMI || 
                    type == nl.akiar.pascal.PascalTokenTypes.COLON) {
                    break; 
                }
            }
        }
        
        return bestId != null ? bestId.getPsi() : null;
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
        // Case 1: Declaration inside a type definition
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

        // Case 2: Implementation with qualified name (TClass.Method)
        if (isImplementation()) {
            PascalRoutine declaration = getDeclaration();
            if (declaration != null) {
                return declaration.getContainingClass();
            }

            // Fallback: try to resolve the qualifier from the qualified name if possible
            // In Delphi, implementations in the implementation section usually look like:
            // procedure TMyClass.MyMethod;
            ASTNode node = getNode();
            ASTNode child = node.getFirstChildNode();
            while (child != null) {
                if (child.getElementType() == PascalTokenTypes.IDENTIFIER) {
                    ASTNode next = child.getTreeNext();
                    while (next != null && next.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                        next = next.getTreeNext();
                    }
                    if (next != null && next.getElementType() == PascalTokenTypes.DOT) {
                        // This identifier is a class name qualifier
                        String className = child.getText();
                        nl.akiar.pascal.stubs.PascalTypeIndex.TypeLookupResult typeResult =
                                nl.akiar.pascal.stubs.PascalTypeIndex.findTypesWithUsesValidation(className, getContainingFile(), child.getStartOffset());
                        if (!typeResult.getInScopeTypes().isEmpty()) {
                            return typeResult.getInScopeTypes().get(0);
                        }
                    }
                }
                child = child.getTreeNext();
            }
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
    public String toString() {
        return "PascalRoutine(" + getName() + ")";
    }
}
