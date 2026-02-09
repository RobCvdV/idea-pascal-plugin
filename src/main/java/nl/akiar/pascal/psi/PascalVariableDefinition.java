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
public interface PascalVariableDefinition extends PsiNameIdentifierOwner, StubBasedPsiElement<PascalVariableStub>, PascalAttributable {

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

    /**
     * Get the parameter modifier (var/const/out/in) for parameter variables.
     * <p>
     * Only applicable when {@link #getVariableKind()} returns {@link VariableKind#PARAMETER}.
     * Returns {@link ParameterModifier#NONE} for non-parameter variables or parameters without modifiers.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code var AParam: Integer} returns VAR</li>
     *   <li>{@code const AParam: Integer} returns CONST</li>
     *   <li>{@code out AParam: Integer} returns OUT</li>
     *   <li>{@code AParam: Integer} returns NONE (default)</li>
     * </ul>
     *
     * @return the parameter modifier, never null
     */
    @NotNull
    ParameterModifier getParameterModifier();

    /**
     * Check if this is a var parameter (pass by reference, mutable).
     *
     * @return true if this is a var parameter
     */
    boolean isVarParameter();

    /**
     * Check if this is a const parameter (pass by reference, immutable).
     *
     * @return true if this is a const parameter
     */
    boolean isConstParameter();

    /**
     * Check if this is an out parameter (pass by reference, write-only).
     *
     * @return true if this is an out parameter
     */
    boolean isOutParameter();

    /**
     * Check if this is a value parameter (pass by value, default).
     * <p>
     * Returns true if no modifier keyword is specified or if this is not a parameter.
     *
     * @return true if this is a value parameter
     */
    boolean isValueParameter();
}
