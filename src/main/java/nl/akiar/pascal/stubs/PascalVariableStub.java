package nl.akiar.pascal.stubs;

import com.intellij.psi.stubs.StubElement;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.psi.VariableKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stub for Pascal variable definitions.
 * Stores minimal indexing data for fast lookup without parsing full AST.
 */
public interface PascalVariableStub extends StubElement<PascalVariableDefinition> {

    /**
     * Get the variable name.
     */
    @Nullable
    String getName();

    /**
     * Get the variable's type name (e.g., "Integer", "TGood", "String").
     */
    @Nullable
    String getTypeName();

    /**
     * Get the kind of variable (global, local, parameter, field, etc.).
     */
    @NotNull
    VariableKind getVariableKind();

    /**
     * Get the name of the containing scope (procedure/function/class name).
     * Null for global variables.
     */
    @Nullable
    String getContainingScopeName();

    @Nullable
    String getOwnerTypeName();

    @Nullable
    String getVisibility();
}
