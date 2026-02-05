package nl.akiar.pascal.parser

/**
 * Registry of built-in Pascal/Delphi types for fast detection during parsing.
 *
 * This allows the parser to immediately classify type references as SIMPLE_TYPE
 * without requiring resolution, enabling zero-cost highlighting for ~80% of type references.
 */
object PascalBuiltInTypes {
    // Numeric types
    private val NUMERIC_TYPES = setOf(
        // Integer types
        "Integer", "Cardinal", "ShortInt", "SmallInt", "LongInt", "Int64",
        "Byte", "Word", "LongWord", "UInt64",
        "NativeInt", "NativeUInt",
        // Floating-point types
        "Single", "Double", "Extended", "Currency", "Comp",
        "Real", "Real48"
    )

    // Boolean types
    private val BOOLEAN_TYPES = setOf(
        "Boolean", "ByteBool", "WordBool", "LongBool"
    )

    // Character types
    private val CHAR_TYPES = setOf(
        "Char", "AnsiChar", "WideChar"
    )

    // String types
    private val STRING_TYPES = setOf(
        "String", "AnsiString", "WideString", "UnicodeString", "ShortString"
    )

    // Pointer and variant types
    private val OTHER_TYPES = setOf(
        "Pointer", "Variant", "OleVariant",
        "PChar", "PWideChar", "PAnsiChar"
    )

    /**
     * All simple types combined.
     * These are types that are always available without a uses clause.
     */
    val ALL_SIMPLE_TYPES: Set<String> = NUMERIC_TYPES + BOOLEAN_TYPES +
                                         CHAR_TYPES + STRING_TYPES + OTHER_TYPES

    /**
     * Check if a type name is a built-in simple type.
     * Case-insensitive to match Pascal's semantics.
     *
     * @param typeName The type name to check (e.g., "Integer", "string", "Boolean")
     * @return true if this is a built-in simple type
     */
    fun isSimpleType(typeName: String): Boolean {
        return ALL_SIMPLE_TYPES.any { it.equals(typeName, ignoreCase = true) }
    }

    /**
     * Pascal keywords that can be used as type names.
     * These are highlighted as keywords rather than as type names.
     */
    val KEYWORD_TYPES = setOf(
        "string",      // lowercase string is a keyword
        "array",       // array of ...
        "set",         // set of ...
        "file",        // file of ...
        "record",      // record type
        "class",       // class type
        "interface"    // interface type
    )

    /**
     * Check if a type name is a Pascal keyword used as a type.
     *
     * @param typeName The type name to check (must be lowercase)
     * @return true if this is a keyword type
     */
    fun isKeywordType(typeName: String): Boolean {
        return KEYWORD_TYPES.contains(typeName.lowercase())
    }
}
