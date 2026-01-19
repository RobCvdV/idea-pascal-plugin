package nl.akiar.pascal.psi;

/**
 * Classification of Pascal variable declarations.
 */
public enum VariableKind {
    /** Unit-level variables (var section at unit level) */
    GLOBAL,

    /** Procedure/function local variables */
    LOCAL,

    /** Procedure/function parameters */
    PARAMETER,

    /** Class/record field variables */
    FIELD,

    /** Constant declarations */
    CONSTANT,

    /** Thread-local variables (threadvar) */
    THREADVAR,

    /** Loop variable (for-loop counter) */
    LOOP_VAR,

    /** Unknown/unclassified */
    UNKNOWN
}
