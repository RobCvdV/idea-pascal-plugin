package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.stubs.PascalRoutineStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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
        ASTNode node = getNode().findChildByType(PascalTokenTypes.IDENTIFIER);
        if (node != null) return node.getPsi();
        
        // Fallback: use a simple search in children
        ASTNode[] children = getNode().getChildren(null);
        for (ASTNode child : children) {
            if (child.getElementType() == PascalTokenTypes.IDENTIFIER) return child.getPsi();
            ASTNode subChild = child.findChildByType(PascalTokenTypes.IDENTIFIER);
            if (subChild != null) return subChild.getPsi();
        }
        
        return null;
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
            if (!routine.isImplementation() && name.equalsIgnoreCase(routine.getName())) {
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
            if (routine.isImplementation() && name.equalsIgnoreCase(routine.getName())) {
                return routine;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "PascalRoutine(" + getName() + ")";
    }
}
