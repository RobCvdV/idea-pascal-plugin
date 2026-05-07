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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
        List<String> allAncestors = extractAllAncestorNames(psi);
        List<String> enumValueNames = extractEnumValueNames(psi);
        return new PascalTypeStubImpl(parentStub, psi.getName(), psi.getTypeKind(),
                psi.getTypeParameters(), allAncestors, enumValueNames);
    }

    /**
     * Walk the type definition's AST for ENUM_ELEMENT nodes and return their
     * identifier names — but stop at *nested* TYPE_DEFINITION boundaries.
     * Each nested type def gets its own stub with its own enum-value list, so
     * we'd double-index otherwise.
     *
     * Net effect:
     *   TAlign = (taLeft, taCenter, taRight)        → stub.enumValues = [taLeft, taCenter, taRight]
     *   TFoo = class type TCursor = (crNormal); end → TFoo.enumValues = []
     *                                                  TCursor.enumValues = [crNormal]
     *
     * Called once at stub-build time when AST is already loaded.
     */
    @NotNull
    private List<String> extractEnumValueNames(@NotNull PascalTypeDefinition psi) {
        ASTNode rootNode = psi.getNode();
        if (rootNode == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        collectEnumElementNamesShallow(rootNode, names, /* isRoot = */ true);
        return names.isEmpty() ? Collections.emptyList() : names;
    }

    private void collectEnumElementNamesShallow(@NotNull ASTNode node, @NotNull List<String> out, boolean isRoot) {
        IElementType type = node.getElementType();
        if (type == nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT) {
            String name = extractEnumElementIdentifier(node);
            if (name != null && !name.isEmpty()) {
                out.add(name);
            }
            return;
        }
        // Stop at nested type definitions; their own stub will index their values.
        if (type == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION && !isRoot) {
            return;
        }
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            collectEnumElementNamesShallow(child, out, /* isRoot = */ false);
            child = child.getTreeNext();
        }
    }

    private String extractEnumElementIdentifier(@NotNull ASTNode enumElNode) {
        // Prefer an IDENTIFIER child (the canonical case)
        ASTNode child = enumElNode.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                return child.getText();
            }
            child = child.getTreeNext();
        }
        // Fallback: strip "= ordinal" trailing from the raw text
        String raw = enumElNode.getText();
        if (raw == null) return null;
        int eq = raw.indexOf('=');
        String trimmed = (eq > 0 ? raw.substring(0, eq) : raw).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Extract all ancestor names from a type definition PSI.
     * For "TFoo = class(TBar, IFoo, IBar)", returns ["TBar", "IFoo", "IBar"].
     * The AST structure is:
     *   TYPE_DEFINITION
     *     IDENTIFIER (type name)
     *     CLASS_TYPE / RECORD_TYPE / INTERFACE_TYPE
     *       LPAREN
     *       IDENTIFIER/TYPE_REFERENCE (first ancestor)
     *       COMMA
     *       IDENTIFIER/TYPE_REFERENCE (second ancestor)
     *       ...
     *       RPAREN
     */
    @NotNull
    private List<String> extractAllAncestorNames(@NotNull PascalTypeDefinition psi) {
        ASTNode node = psi.getNode();
        if (node == null) return Collections.emptyList();

        // Check that we're not an enum or alias type
        TypeKind kind = psi.getTypeKind();
        if (kind == TypeKind.ENUM || kind == TypeKind.ALIAS || kind == TypeKind.PROCEDURAL) {
            return Collections.emptyList();
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
            lparen = nl.akiar.pascal.psi.PsiUtil.findFirstRecursive(typeNode, nl.akiar.pascal.PascalTokenTypes.LPAREN);
        }
        if (lparen == null) return Collections.emptyList();

        List<String> ancestors = new ArrayList<>();
        ASTNode next = lparen.getTreeNext();

        while (next != null) {
            IElementType type = next.getElementType();
            if (type == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                next = next.getTreeNext();
                continue;
            }
            if (type == nl.akiar.pascal.PascalTokenTypes.RPAREN) {
                break;
            }
            if (type == nl.akiar.pascal.PascalTokenTypes.COMMA) {
                next = next.getTreeNext();
                continue;
            }

            // Handle TYPE_REFERENCE elements created by parser (wraps identifiers)
            if (type == nl.akiar.pascal.psi.PascalElementTypes.TYPE_REFERENCE) {
                String extracted = extractNameFromTypeReference(next);
                if (extracted != null) {
                    ancestors.add(extracted);
                }
                next = next.getTreeNext();
                continue;
            }

            // Handle bare IDENTIFIER tokens
            if (type == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                String name = collectDottedName(next);
                if (name != null && !name.isEmpty()) {
                    ancestors.add(name);
                    // Skip past the tokens we consumed
                    next = skipPastDottedName(next);
                    continue;
                }
            }

            next = next.getTreeNext();
        }

        return ancestors;
    }

    /**
     * Extract a type name from a TYPE_REFERENCE AST node.
     * Handles both PascalTypeReferenceElement and raw identifier children.
     * Strips generic arguments (stops at '<').
     */
    private String extractNameFromTypeReference(ASTNode typeRefNode) {
        PsiElement typeRefElement = typeRefNode.getPsi();
        if (typeRefElement instanceof nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) {
            String typeName = ((nl.akiar.pascal.psi.impl.PascalTypeReferenceElement) typeRefElement).getReferencedTypeName();
            if (typeName != null) {
                // Strip generic args
                int lt = typeName.indexOf('<');
                return lt > 0 ? typeName.substring(0, lt) : typeName;
            }
        }
        // Fallback: extract identifiers from TYPE_REFERENCE children
        StringBuilder sb = new StringBuilder();
        ASTNode refChild = typeRefNode.getFirstChildNode();
        while (refChild != null) {
            if (refChild.getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                if (sb.length() > 0) sb.append(".");
                sb.append(refChild.getText());
            } else if (refChild.getElementType() == nl.akiar.pascal.PascalTokenTypes.LT) {
                break; // Stop at generic arguments
            }
            refChild = refChild.getTreeNext();
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Collect a dotted name starting from an IDENTIFIER node.
     * E.g., "System.IInterface" from IDENTIFIER DOT IDENTIFIER.
     */
    private String collectDottedName(ASTNode identNode) {
        StringBuilder sb = new StringBuilder();
        sb.append(identNode.getText());
        ASTNode next = identNode.getTreeNext();
        while (next != null) {
            if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                next = next.getTreeNext();
                continue;
            }
            if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.DOT) {
                sb.append(".");
                next = next.getTreeNext();
            } else if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                sb.append(next.getText());
                next = next.getTreeNext();
            } else if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.LT) {
                break; // Stop at generic arguments
            } else {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Skip past a dotted name (IDENTIFIER [DOT IDENTIFIER]*) and return the next node after it.
     */
    private ASTNode skipPastDottedName(ASTNode identNode) {
        ASTNode next = identNode.getTreeNext();
        while (next != null) {
            if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.WHITE_SPACE) {
                next = next.getTreeNext();
                continue;
            }
            if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.DOT) {
                next = next.getTreeNext();
            } else if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                next = next.getTreeNext();
            } else if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.LT) {
                // Skip generic arguments: <...>
                int depth = 1;
                next = next.getTreeNext();
                while (next != null && depth > 0) {
                    if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.LT) depth++;
                    else if (next.getElementType() == nl.akiar.pascal.PascalTokenTypes.GT) depth--;
                    next = next.getTreeNext();
                }
                break;
            } else {
                break;
            }
        }
        return next;
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
        List<String> ancestors = stub.getAllAncestorNames();
        dataStream.writeInt(ancestors.size());
        for (String ancestor : ancestors) {
            dataStream.writeName(ancestor);
        }
        List<String> enumValueNames = stub.getEnumValueNames();
        dataStream.writeInt(enumValueNames.size());
        for (String v : enumValueNames) {
            dataStream.writeName(v);
        }
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
        int ancestorCount = dataStream.readInt();
        List<String> allAncestorNames = new ArrayList<>(ancestorCount);
        for (int i = 0; i < ancestorCount; i++) {
            allAncestorNames.add(dataStream.readNameString());
        }
        int enumCount = dataStream.readInt();
        List<String> enumValueNames = new ArrayList<>(enumCount);
        for (int i = 0; i < enumCount; i++) {
            enumValueNames.add(dataStream.readNameString());
        }
        return new PascalTypeStubImpl(parentStub, name, kind, typeParameters, allAncestorNames, enumValueNames);
    }

    @Override
    public void indexStub(@NotNull PascalTypeStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(PascalTypeIndex.KEY, name.toLowerCase());
        }

        // Index each ancestor name for the implementors reverse index
        for (String ancestor : stub.getAllAncestorNames()) {
            String simpleName = ancestor;
            // Strip generic params (before '<')
            int ltIdx = simpleName.indexOf('<');
            if (ltIdx > 0) simpleName = simpleName.substring(0, ltIdx);
            // Strip unit prefix (after last '.')
            int dotIdx = simpleName.lastIndexOf('.');
            if (dotIdx >= 0) simpleName = simpleName.substring(dotIdx + 1);
            if (!simpleName.isEmpty()) {
                sink.occurrence(PascalImplementorsIndex.KEY, simpleName.toLowerCase());
            }
        }

        // Index each enum-value name so unqualified resolution can find the
        // containing type def from a stub-only lookup (no AST load).
        for (String valueName : stub.getEnumValueNames()) {
            if (!valueName.isEmpty()) {
                sink.occurrence(PascalEnumValueStubIndex.KEY, valueName.toLowerCase());
            }
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        // Avoid calling node.getPsi() during indexing
        return node.getElementType() == nl.akiar.pascal.psi.PascalElementTypes.TYPE_DEFINITION;
    }
}
