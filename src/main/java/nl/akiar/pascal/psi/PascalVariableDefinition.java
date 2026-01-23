package nl.akiar.pascal.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import nl.akiar.pascal.stubs.PascalVariableStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI element interface for Pascal variable definitions.
 * Represents: var declarations, parameters, fields, constants.
 */
public interface PascalVariableDefinition extends PsiNameIdentifierOwner, StubBasedPsiElement<PascalVariableStub> {

    /**
     * Get the variable name.
     */
    @Override
    @Nullable
    String getName();

    /**
     * Get the variable's type name (e.g., "Integer", "TGood").
     */
    @Nullable
    String getTypeName();

    /**
     * Get the kind of variable (global, local, parameter, field, etc.).
     */
    @NotNull
    VariableKind getVariableKind();

    /**
     * Get the containing scope name (procedure/function/class name).
     * Null for global variables.
     */
    @Nullable
    String getContainingScopeName();

    /**
     * Get the documentation comment preceding this variable definition.
     */
    @Nullable
    String getDocComment();

    /**
     * Get the full declaration text (e.g., "FGood: TGood" or "const MaxCount = 100").
     */
    @NotNull
    String getDeclarationText();

    /**
     * Get the visibility modifier for fields (private, protected, public, published).
     * Null for non-field variables.
     */
    @Nullable
    String getVisibility();

    /**
     * Get the containing class name for fields.
     * Null for non-field variables.
     */
    @Nullable
    String getContainingClassName();

    /**
     * Get the containing function name for local variables.
     * Null for non-local variables.
     */
    @Nullable
    String getContainingFunctionName();

    /**
     * Get the unit name for this variable's file.
     */
    @NotNull
    String getUnitName();

    /**
     * Returns the class that contains this variable if it's a field.
     */
    @Nullable
    PascalTypeDefinition getContainingClass();
}
