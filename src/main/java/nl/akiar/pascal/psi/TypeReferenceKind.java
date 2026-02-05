package nl.akiar.pascal.psi;

/**
 * Categorizes type references by their nature to enable fast highlighting
 * without requiring resolution for common cases.
 * <p>
 * This classification happens at parse time, allowing the semantic annotator
 * to instantly highlight built-in types without expensive resolution operations.
 */
public enum TypeReferenceKind {
    /**
     * Built-in simple types known at parse time: Integer, String, Boolean, etc.
     * Can be highlighted immediately without resolution.
     * These types are always available in Pascal/Delphi and never require a uses clause.
     * <p>
     * Examples: Integer, Cardinal, String, Boolean, Char, Double, Pointer, Variant
     */
    SIMPLE_TYPE,

    /**
     * User-defined types: TMyClass, IMyInterface, EMyException, etc.
     * Requires resolution to determine exact kind (class, record, interface, etc.).
     * These types follow Pascal naming conventions (T*, I*, E* prefix).
     * <p>
     * Examples: TObject, TList, IInterface, EException
     */
    USER_TYPE,

    /**
     * Pascal keywords used as types: string, array, set, file, etc.
     * Can be highlighted as keywords immediately without resolution.
     * These are language keywords that also function as type specifiers.
     * <p>
     * Examples: string, array, set, file, record, class, interface
     */
    KEYWORD_TYPE,

    /**
     * Unknown or ambiguous type reference.
     * Requires full resolution to determine nature.
     * This is a fallback for cases where the type name doesn't match
     * any known pattern.
     */
    UNKNOWN
}
