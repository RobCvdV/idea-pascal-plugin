package com.mendrix.pascal.psi;

import com.intellij.psi.stubs.IStubElementType;
import com.mendrix.pascal.stubs.PascalTypeStub;
import com.mendrix.pascal.stubs.PascalTypeStubElementType;

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
    com.intellij.psi.tree.IElementType GENERIC_PARAMETER = new com.mendrix.pascal.PascalTokenType("GENERIC_PARAMETER");
}
