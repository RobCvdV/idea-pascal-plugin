package nl.akiar.pascal;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Token types for Object Pascal language
 */
public interface PascalTokenTypes {
    // Comments
    IElementType LINE_COMMENT = new PascalTokenType("LINE_COMMENT");
    IElementType BLOCK_COMMENT = new PascalTokenType("BLOCK_COMMENT");
    IElementType COMPILER_DIRECTIVE = new PascalTokenType("COMPILER_DIRECTIVE");

    // Unit structure keywords
    IElementType KW_PROGRAM = new PascalTokenType("PROGRAM");
    IElementType KW_UNIT = new PascalTokenType("UNIT");
    IElementType KW_LIBRARY = new PascalTokenType("LIBRARY");
    IElementType KW_USES = new PascalTokenType("USES");
    IElementType KW_INTERFACE = new PascalTokenType("INTERFACE");
    IElementType KW_IMPLEMENTATION = new PascalTokenType("IMPLEMENTATION");
    IElementType KW_INITIALIZATION = new PascalTokenType("INITIALIZATION");
    IElementType KW_FINALIZATION = new PascalTokenType("FINALIZATION");

    // Declaration keywords
    IElementType KW_VAR = new PascalTokenType("VAR");
    IElementType KW_CONST = new PascalTokenType("CONST");
    IElementType KW_TYPE = new PascalTokenType("TYPE");
    IElementType KW_RESOURCESTRING = new PascalTokenType("RESOURCESTRING");
    IElementType KW_LABEL = new PascalTokenType("LABEL");
    IElementType KW_THREADVAR = new PascalTokenType("THREADVAR");

    // Type definition keywords
    IElementType KW_CLASS = new PascalTokenType("CLASS");
    IElementType KW_RECORD = new PascalTokenType("RECORD");
    IElementType KW_OBJECT = new PascalTokenType("OBJECT");
    IElementType KW_ARRAY = new PascalTokenType("ARRAY");
    IElementType KW_SET = new PascalTokenType("SET");
    IElementType KW_FILE = new PascalTokenType("FILE");
    IElementType KW_STRING = new PascalTokenType("STRING");
    IElementType KW_PACKED = new PascalTokenType("PACKED");

    // Routine keywords
    IElementType KW_PROCEDURE = new PascalTokenType("PROCEDURE");
    IElementType KW_FUNCTION = new PascalTokenType("FUNCTION");
    IElementType KW_CONSTRUCTOR = new PascalTokenType("CONSTRUCTOR");
    IElementType KW_DESTRUCTOR = new PascalTokenType("DESTRUCTOR");
    IElementType KW_PROPERTY = new PascalTokenType("PROPERTY");
    IElementType KW_OPERATOR = new PascalTokenType("OPERATOR");

    // Block keywords
    IElementType KW_BEGIN = new PascalTokenType("BEGIN");
    IElementType KW_END = new PascalTokenType("END");

    // Control flow keywords
    IElementType KW_IF = new PascalTokenType("IF");
    IElementType KW_THEN = new PascalTokenType("THEN");
    IElementType KW_ELSE = new PascalTokenType("ELSE");
    IElementType KW_CASE = new PascalTokenType("CASE");
    IElementType KW_OF = new PascalTokenType("OF");
    IElementType KW_FOR = new PascalTokenType("FOR");
    IElementType KW_TO = new PascalTokenType("TO");
    IElementType KW_DOWNTO = new PascalTokenType("DOWNTO");
    IElementType KW_DO = new PascalTokenType("DO");
    IElementType KW_WHILE = new PascalTokenType("WHILE");
    IElementType KW_REPEAT = new PascalTokenType("REPEAT");
    IElementType KW_UNTIL = new PascalTokenType("UNTIL");
    IElementType KW_WITH = new PascalTokenType("WITH");
    IElementType KW_GOTO = new PascalTokenType("GOTO");
    IElementType KW_BREAK = new PascalTokenType("BREAK");
    IElementType KW_CONTINUE = new PascalTokenType("CONTINUE");
    IElementType KW_EXIT = new PascalTokenType("EXIT");

    // Exception keywords
    IElementType KW_TRY = new PascalTokenType("TRY");
    IElementType KW_EXCEPT = new PascalTokenType("EXCEPT");
    IElementType KW_FINALLY = new PascalTokenType("FINALLY");
    IElementType KW_RAISE = new PascalTokenType("RAISE");
    IElementType KW_ON = new PascalTokenType("ON");

    // Special values
    IElementType KW_NIL = new PascalTokenType("NIL");
    IElementType KW_SELF = new PascalTokenType("SELF");
    IElementType KW_RESULT = new PascalTokenType("RESULT");
    IElementType KW_INHERITED = new PascalTokenType("INHERITED");
    IElementType KW_TRUE = new PascalTokenType("TRUE");
    IElementType KW_FALSE = new PascalTokenType("FALSE");

    // Logical/Bitwise operators (as keywords)
    IElementType KW_AND = new PascalTokenType("AND");
    IElementType KW_OR = new PascalTokenType("OR");
    IElementType KW_NOT = new PascalTokenType("NOT");
    IElementType KW_XOR = new PascalTokenType("XOR");
    IElementType KW_DIV = new PascalTokenType("DIV");
    IElementType KW_MOD = new PascalTokenType("MOD");
    IElementType KW_SHL = new PascalTokenType("SHL");
    IElementType KW_SHR = new PascalTokenType("SHR");
    IElementType KW_IN = new PascalTokenType("IN");
    IElementType KW_IS = new PascalTokenType("IS");
    IElementType KW_AS = new PascalTokenType("AS");

