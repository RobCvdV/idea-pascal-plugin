package nl.akiar.pascal.resolution

/**
 * Delphi built-in identifiers from the System unit.
 *
 * According to Delphi documentation, the System and SysInit units are automatically
 * used by every program/unit. These identifiers are always available without any
 * explicit uses clause.
 *
 * This list prevents false positive errors for built-in functions, types, and constants.
 */
object DelphiBuiltIns {

    /**
     * Built-in functions from System unit.
     * These are compiler intrinsics or always-available functions.
     */
    private val BUILT_IN_FUNCTIONS = setOf(
        // Memory and pointer functions
        "assigned",
        "addr",
        "ptr",
        "sizeof",
        "typeof",
        "typeinfo",
        "getmem",
        "freemem",
        "reallocmem",
        "new",
        "dispose",
        "fillchar",
        "move",
        "copymemory",

        // Ordinal functions
        "ord",
        "chr",
        "succ",
        "pred",
        "inc",
        "dec",
        "low",
        "high",
        "odd",

        // Math functions
        "abs",
        "sqr",
        "sqrt",
        "sin",
        "cos",
        "arctan",
        "ln",
        "exp",
        "round",
        "trunc",
        "int",
        "frac",
        "random",
        "randomize",

        // String functions
        "length",
        "setlength",
        "copy",
        "concat",
        "pos",
        "delete",
        "insert",
        "str",
        "val",
        "upcase",
        "lowercase",
        "uppercase",
        "trim",
        "trimleft",
        "trimright",
        "stringofchar",
        "stringreplace",
        "format",
        "formatfloat",
        "inttostr",
        "strtoint",
        "strtointdef",
        "floattostr",
        "strtofloat",
        "strtofloatdef",
        "booltostr",
        "strtobool",
        "strtobooldef",

        // Array functions
        "setlength",
        "finalize",
        "initialize",

        // Type conversion
        "byte",
        "word",
        "longword",
        "cardinal",
        "shortint",
        "smallint",
        "integer",
        "int64",
        "uint64",
        "single",
        "double",
        "extended",
        "char",
        "widechar",
        "ansichar",

        // I/O
        "write",
        "writeln",
        "read",
        "readln",
        "eof",
        "eoln",
        "ioresult",
        "assign",
        "reset",
        "rewrite",
        "append",
        "close",
        "closefile",
        "flush",
        "blockread",
        "blockwrite",
        "seek",
        "filepos",
        "filesize",
        "rename",
        "erase",

        // Control flow
        "exit",
        "halt",
        "break",
        "continue",
        "runerror",

        // Exception handling
        "raise",
        "abort",

        // Class/Object functions
        "classtype",
        "freeandnil",

        // Variant functions
        "varisempty",
        "varisnull",
        "vararraycreate",
        "vararrayof",
        "vararraydimcount",
        "vararraylowbound",
        "vararrayhighbound",
        "vararraylock",
        "vararrayunlock",
        "vartype",
        "vartypeisstr",
        "vartostr",

        // Miscellaneous
        "assert",
        "exclude",
        "include",
        "swap",
        "hi",
        "lo",
        "paramcount",
        "paramstr",
        "getdir",
        "chdir",
        "mkdir",
        "rmdir",
        "now",
        "date",
        "time",
        "sleep",
        "beep",

        // RTTI
        "default",
        "typeinfo",

        // Interface support
        "supports",

        // Comparison
        "comparestr",
        "comparetext",
        "samestr",
        "sametext",

        // Common SysUtils functions (also considered built-in)
        "fileexists",
        "directoryexists",
        "forcedirectories",
        "extractfilepath",
        "extractfilename",
        "extractfileext",
        "extractfiledir",
        "expandfilename",
        "changefileext",
        "includetrailingpathdelimiter",
        "excludetrailingpathdelimiter",
        "getcurrentdir",
        "setcurrentdir"
    )

