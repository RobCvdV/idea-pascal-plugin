package nl.akiar.pascal.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalRoutine;
import nl.akiar.pascal.psi.impl.PascalRoutineImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

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
        String ownerName = null;
        // Try parent type definition first
        PsiElement parent = psi.getParent();
        while (parent != null) {
            if (parent instanceof nl.akiar.pascal.psi.PascalTypeDefinition) {
                String n = ((nl.akiar.pascal.psi.PascalTypeDefinition) parent).getName();
                if (n != null && !n.isEmpty()) ownerName = n;
                break;
            }
            parent = parent.getParent();
        }
        // Scan qualified name TClass.Method
        if (ownerName == null) {
            com.intellij.lang.ASTNode node = psi.getNode();
            com.intellij.lang.ASTNode child = node.getFirstChildNode();
            com.intellij.lang.ASTNode lastId = null;
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

        // Extract return type name from function signature
        String returnTypeName = extractReturnTypeName(psi);

        return new PascalRoutineStubImpl(parentStub, psi.getName(), psi.isImplementation(), ownerName, returnTypeName);
    }

    /**
     * Extract the return type name from a function's AST.
     * Functions have the syntax: function Name(...): ReturnType;
     * Procedures don't have a return type.
     */
    @org.jetbrains.annotations.Nullable
    private String extractReturnTypeName(@NotNull PascalRoutine psi) {
        ASTNode node = psi.getNode();
        if (node == null) return null;

        // Find the colon after the parameter list (or after the name for parameterless functions)
        // Then find the identifier after the colon
        ASTNode child = node.getFirstChildNode();
        boolean foundRparen = false;
        boolean foundColon = false;

        while (child != null) {
            com.intellij.psi.tree.IElementType type = child.getElementType();

            // Skip past the RPAREN (end of parameters)
            if (type == nl.akiar.pascal.PascalTokenTypes.RPAREN) {
                foundRparen = true;
            }

            // Look for COLON after RPAREN (or at the start for parameterless functions)
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
    }

    @NotNull
    @Override
    public PascalRoutineStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        boolean isImplementation = dataStream.readBoolean();
        String ownerName = dataStream.readNameString();
        String returnTypeName = dataStream.readNameString();
        return new PascalRoutineStubImpl(parentStub, name, isImplementation, ownerName, returnTypeName);
    }

    @Override
    public void indexStub(@NotNull PascalRoutineStub stub, @NotNull IndexSink sink) {
        if (stub.getName() != null) {
            sink.occurrence(PascalRoutineIndex.KEY, stub.getName().toLowerCase());
        }
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();
        return psi instanceof nl.akiar.pascal.psi.PascalRoutine;
    }
}