    // Visibility modifiers
    IElementType KW_PRIVATE = new PascalTokenType("PRIVATE");
    IElementType KW_PROTECTED = new PascalTokenType("PROTECTED");
    IElementType KW_PUBLIC = new PascalTokenType("PUBLIC");
    IElementType KW_PUBLISHED = new PascalTokenType("PUBLISHED");
    IElementType KW_STRICT = new PascalTokenType("STRICT");

    // Method directives
    IElementType KW_VIRTUAL = new PascalTokenType("VIRTUAL");
    IElementType KW_OVERRIDE = new PascalTokenType("OVERRIDE");
    IElementType KW_ABSTRACT = new PascalTokenType("ABSTRACT");
    IElementType KW_DYNAMIC = new PascalTokenType("DYNAMIC");
    IElementType KW_REINTRODUCE = new PascalTokenType("REINTRODUCE");
    IElementType KW_OVERLOAD = new PascalTokenType("OVERLOAD");
    IElementType KW_STATIC = new PascalTokenType("STATIC");
    IElementType KW_EXTERNAL = new PascalTokenType("EXTERNAL");
    IElementType KW_FORWARD = new PascalTokenType("FORWARD");
    IElementType KW_INLINE = new PascalTokenType("INLINE");
    IElementType KW_ASSEMBLER = new PascalTokenType("ASSEMBLER");
    IElementType KW_CDECL = new PascalTokenType("CDECL");
    IElementType KW_STDCALL = new PascalTokenType("STDCALL");
    IElementType KW_REGISTER = new PascalTokenType("REGISTER");
    IElementType KW_PASCAL = new PascalTokenType("PASCAL");
    IElementType KW_SAFECALL = new PascalTokenType("SAFECALL");
    IElementType KW_MESSAGE = new PascalTokenType("MESSAGE");
    IElementType KW_DISPID = new PascalTokenType("DISPID");

    // Hint directives
    IElementType KW_DEPRECATED = new PascalTokenType("DEPRECATED");
    IElementType KW_EXPERIMENTAL = new PascalTokenType("EXPERIMENTAL");
    IElementType KW_PLATFORM = new PascalTokenType("PLATFORM");

    // Property specifiers
    IElementType KW_READ = new PascalTokenType("READ");
    IElementType KW_WRITE = new PascalTokenType("WRITE");
    IElementType KW_DEFAULT = new PascalTokenType("DEFAULT");
    IElementType KW_STORED = new PascalTokenType("STORED");
    IElementType KW_NODEFAULT = new PascalTokenType("NODEFAULT");
    IElementType KW_INDEX = new PascalTokenType("INDEX");
    IElementType KW_IMPLEMENTS = new PascalTokenType("IMPLEMENTS");

    // Other keywords
    IElementType KW_REFERENCE = new PascalTokenType("REFERENCE");
    IElementType KW_HELPER = new PascalTokenType("HELPER");
    IElementType KW_SEALED = new PascalTokenType("SEALED");
    IElementType KW_ABSOLUTE = new PascalTokenType("ABSOLUTE");
    IElementType KW_OUT = new PascalTokenType("OUT");
    IElementType KW_DISPINTERFACE = new PascalTokenType("DISPINTERFACE");
    IElementType KW_NAME = new PascalTokenType("NAME");

    // Operators
    IElementType ASSIGN = new PascalTokenType("ASSIGN");           // :=
    IElementType EQ = new PascalTokenType("EQ");                   // =
    IElementType NE = new PascalTokenType("NE");                   // <>
    IElementType LT = new PascalTokenType("LT");                   // <
    IElementType GT = new PascalTokenType("GT");                   // >
    IElementType LE = new PascalTokenType("LE");                   // <=
    IElementType GE = new PascalTokenType("GE");                   // >=
    IElementType PLUS = new PascalTokenType("PLUS");               // +
    IElementType MINUS = new PascalTokenType("MINUS");             // -
    IElementType MULT = new PascalTokenType("MULT");               // *
    IElementType DIVIDE = new PascalTokenType("DIVIDE");           // /
    IElementType AT = new PascalTokenType("AT");                   // @
    IElementType CARET = new PascalTokenType("CARET");             // ^
    IElementType DOTDOT = new PascalTokenType("DOTDOT");           // ..
    IElementType COLON = new PascalTokenType("COLON");             // :
    IElementType SEMI = new PascalTokenType("SEMI");               // ;
    IElementType COMMA = new PascalTokenType("COMMA");             // ,
    IElementType DOT = new PascalTokenType("DOT");                 // .
    IElementType LPAREN = new PascalTokenType("LPAREN");           // (
    IElementType RPAREN = new PascalTokenType("RPAREN");           // )
    IElementType LBRACKET = new PascalTokenType("LBRACKET");       // [
    IElementType RBRACKET = new PascalTokenType("RBRACKET");       // ]

    // Literals
    IElementType STRING_LITERAL = new PascalTokenType("STRING_LITERAL");
    IElementType INTEGER_LITERAL = new PascalTokenType("INTEGER_LITERAL");
    IElementType FLOAT_LITERAL = new PascalTokenType("FLOAT_LITERAL");
    IElementType HEX_LITERAL = new PascalTokenType("HEX_LITERAL");
    IElementType CHAR_LITERAL = new PascalTokenType("CHAR_LITERAL");

    // Identifier
    IElementType IDENTIFIER = new PascalTokenType("IDENTIFIER");

    // Standard tokens
    IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;
    IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
}
