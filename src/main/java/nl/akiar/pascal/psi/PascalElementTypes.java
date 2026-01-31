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
     * Element type for uses clause.
     */
    com.intellij.psi.tree.IElementType USES_SECTION = new nl.akiar.pascal.PascalTokenType("USES_SECTION");

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
    com.intellij.psi.stubs.IStubElementType<nl.akiar.pascal.stubs.PascalRoutineStub, nl.akiar.pascal.psi.PascalRoutine> ROUTINE_DECLARATION =
            new nl.akiar.pascal.stubs.PascalRoutineStubElementType();

    /**
     * Element type for formal parameters.
     */
    com.intellij.psi.tree.IElementType FORMAL_PARAMETER = new nl.akiar.pascal.PascalTokenType("FORMAL_PARAMETER");

    /**
     * Element type for constant section.
     */
    com.intellij.psi.tree.IElementType CONST_SECTION = new nl.akiar.pascal.PascalTokenType("CONST_SECTION");

    /**
     * Element type for Pascal property definitions.
     */
    IStubElementType<nl.akiar.pascal.stubs.PascalPropertyStub, PascalProperty> PROPERTY_DEFINITION =
            new nl.akiar.pascal.stubs.PascalPropertyStubElementType();

    // ============================================================================
    // Scope/Section Types (for variable scope checking)
    // ============================================================================

    /**
     * Element type for class body scope (between class and end).
     */
    com.intellij.psi.tree.IElementType CLASS_BODY = new nl.akiar.pascal.PascalTokenType("CLASS_BODY");

    /**
     * Element type for record body scope (between record and end).
     */
    com.intellij.psi.tree.IElementType RECORD_BODY = new nl.akiar.pascal.PascalTokenType("RECORD_BODY");

    /**
     * Element type for interface body scope (between interface and end).
     */
    com.intellij.psi.tree.IElementType INTERFACE_BODY = new nl.akiar.pascal.PascalTokenType("INTERFACE_BODY");

    /**
     * Element type for routine body scope (local variable scope).
     */
    com.intellij.psi.tree.IElementType ROUTINE_BODY = new nl.akiar.pascal.PascalTokenType("ROUTINE_BODY");

    /**
     * Element type for visibility section (private, protected, public, published).
     */
    com.intellij.psi.tree.IElementType VISIBILITY_SECTION = new nl.akiar.pascal.PascalTokenType("VISIBILITY_SECTION");

    // ============================================================================
    // Routine Type Distinction
    // ============================================================================

    /**
     * Element type for standalone routines (not inside a class/record).
     */
    com.intellij.psi.tree.IElementType STANDALONE_ROUTINE = new nl.akiar.pascal.PascalTokenType("STANDALONE_ROUTINE");

    /**
     * Element type for method declarations (inside class/record).
     */
    com.intellij.psi.tree.IElementType METHOD_DECLARATION = new nl.akiar.pascal.PascalTokenType("METHOD_DECLARATION");

    /**
     * Element type for class methods (class function/procedure).
     */
    com.intellij.psi.tree.IElementType CLASS_METHOD = new nl.akiar.pascal.PascalTokenType("CLASS_METHOD");

    /**
     * Element type for constructor declarations.
     */
    com.intellij.psi.tree.IElementType CONSTRUCTOR_DECLARATION = new nl.akiar.pascal.PascalTokenType("CONSTRUCTOR_DECLARATION");

    /**
     * Element type for destructor declarations.
     */
    com.intellij.psi.tree.IElementType DESTRUCTOR_DECLARATION = new nl.akiar.pascal.PascalTokenType("DESTRUCTOR_DECLARATION");

    // ============================================================================
    // Specific Identifier Element Types
    // ============================================================================

    /**
     * Element type for enum element declarations (enum values).
     */
    com.intellij.psi.tree.IElementType ENUM_ELEMENT = new nl.akiar.pascal.PascalTokenType("ENUM_ELEMENT");

    /**
     * Element type for field definitions in records/classes.
     */
    com.intellij.psi.tree.IElementType FIELD_DEFINITION = new nl.akiar.pascal.PascalTokenType("FIELD_DEFINITION");

    /**
     * Element type for constant definitions.
     */
    com.intellij.psi.tree.IElementType CONSTANT_DEFINITION = new nl.akiar.pascal.PascalTokenType("CONSTANT_DEFINITION");

    /**
     * Element type for label definitions.
     */
    com.intellij.psi.tree.IElementType LABEL_DEFINITION = new nl.akiar.pascal.PascalTokenType("LABEL_DEFINITION");

    /**
     * Element type for local variable definitions (inside routine body).
     */
    com.intellij.psi.tree.IElementType LOCAL_VARIABLE = new nl.akiar.pascal.PascalTokenType("LOCAL_VARIABLE");

    // ============================================================================
    // Specific Type Definition Types
    // ============================================================================

    /**
     * Element type for class type definitions.
     */
    com.intellij.psi.tree.IElementType CLASS_TYPE = new nl.akiar.pascal.PascalTokenType("CLASS_TYPE");

    /**
     * Element type for record type definitions.
     */
    com.intellij.psi.tree.IElementType RECORD_TYPE = new nl.akiar.pascal.PascalTokenType("RECORD_TYPE");

    /**
     * Element type for interface type definitions.
     */
    com.intellij.psi.tree.IElementType INTERFACE_TYPE = new nl.akiar.pascal.PascalTokenType("INTERFACE_TYPE");

    /**
     * Element type for enum type definitions.
     */
    com.intellij.psi.tree.IElementType ENUM_TYPE = new nl.akiar.pascal.PascalTokenType("ENUM_TYPE");
}
