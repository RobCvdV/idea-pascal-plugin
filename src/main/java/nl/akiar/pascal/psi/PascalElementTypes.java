package nl.akiar.pascal.psi;

import com.intellij.psi.stubs.IStubElementType;
import nl.akiar.pascal.stubs.PascalTypeStub;
import nl.akiar.pascal.stubs.PascalTypeStubElementType;
import nl.akiar.pascal.stubs.PascalVariableStub;
import nl.akiar.pascal.stubs.PascalVariableStubElementType;

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
     * Element type for Pascal variable definitions (var, const, parameters, fields).
     */
    IStubElementType<PascalVariableStub, PascalVariableDefinition> VARIABLE_DEFINITION =
            new PascalVariableStubElementType();

    /**
     * Element type for generic parameters.
     */
    com.intellij.psi.tree.IElementType GENERIC_PARAMETER = new nl.akiar.pascal.PascalTokenType("GENERIC_PARAMETER");

    /**
     * Element type for unit references in uses clause.
     */
    com.intellij.psi.tree.IElementType UNIT_REFERENCE = new nl.akiar.pascal.PascalTokenType("UNIT_REFERENCE");
}
