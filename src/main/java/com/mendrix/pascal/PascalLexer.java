package com.mendrix.pascal;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.Map;

/**
 * Hand-written lexer for Object Pascal files.
 * Supports Delphi-style Pascal syntax with case-insensitive keywords.
 */
public class PascalLexer implements FlexLexer {
    private CharSequence myBuffer;
    private int myBufferEnd;
    private int myTokenStart;
    private int myTokenEnd;
    private int myState;

    // Keyword map for case-insensitive lookup
    private static final Map<String, IElementType> KEYWORDS = new HashMap<>();

    static {
        // Unit structure
        KEYWORDS.put("program", PascalTokenTypes.KW_PROGRAM);
        KEYWORDS.put("unit", PascalTokenTypes.KW_UNIT);
        KEYWORDS.put("library", PascalTokenTypes.KW_LIBRARY);
        KEYWORDS.put("uses", PascalTokenTypes.KW_USES);
        KEYWORDS.put("interface", PascalTokenTypes.KW_INTERFACE);
        KEYWORDS.put("implementation", PascalTokenTypes.KW_IMPLEMENTATION);
        KEYWORDS.put("initialization", PascalTokenTypes.KW_INITIALIZATION);
        KEYWORDS.put("finalization", PascalTokenTypes.KW_FINALIZATION);

        // Declarations
        KEYWORDS.put("var", PascalTokenTypes.KW_VAR);
        KEYWORDS.put("const", PascalTokenTypes.KW_CONST);
        KEYWORDS.put("type", PascalTokenTypes.KW_TYPE);
        KEYWORDS.put("resourcestring", PascalTokenTypes.KW_RESOURCESTRING);
        KEYWORDS.put("label", PascalTokenTypes.KW_LABEL);
        KEYWORDS.put("threadvar", PascalTokenTypes.KW_THREADVAR);

        // Type definitions
        KEYWORDS.put("class", PascalTokenTypes.KW_CLASS);
        KEYWORDS.put("record", PascalTokenTypes.KW_RECORD);
        KEYWORDS.put("object", PascalTokenTypes.KW_OBJECT);
        KEYWORDS.put("array", PascalTokenTypes.KW_ARRAY);
        KEYWORDS.put("set", PascalTokenTypes.KW_SET);
        KEYWORDS.put("file", PascalTokenTypes.KW_FILE);
        KEYWORDS.put("string", PascalTokenTypes.KW_STRING);
        KEYWORDS.put("packed", PascalTokenTypes.KW_PACKED);

        // Routines
        KEYWORDS.put("procedure", PascalTokenTypes.KW_PROCEDURE);
        KEYWORDS.put("function", PascalTokenTypes.KW_FUNCTION);
        KEYWORDS.put("constructor", PascalTokenTypes.KW_CONSTRUCTOR);
        KEYWORDS.put("destructor", PascalTokenTypes.KW_DESTRUCTOR);
        KEYWORDS.put("property", PascalTokenTypes.KW_PROPERTY);

        // Blocks
        KEYWORDS.put("begin", PascalTokenTypes.KW_BEGIN);
        KEYWORDS.put("end", PascalTokenTypes.KW_END);

        // Control flow
        KEYWORDS.put("if", PascalTokenTypes.KW_IF);
        KEYWORDS.put("then", PascalTokenTypes.KW_THEN);
        KEYWORDS.put("else", PascalTokenTypes.KW_ELSE);
        KEYWORDS.put("case", PascalTokenTypes.KW_CASE);
        KEYWORDS.put("of", PascalTokenTypes.KW_OF);
        KEYWORDS.put("for", PascalTokenTypes.KW_FOR);
        KEYWORDS.put("to", PascalTokenTypes.KW_TO);
        KEYWORDS.put("downto", PascalTokenTypes.KW_DOWNTO);
        KEYWORDS.put("do", PascalTokenTypes.KW_DO);
        KEYWORDS.put("while", PascalTokenTypes.KW_WHILE);
        KEYWORDS.put("repeat", PascalTokenTypes.KW_REPEAT);
        KEYWORDS.put("until", PascalTokenTypes.KW_UNTIL);
        KEYWORDS.put("with", PascalTokenTypes.KW_WITH);
        KEYWORDS.put("goto", PascalTokenTypes.KW_GOTO);
        KEYWORDS.put("break", PascalTokenTypes.KW_BREAK);
        KEYWORDS.put("continue", PascalTokenTypes.KW_CONTINUE);
        KEYWORDS.put("exit", PascalTokenTypes.KW_EXIT);

        // Exceptions
        KEYWORDS.put("try", PascalTokenTypes.KW_TRY);
        KEYWORDS.put("except", PascalTokenTypes.KW_EXCEPT);
        KEYWORDS.put("finally", PascalTokenTypes.KW_FINALLY);
        KEYWORDS.put("raise", PascalTokenTypes.KW_RAISE);
        KEYWORDS.put("on", PascalTokenTypes.KW_ON);

        // Special values
        KEYWORDS.put("nil", PascalTokenTypes.KW_NIL);
        KEYWORDS.put("self", PascalTokenTypes.KW_SELF);
        KEYWORDS.put("result", PascalTokenTypes.KW_RESULT);
        KEYWORDS.put("inherited", PascalTokenTypes.KW_INHERITED);
        KEYWORDS.put("true", PascalTokenTypes.KW_TRUE);
        KEYWORDS.put("false", PascalTokenTypes.KW_FALSE);

        // Logical/Bitwise operators
        KEYWORDS.put("and", PascalTokenTypes.KW_AND);
        KEYWORDS.put("or", PascalTokenTypes.KW_OR);
        KEYWORDS.put("not", PascalTokenTypes.KW_NOT);
        KEYWORDS.put("xor", PascalTokenTypes.KW_XOR);
        KEYWORDS.put("div", PascalTokenTypes.KW_DIV);
        KEYWORDS.put("mod", PascalTokenTypes.KW_MOD);
        KEYWORDS.put("shl", PascalTokenTypes.KW_SHL);
        KEYWORDS.put("shr", PascalTokenTypes.KW_SHR);
        KEYWORDS.put("in", PascalTokenTypes.KW_IN);
        KEYWORDS.put("is", PascalTokenTypes.KW_IS);
        KEYWORDS.put("as", PascalTokenTypes.KW_AS);

        // Visibility
        KEYWORDS.put("private", PascalTokenTypes.KW_PRIVATE);
        KEYWORDS.put("protected", PascalTokenTypes.KW_PROTECTED);
        KEYWORDS.put("public", PascalTokenTypes.KW_PUBLIC);
        KEYWORDS.put("published", PascalTokenTypes.KW_PUBLISHED);
        KEYWORDS.put("strict", PascalTokenTypes.KW_STRICT);

        // Method directives
        KEYWORDS.put("virtual", PascalTokenTypes.KW_VIRTUAL);
        KEYWORDS.put("override", PascalTokenTypes.KW_OVERRIDE);
        KEYWORDS.put("abstract", PascalTokenTypes.KW_ABSTRACT);
        KEYWORDS.put("dynamic", PascalTokenTypes.KW_DYNAMIC);
        KEYWORDS.put("reintroduce", PascalTokenTypes.KW_REINTRODUCE);
        KEYWORDS.put("overload", PascalTokenTypes.KW_OVERLOAD);
        KEYWORDS.put("static", PascalTokenTypes.KW_STATIC);
        KEYWORDS.put("external", PascalTokenTypes.KW_EXTERNAL);
        KEYWORDS.put("forward", PascalTokenTypes.KW_FORWARD);
        KEYWORDS.put("inline", PascalTokenTypes.KW_INLINE);
        KEYWORDS.put("assembler", PascalTokenTypes.KW_ASSEMBLER);
        KEYWORDS.put("cdecl", PascalTokenTypes.KW_CDECL);
        KEYWORDS.put("stdcall", PascalTokenTypes.KW_STDCALL);
        KEYWORDS.put("register", PascalTokenTypes.KW_REGISTER);
        KEYWORDS.put("pascal", PascalTokenTypes.KW_PASCAL);
        KEYWORDS.put("safecall", PascalTokenTypes.KW_SAFECALL);
        KEYWORDS.put("message", PascalTokenTypes.KW_MESSAGE);
        KEYWORDS.put("dispid", PascalTokenTypes.KW_DISPID);

        // Hint directives
        KEYWORDS.put("deprecated", PascalTokenTypes.KW_DEPRECATED);
        KEYWORDS.put("experimental", PascalTokenTypes.KW_EXPERIMENTAL);
        KEYWORDS.put("platform", PascalTokenTypes.KW_PLATFORM);

        // Property specifiers
        KEYWORDS.put("read", PascalTokenTypes.KW_READ);
        KEYWORDS.put("write", PascalTokenTypes.KW_WRITE);
        KEYWORDS.put("default", PascalTokenTypes.KW_DEFAULT);
        KEYWORDS.put("stored", PascalTokenTypes.KW_STORED);
        KEYWORDS.put("nodefault", PascalTokenTypes.KW_NODEFAULT);
        KEYWORDS.put("index", PascalTokenTypes.KW_INDEX);
        KEYWORDS.put("implements", PascalTokenTypes.KW_IMPLEMENTS);

        // Other
        KEYWORDS.put("reference", PascalTokenTypes.KW_REFERENCE);
        KEYWORDS.put("helper", PascalTokenTypes.KW_HELPER);
        KEYWORDS.put("sealed", PascalTokenTypes.KW_SEALED);
        KEYWORDS.put("absolute", PascalTokenTypes.KW_ABSOLUTE);
        KEYWORDS.put("out", PascalTokenTypes.KW_OUT);
        KEYWORDS.put("dispinterface", PascalTokenTypes.KW_DISPINTERFACE);
        KEYWORDS.put("name", PascalTokenTypes.KW_NAME);
    }

