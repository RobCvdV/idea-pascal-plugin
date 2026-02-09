package nl.akiar.pascal.psi;

/**
 * Enum representing parameter passing modifiers in Pascal routines.
 * <p>
 * Pascal supports several parameter passing mechanisms:
 * <ul>
 *   <li>VALUE - Value parameter (default, pass by value)</li>
 *   <li>VAR - Variable parameter (pass by reference, mutable)</li>
 *   <li>CONST - Constant parameter (pass by reference, immutable)</li>
 *   <li>OUT - Output parameter (pass by reference, write-only)</li>
 *   <li>IN - Input parameter (explicit pass by value, rare)</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * procedure Test(
 *   AValue: Integer;        // VALUE - value parameter (default)
 *   var AVar: Integer;      // VAR - mutable reference
 *   const AConst: Integer;  // CONST - immutable reference
 *   out AOut: Integer       // OUT - output only
 * );
 * </pre>
 */
public enum ParameterModifier {
    /**
     * Value parameter (default, no modifier keyword).
     * <p>
     * The parameter is passed by value (a copy is made).
     * This is the default when no modifier keyword is specified.
     * <p>
     * Example: {@code procedure Test(AValue: Integer);}
     */
    VALUE,

    /**
     * @deprecated Use {@link #VALUE} instead. This is kept for backward compatibility.
     */
    @Deprecated
    NONE,

    /**
     * Variable parameter - pass by reference, mutable.
     * <p>
     * The parameter is passed by reference and can be modified.
     * Changes to the parameter affect the original variable.
     * <p>
     * Example: {@code procedure Swap(var A, B: Integer);}
     */
    VAR,

    /**
     * Constant parameter - pass by reference, immutable.
     * <p>
     * The parameter is passed by reference but cannot be modified.
     * More efficient than value parameters for large structures.
     * <p>
     * Example: {@code procedure Display(const AData: TLargeRecord);}
     */
    CONST,

    /**
     * Output parameter - pass by reference, write-only.
     * <p>
     * The parameter is passed by reference and is intended for output only.
     * The compiler may not initialize it before the call.
     * <p>
     * Example: {@code procedure GetValues(out AResult: Integer);}
     */
    OUT,

    /**
     * Input parameter - explicit pass by value (rare).
     * <p>
     * Explicitly marks a parameter as pass by value.
     * Rarely used in practice, mostly for documentation or specific cases.
     * <p>
     * Example: {@code procedure Process(in AValue: Integer);}
     */
    IN
}

