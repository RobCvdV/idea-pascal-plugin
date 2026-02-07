package nl.akiar.pascal.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.*;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;
import nl.akiar.pascal.psi.impl.PascalRoutineImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PascalRoutineStubElementType extends IStubElementType<PascalRoutineStub, PascalRoutine> {
    public PascalRoutineStubElementType() {
        super("ROUTINE_DECLARATION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalRoutine createPsi(@NotNull PascalRoutineStub stub) {
        return new PascalRoutineImpl(stub, this);
    }

    @NotNull
    @Override
    public PascalRoutineStub createStub(@NotNull PascalRoutine psi, StubElement parentStub) {
        // Owner type name
        String ownerName = null;
        PsiElement parent = psi.getParent();
        while (parent != null) {
            if (parent instanceof nl.akiar.pascal.psi.PascalTypeDefinition) {
                String n = ((nl.akiar.pascal.psi.PascalTypeDefinition) parent).getName();
                if (n != null && !n.isEmpty()) ownerName = n;
                break;
            }
            parent = parent.getParent();
        }
        if (ownerName == null) {
            ASTNode node = psi.getNode();
            ASTNode child = node.getFirstChildNode();
            ASTNode lastId = null;
            while (child != null) {
                if (child.getElementType() == nl.akiar.pascal.PascalTokenTypes.LPAREN ||
                    child.getElementType() == nl.akiar.pascal.PascalTokenTypes.SEMI ||
                    child.getElementType() == nl.akiar.pascal.PascalTokenTypes.COLON) {
                    break;
                }
                if (child.getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                    lastId = child;
                } else if (child.getElementType() == nl.akiar.pascal.PascalTokenTypes.DOT && lastId != null) {
                    ownerName = lastId.getText();
                    break;
                }
                child = child.getTreeNext();
            }
        }

        // Return type name
        String returnTypeName = extractReturnTypeName(psi);

        // Unit name
        String unitName = null;
        com.intellij.psi.PsiFile file = psi.getContainingFile();
        if (file != null) {
            String base = file.getName();
            int dot = base.lastIndexOf('.');
            unitName = dot > 0 ? base.substring(0, dot) : base;
        }
        if (unitName == null) unitName = "";

        // Signature hash: join parameter type names
        String signatureHash = computeSignatureHashFromParams(psi);
        if (signatureHash == null) signatureHash = "";

        // Visibility and section from PSI via local AST inspection
        String visibility = null;
        String section = null;
        try {
            visibility = nl.akiar.pascal.psi.PsiUtil.getVisibility(psi);
            section = nl.akiar.pascal.psi.PsiUtil.getSection(psi);
        } catch (Exception ignored) {
            // Guard null-safety during stub creation
        }

        return new PascalRoutineStubImpl(parentStub, psi.getName(), psi.isImplementation(), ownerName, returnTypeName, unitName, signatureHash, visibility, section);
    }

    @org.jetbrains.annotations.Nullable
    private String extractReturnTypeName(@NotNull PascalRoutine psi) {
        ASTNode node = psi.getNode();
        if (node == null) return null;
        ASTNode child = node.getFirstChildNode();
        boolean foundColon = false;
        while (child != null) {
            com.intellij.psi.tree.IElementType type = child.getElementType();
            if (type == nl.akiar.pascal.PascalTokenTypes.COLON) {
                foundColon = true;
                child = child.getTreeNext();
                continue;
            }
            if (foundColon) {
                if (type == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                    child = child.getTreeNext();
                    continue;
                }
                if (type == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                    StringBuilder typeName = new StringBuilder(child.getText());
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
                if (type == nl.akiar.pascal.PascalTokenTypes.SEMI) {
                    break;
                }
            }
            child = child.getTreeNext();
        }
        return null;
    }

    private String computeSignatureHashFromParams(@NotNull PascalRoutine psi) {
        List<PascalVariableDefinition> params = new ArrayList<>();
        for (PsiElement child = psi.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PascalVariableDefinition var && var.getVariableKind() == VariableKind.PARAMETER) {
                params.add(var);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (PascalVariableDefinition p : params) {
            String tn = p.getTypeName();
            if (tn != null) sb.append(tn.toLowerCase()).append(";");
        }
        return sb.toString();
    }

    @NotNull
    @Override
    public String getExternalId() {
        return "pascal.routine";
    }

    @Override
    public void serialize(@NotNull PascalRoutineStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeBoolean(stub.isImplementation());
        dataStream.writeName(stub.getContainingClassName());
        dataStream.writeName(stub.getReturnTypeName());
        dataStream.writeName(stub.getUnitName() == null ? "" : stub.getUnitName());
        dataStream.writeName(stub.getSignatureHash() == null ? "" : stub.getSignatureHash());
        dataStream.writeName(stub.getVisibility() == null ? "" : stub.getVisibility());
        dataStream.writeName(stub.getSection() == null ? "" : stub.getSection());
    }

    @NotNull
    @Override
    public PascalRoutineStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        boolean isImplementation = dataStream.readBoolean();
        String ownerName = dataStream.readNameString();
        String returnTypeName = dataStream.readNameString();
        String unitName = dataStream.readNameString();
        String signatureHash = dataStream.readNameString();
        String visibility = dataStream.readNameString();
        String section = dataStream.readNameString();
        if (unitName == null) unitName = "";
        if (signatureHash == null) signatureHash = "";
        if (visibility == null || visibility.isEmpty()) visibility = null;
        if (section == null || section.isEmpty()) section = null;
        return new PascalRoutineStubImpl(parentStub, name, isImplementation, ownerName, returnTypeName, unitName, signatureHash, visibility, section);
    }

    @Override
    public void indexStub(@NotNull PascalRoutineStub stub, @NotNull IndexSink sink) {
        if (stub.getName() != null) {
            sink.occurrence(PascalRoutineIndex.KEY, stub.getName().toLowerCase());
            String unit = stub.getUnitName();
            String owner = stub.getContainingClassName();
            String sig = stub.getSignatureHash();
            if (unit != null && owner != null) {
                String scopedKey = (unit + "#" + owner + "#" + stub.getName()).toLowerCase();
                sink.occurrence(nl.akiar.pascal.stubs.PascalScopedRoutineIndex.KEY, scopedKey);
                // Add overload-aware key with signature if present
                if (sig != null && !sig.isEmpty()) {
                    String scopedKeyWithSig = (unit + "#" + owner + "#" + stub.getName() + "#" + sig).toLowerCase();
                    sink.occurrence(nl.akiar.pascal.stubs.PascalScopedRoutineIndex.KEY, scopedKeyWithSig);
                }
            }
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        // Avoid calling node.getPsi() during indexing
        return node.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.ROUTINE_DECLARATION;
    }
}