    public PascalLexer() {
    }

    public void reset(CharSequence buffer, int start, int end, int initialState) {
        myBuffer = buffer;
        myBufferEnd = end;
        myTokenStart = start;
        myTokenEnd = start;
        myState = initialState;
    }

    public int getTokenStart() {
        return myTokenStart;
    }

    public int getTokenEnd() {
        return myTokenEnd;
    }

    @Override
    public int yystate() {
        return myState;
    }

    @Override
    public void yybegin(int state) {
        myState = state;
    }

    public CharSequence yytext() {
        return myBuffer.subSequence(myTokenStart, myTokenEnd);
    }

    public int yylength() {
        return myTokenEnd - myTokenStart;
    }

    public char yycharat(int offset) {
        return myBuffer.charAt(myTokenStart + offset);
    }

    @Override
    public IElementType advance() {
        myTokenStart = myTokenEnd;

        if (myTokenStart >= myBufferEnd) {
            return null;
        }

        char c = myBuffer.charAt(myTokenStart);
        myTokenEnd = myTokenStart + 1;

        // Whitespace
        if (Character.isWhitespace(c)) {
            while (myTokenEnd < myBufferEnd && Character.isWhitespace(myBuffer.charAt(myTokenEnd))) {
                myTokenEnd++;
            }
            return TokenType.WHITE_SPACE;
        }

        // Line comment //
        if (c == '/' && myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '/') {
            myTokenEnd++;
            while (myTokenEnd < myBufferEnd) {
                char ch = myBuffer.charAt(myTokenEnd);
                if (ch == '\n' || ch == '\r') break;
                myTokenEnd++;
            }
            return PascalTokenTypes.LINE_COMMENT;
        }

        // Block comment { } or compiler directive {$ }
        if (c == '{') {
            boolean isDirective = myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '$';
            while (myTokenEnd < myBufferEnd) {
                if (myBuffer.charAt(myTokenEnd) == '}') {
                    myTokenEnd++;
                    break;
                }
                myTokenEnd++;
            }
            return isDirective ? PascalTokenTypes.COMPILER_DIRECTIVE : PascalTokenTypes.BLOCK_COMMENT;
        }

        // Parenthesis comment (* *) or compiler directive (*$ *)
        if (c == '(' && myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '*') {
            myTokenEnd++;
            boolean isDirective = myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '$';
            while (myTokenEnd < myBufferEnd - 1) {
                if (myBuffer.charAt(myTokenEnd) == '*' && myBuffer.charAt(myTokenEnd + 1) == ')') {
                    myTokenEnd += 2;
                    break;
                }
                myTokenEnd++;
            }
            // Handle unclosed comment at end of buffer
            if (myTokenEnd >= myBufferEnd - 1) {
                myTokenEnd = myBufferEnd;
            }
            return isDirective ? PascalTokenTypes.COMPILER_DIRECTIVE : PascalTokenTypes.BLOCK_COMMENT;
        }

        // String literal '...'
        if (c == '\'') {
            while (myTokenEnd < myBufferEnd) {
                char ch = myBuffer.charAt(myTokenEnd);
                myTokenEnd++;
                if (ch == '\'') {
                    // Check for escaped quote ''
                    if (myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '\'') {
                        myTokenEnd++;
                    } else {
                        break;
                    }
                }
            }
            return PascalTokenTypes.STRING_LITERAL;
        }

        // Character literal #...
        if (c == '#') {
            if (myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '$') {
                // Hex char #$XX
                myTokenEnd++;
                while (myTokenEnd < myBufferEnd && isHexDigit(myBuffer.charAt(myTokenEnd))) {
                    myTokenEnd++;
                }
            } else {
                // Decimal char #123
                while (myTokenEnd < myBufferEnd && Character.isDigit(myBuffer.charAt(myTokenEnd))) {
                    myTokenEnd++;
                }
            }
            return PascalTokenTypes.CHAR_LITERAL;
        }

        // Hex literal $...
        if (c == '$') {
            while (myTokenEnd < myBufferEnd && isHexDigit(myBuffer.charAt(myTokenEnd))) {
                myTokenEnd++;
            }
            return PascalTokenTypes.HEX_LITERAL;
        }

        // Number literal
        if (Character.isDigit(c)) {
            boolean isFloat = false;
            while (myTokenEnd < myBufferEnd && Character.isDigit(myBuffer.charAt(myTokenEnd))) {
                myTokenEnd++;
            }
            // Check for decimal point (but not ..)
            if (myTokenEnd < myBufferEnd - 1 && myBuffer.charAt(myTokenEnd) == '.'
                    && myBuffer.charAt(myTokenEnd + 1) != '.') {
                if (Character.isDigit(myBuffer.charAt(myTokenEnd + 1))) {
                    isFloat = true;
                    myTokenEnd++;
                    while (myTokenEnd < myBufferEnd && Character.isDigit(myBuffer.charAt(myTokenEnd))) {
                        myTokenEnd++;
                    }
                }
            }
            // Check for exponent
            if (myTokenEnd < myBufferEnd) {
                char ec = myBuffer.charAt(myTokenEnd);
                if (ec == 'e' || ec == 'E') {
                    isFloat = true;
                    myTokenEnd++;
                    if (myTokenEnd < myBufferEnd) {
                        char sign = myBuffer.charAt(myTokenEnd);
                        if (sign == '+' || sign == '-') {
                            myTokenEnd++;
                        }
                    }
                    while (myTokenEnd < myBufferEnd && Character.isDigit(myBuffer.charAt(myTokenEnd))) {
                        myTokenEnd++;
                    }
                }
            }
            return isFloat ? PascalTokenTypes.FLOAT_LITERAL : PascalTokenTypes.INTEGER_LITERAL;
        }

        // Identifier or keyword
        if (Character.isLetter(c) || c == '_') {
            while (myTokenEnd < myBufferEnd) {
                char ic = myBuffer.charAt(myTokenEnd);
                if (Character.isLetterOrDigit(ic) || ic == '_') {
                    myTokenEnd++;
                } else {
                    break;
                }
            }
            String word = myBuffer.subSequence(myTokenStart, myTokenEnd).toString().toLowerCase();
            IElementType keywordType = KEYWORDS.get(word);
            return keywordType != null ? keywordType : PascalTokenTypes.IDENTIFIER;
        }

        // Two-character operators (check these first)
        if (myTokenEnd < myBufferEnd) {
            char next = myBuffer.charAt(myTokenEnd);
            if (c == ':' && next == '=') {
                myTokenEnd++;
                return PascalTokenTypes.ASSIGN;
            }
            if (c == '<' && next == '>') {
                myTokenEnd++;
                return PascalTokenTypes.NE;
            }
            if (c == '<' && next == '=') {
                myTokenEnd++;
                return PascalTokenTypes.LE;
            }
            if (c == '>' && next == '=') {
                myTokenEnd++;
                return PascalTokenTypes.GE;
            }
            if (c == '.' && next == '.') {
                myTokenEnd++;
                return PascalTokenTypes.DOTDOT;
            }
        }

        // Single character operators
        switch (c) {
            case '=': return PascalTokenTypes.EQ;
            case '<': return PascalTokenTypes.LT;
            case '>': return PascalTokenTypes.GT;
            case '+': return PascalTokenTypes.PLUS;
            case '-': return PascalTokenTypes.MINUS;
            case '*': return PascalTokenTypes.MULT;
            case '/': return PascalTokenTypes.DIVIDE;
            case '@': return PascalTokenTypes.AT;
            case '^': return PascalTokenTypes.CARET;
            case ':': return PascalTokenTypes.COLON;
            case ';': return PascalTokenTypes.SEMI;
            case ',': return PascalTokenTypes.COMMA;
            case '.': return PascalTokenTypes.DOT;
            case '(': return PascalTokenTypes.LPAREN;
            case ')': return PascalTokenTypes.RPAREN;
            case '[': return PascalTokenTypes.LBRACKET;
            case ']': return PascalTokenTypes.RBRACKET;
            default: return TokenType.BAD_CHARACTER;
        }
    }

    private boolean isHexDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
