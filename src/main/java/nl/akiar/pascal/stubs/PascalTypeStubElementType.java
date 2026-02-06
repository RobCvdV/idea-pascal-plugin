package nl.akiar.pascal.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.TypeKind;
import nl.akiar.pascal.psi.impl.PascalTypeDefinitionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stub element type for Pascal type definitions.
 * Handles serialization/deserialization and indexing.
 */
public class PascalTypeStubElementType extends IStubElementType<PascalTypeStub, PascalTypeDefinition> {
    private static final Logger LOG = Logger.getInstance(PascalTypeStubElementType.class);

    public PascalTypeStubElementType() {
        super("PASCAL_TYPE_DEFINITION", PascalLanguage.INSTANCE);
    }

    @Override
    public PascalTypeDefinition createPsi(@NotNull PascalTypeStub stub) {
        return new PascalTypeDefinitionImpl(stub, this);
    }

    @Override
    @NotNull
    public PascalTypeStub createStub(@NotNull PascalTypeDefinition psi, StubElement<?> parentStub) {
        // LOG.info("[PascalStub] Creating stub for: " + psi.getName() + " (" + psi.getTypeKind() + ")");
        String superClassName = extractSuperClassName(psi);
        return new PascalTypeStubImpl(parentStub, psi.getName(), psi.getTypeKind(), psi.getTypeParameters(), superClassName);
    }

    /**
     * Extract the superclass name from a type definition PSI.
     * Looks for patterns like: class(TBase) or class(TBase, IInterface)
     * The AST structure is:
     *   TYPE_DEFINITION
     *     IDENTIFIER (type name)
     *     CLASS_TYPE / RECORD_TYPE / INTERFACE_TYPE
     *       LPAREN
     *       IDENTIFIER (superclass name)
     *       ...
     */
    @Nullable
    private String extractSuperClassName(@NotNull PascalTypeDefinition psi) {
        ASTNode node = psi.getNode();
        if (node == null) return null;

        // Check that we're not an enum or alias type
        TypeKind kind = psi.getTypeKind();
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
        ASTNode lparen = typeNode.findChildByType(nl.akiar.pascal.PascalTokenTypes.LPAREN);
        if (lparen == null) {
            // Also check recursively in children
            lparen = nl.akiar.pascal.psi.PsiUtil.findFirstRecursive(typeNode, nl.akiar.pascal.PascalTokenTypes.LPAREN);
        }
        if (lparen == null) return null;

        // Find the first identifier after LPAREN - this is the superclass name
        ASTNode next = lparen.getTreeNext();
        StringBuilder superName = new StringBuilder();
        while (next != null) {
            IElementType type = next.getElementType();
            if (type == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                next = next.getTreeNext();
                continue;
            }
            if (type == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                superName.append(next.getText());
                next = next.getTreeNext();
                // Handle dotted names like System.TObject
                while (next != null) {
                    if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                        next = next.getTreeNext();
                        continue;
                    }
                    if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.DOT) {
                        superName.append(".");
                        next = next.getTreeNext();
                    } else if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                        superName.append(next.getText());
                        next = next.getTreeNext();
                    } else {
                        break;
                    }
                }
                return superName.length() > 0 ? superName.toString() : null;
            }
            // If we hit RPAREN or COMMA before identifier, no superclass
            if (type == nl.akiar.pascal.PascalTokenTypes.RPAREN ||
                type == nl.akiar.pascal.PascalTokenTypes.COMMA) {
                return null;
            }
            next = next.getTreeNext();
        }
        return null;
    }

    @Override
    @NotNull
    public String getExternalId() {
        return "pascal.typeDefinition";
    }

    @Override
    public void serialize(@NotNull PascalTypeStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeInt(stub.getTypeKind().ordinal());
        List<String> typeParameters = stub.getTypeParameters();
        dataStream.writeInt(typeParameters.size());
        for (String param : typeParameters) {
            dataStream.writeName(param);
        }
        dataStream.writeName(stub.getSuperClassName());
    }

    @Override
    @NotNull
    public PascalTypeStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        int kindOrdinal = dataStream.readInt();
        TypeKind kind = TypeKind.values()[kindOrdinal];
        int paramCount = dataStream.readInt();
        List<String> typeParameters = new ArrayList<>(paramCount);
        for (int i = 0; i < paramCount; i++) {
            typeParameters.add(dataStream.readNameString());
        }
        String superClassName = dataStream.readNameString();
        return new PascalTypeStubImpl(parentStub, name, kind, typeParameters, superClassName);
    }

    @Override
    public void indexStub(@NotNull PascalTypeStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            // LOG.info("[PascalStub] Indexing type: " + name + " (" + stub.getTypeKind() + ")");
            sink.occurrence(PascalTypeIndex.KEY, name.toLowerCase());
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        // Avoid calling node.getPsi() during indexing
        return node.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION;
    }
}
