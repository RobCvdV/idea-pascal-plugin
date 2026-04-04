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
            // Check for CLASS_TYPE_REFERENCE child (handles TClass<T>.Method)
            for (PsiElement child = psi.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.CLASS_TYPE_REFERENCE) {
                    String refText = child.getText().trim();
                    int ltIdx = refText.indexOf('<');
                    ownerName = ltIdx > 0 ? refText.substring(0, ltIdx) : refText;
                    break;
                }
            }
        }
        if (ownerName == null) {
            // Fallback for implementation methods: extract TClass from TClass.Method via prevLeaf
            PsiElement nameId = psi.getNameIdentifier();
            if (nameId != null) {
                PsiElement prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(nameId);
                while (prev instanceof com.intellij.psi.PsiWhiteSpace) {
                    prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev);
                }
                if (prev != null && prev.getNode().getElementType() == nl.akiar.pascal.PascalTokenTypes.DOT) {
                    prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev);
                    while (prev instanceof com.intellij.psi.PsiWhiteSpace) {
                        prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev);
                    }
                    if (prev != null && prev.getNode().getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                        ownerName = nl.akiar.pascal.psi.PsiUtil.stripEscapePrefix(prev.getText());
                    }
                }
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
            unitName = unitName.toLowerCase(); // Normalize to lowercase
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

        // First check for RETURN_TYPE composite node (created by parser)
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.RETURN_TYPE) {
                com.intellij.psi.PsiElement returnTypePsi = child.getPsi();
                if (returnTypePsi instanceof nl.akiar.pascal.psi.PascalReturnType) {
                    return ((nl.akiar.pascal.psi.PascalReturnType) returnTypePsi).getTypeName();
                }
            }
            child = child.getTreeNext();
        }

        // Fallback: find the colon after the parameter list
        child = node.getFirstChildNode();
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
                if (type == nl.akiar.pascal.PascalTokenTypes.SEMI) {
                    break;
                }
            }
            child = child.getTreeNext();
        }
        return null;
    }

    private String computeSignatureHashFromParams(@NotNull PascalRoutine psi) {
        StringBuilder sb = new StringBuilder();
        // Only consider parameters from THIS routine's own FORMAL_PARAMETER_LIST,
        // not parameters from nested routines/lambdas in the implementation body.
        for (com.intellij.psi.PsiElement child = psi.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.FORMAL_PARAMETER_LIST) {
                for (PascalVariableDefinition p : com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(child, PascalVariableDefinition.class)) {
                    if (p.getVariableKind() == VariableKind.PARAMETER) {
                        String tn = p.getTypeName();
                        if (tn != null) sb.append(tn.toLowerCase()).append(";");
                    }
                }
                break;
            }
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
        else unitName = unitName.toLowerCase(); // Normalize to lowercase
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