    /**
     * Built-in types from System unit.
     * These fundamental types are always available.
     */
    private val BUILT_IN_TYPES = setOf(
        // Object Pascal base types
//        "tobject",
//        "tclass",
//        "tinterfacedobject",
//        "tinterfacedpersistent",

        // Exception types
        "exception",
        "eabort",
        "eabstract",
        "eaccessviolation",
        "eassertionfailed",
        "econverterror",
        "edivbyzero",
        "eexternal",
        "eexternalexception",
        "eheapexception",
        "einouterror",
        "eintegeroverflow",
        "eintoverflow",
        "einvalidcast",
        "einvalidcontainer",
        "einvalidinsert",
        "einvalidop",
        "einvalidoperation",
        "einvalidpointer",
        "elistError",
        "ematherror",
        "enotimplemented",
        "enotsupportedexception",
        "eoserror",
        "eoutofmemory",
        "eoverflow",
        "eprivilegenothold",
        "epropertyconverterror",
        "epropertyerror",
        "erangeerror",
        "ereadonly",
        "estackoverflow",
        "estreakerror",
        "estringlisterror",
        "eunderflow",
        "evariant",
        "evarianterror",
        "ewriteerror",
        "ezeroDivide",

        // Interface types
        "iunknown",
        "iinterface",
        "idispatch",
        "iinvokable",

        // Pointer types
        "pointer",
        "pchar",
        "pwidechar",
        "pansichar",
        "pbyte",
        "pword",
        "plongword",
        "pcardinal",
        "pshortint",
        "psmallint",
        "pinteger",
        "pint64",
        "psingle",
        "pdouble",
        "pextended",
        "pboolean",
        "pvariant",

        // String types
        "string",
        "shortstring",
        "ansistring",
        "widestring",
        "unicodestring",
        "rawbytestring",
        "utf8string",

        // Character types
        "char",
        "ansichar",
        "widechar",

        // Numeric types
        "byte",
        "shortint",
        "word",
        "smallint",
        "longword",
        "cardinal",
        "longint",
        "integer",
        "int64",
        "uint64",
        "nativeint",
        "nativeuint",
        "single",
        "double",
        "extended",
        "real",
        "real48",
        "comp",
        "currency",

        // Boolean types
        "boolean",
        "bytebool",
        "wordbool",
        "longbool",

        // Variant
        "variant",
        "olevariant",

        // Other
        "tguid",
        "tmethod",
        "tnotifyevent",
        "tdate",
        "ttime",
        "tdatetime",
        "text",
        "textfile",
        "file",

        // Common classes (from System.Classes/SysUtils)
//        "tlist",
//        "tobjectlist",
//        "tstrings",
//        "tstringlist",
//        "tstream",
//        "tfilestream",
//        "tmemorystream",
//        "tcomponent",
//        "tpersistent",
//        "tcollection",
//        "tcollectionitem",
//        "tthread"
    )

    /**
     * Built-in constants from System unit.
     */
    private val BUILT_IN_CONSTANTS = setOf(
        "nil",
        "true",
        "false",
        "maxint",
        "maxlongint",
        "pi"
    )

    /**
     * Check if an identifier is a built-in function.
     */
    @JvmStatic
    fun isBuiltInFunction(name: String): Boolean {
        return name.lowercase() in BUILT_IN_FUNCTIONS
    }

    /**
     * Check if an identifier is a built-in type.
     */
    @JvmStatic
    fun isBuiltInType(name: String): Boolean {
        return name.lowercase() in BUILT_IN_TYPES
    }

    /**
     * Check if an identifier is a built-in constant.
     */
    @JvmStatic
    fun isBuiltInConstant(name: String): Boolean {
        return name.lowercase() in BUILT_IN_CONSTANTS
    }

    /**
     * Check if an identifier is any kind of built-in (function, type, or constant).
     */
    @JvmStatic
    fun isBuiltIn(name: String): Boolean {
        val lower = name.lowercase()
        return lower in BUILT_IN_FUNCTIONS || lower in BUILT_IN_TYPES || lower in BUILT_IN_CONSTANTS
    }
}
