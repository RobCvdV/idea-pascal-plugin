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

    /**
     * Element type for interface section.
     */
    com.intellij.psi.tree.IElementType INTERFACE_SECTION = new nl.akiar.pascal.PascalTokenType("INTERFACE_SECTION");

    /**
     * Element type for implementation section.
     */
    com.intellij.psi.tree.IElementType IMPLEMENTATION_SECTION = new nl.akiar.pascal.PascalTokenType("IMPLEMENTATION_SECTION");

    /**
     * Element type for unit declaration.
     */
    com.intellij.psi.tree.IElementType UNIT_DECL_SECTION = new nl.akiar.pascal.PascalTokenType("UNIT_DECL_SECTION");

    /**
     * Element type for program declaration.
     */
    com.intellij.psi.tree.IElementType PROGRAM_DECL_SECTION = new nl.akiar.pascal.PascalTokenType("PROGRAM_DECL_SECTION");

    /**
     * Element type for library declaration.
     */
    com.intellij.psi.tree.IElementType LIBRARY_DECL_SECTION = new nl.akiar.pascal.PascalTokenType("LIBRARY_DECL_SECTION");

    /**
     * Element type for variable section (var, const, etc.).
     */
    com.intellij.psi.tree.IElementType VARIABLE_SECTION = new nl.akiar.pascal.PascalTokenType("VARIABLE_SECTION");

    /**
     * Element type for type section.
     */
    com.intellij.psi.tree.IElementType TYPE_SECTION = new nl.akiar.pascal.PascalTokenType("TYPE_SECTION");

    /**
     * Element type for routine declarations (procedures, functions).
     */
    com.intellij.psi.tree.IElementType ROUTINE_DECLARATION = new nl.akiar.pascal.PascalTokenType("ROUTINE_DECLARATION");

    /**
     * Element type for formal parameters.
     */
    com.intellij.psi.tree.IElementType FORMAL_PARAMETER = new nl.akiar.pascal.PascalTokenType("FORMAL_PARAMETER");
}
