package nl.akiar.pascal.psi;

import com.intellij.psi.stubs.IStubElementType;
import nl.akiar.pascal.stubs.PascalTypeStub;
import nl.akiar.pascal.stubs.PascalTypeStubElementType;

/**
 * Element type constants for Pascal PSI nodes.
 */
public interface PascalElementTypes {
    /**
     * Element type for Pascal type definitions (class, record, interface).
     */
    IStubElementType<PascalTypeStub, PascalTypeDefinition> TYPE_DEFINITION =
            new PascalTypeStubElementType();

    /**
     * Element type for generic parameters.
     */
    com.intellij.psi.tree.IElementType GENERIC_PARAMETER = new nl.akiar.pascal.PascalTokenType("GENERIC_PARAMETER");
}
