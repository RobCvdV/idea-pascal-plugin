package com.mendrix.pascal;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Syntax highlighter for Pascal files
 */
public class PascalSyntaxHighlighter extends SyntaxHighlighterBase {

    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("PASCAL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

    public static final TextAttributesKey STRING =
            createTextAttributesKey("PASCAL_STRING", DefaultLanguageHighlighterColors.STRING);

    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("PASCAL_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

    public static final TextAttributesKey COMMENT =
            createTextAttributesKey("PASCAL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

    public static final TextAttributesKey DIRECTIVE =
            createTextAttributesKey("PASCAL_DIRECTIVE", DefaultLanguageHighlighterColors.METADATA);

    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("PASCAL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("PASCAL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);

    public static final TextAttributesKey PARENTHESES =
            createTextAttributesKey("PASCAL_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);

    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("PASCAL_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);

    public static final TextAttributesKey SEMICOLON =
            createTextAttributesKey("PASCAL_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);

    public static final TextAttributesKey COMMA =
            createTextAttributesKey("PASCAL_COMMA", DefaultLanguageHighlighterColors.COMMA);

    public static final TextAttributesKey DOT =
            createTextAttributesKey("PASCAL_DOT", DefaultLanguageHighlighterColors.DOT);

    // Type definition colors for semantic highlighting
    public static final TextAttributesKey TYPE_CLASS =
            createTextAttributesKey("PASCAL_TYPE_CLASS", DefaultLanguageHighlighterColors.CLASS_NAME);

    public static final TextAttributesKey TYPE_RECORD =
            createTextAttributesKey("PASCAL_TYPE_RECORD", DefaultLanguageHighlighterColors.CLASS_NAME);

    public static final TextAttributesKey TYPE_INTERFACE =
            createTextAttributesKey("PASCAL_TYPE_INTERFACE", DefaultLanguageHighlighterColors.INTERFACE_NAME);

    private static final TextAttributesKey[] KEYWORD_KEYS = new TextAttributesKey[]{KEYWORD};
    private static final TextAttributesKey[] STRING_KEYS = new TextAttributesKey[]{STRING};
    private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[]{NUMBER};
    private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
    private static final TextAttributesKey[] DIRECTIVE_KEYS = new TextAttributesKey[]{DIRECTIVE};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[]{IDENTIFIER};
    private static final TextAttributesKey[] OPERATOR_KEYS = new TextAttributesKey[]{OPERATOR};
    private static final TextAttributesKey[] PARENTHESES_KEYS = new TextAttributesKey[]{PARENTHESES};
    private static final TextAttributesKey[] BRACKETS_KEYS = new TextAttributesKey[]{BRACKETS};
    private static final TextAttributesKey[] SEMICOLON_KEYS = new TextAttributesKey[]{SEMICOLON};
    private static final TextAttributesKey[] COMMA_KEYS = new TextAttributesKey[]{COMMA};
    private static final TextAttributesKey[] DOT_KEYS = new TextAttributesKey[]{DOT};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new PascalLexerAdapter();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        // Keywords
        if (isKeyword(tokenType)) {
            return KEYWORD_KEYS;
        }

        // Strings and chars
        if (tokenType.equals(PascalTokenTypes.STRING_LITERAL) ||
            tokenType.equals(PascalTokenTypes.CHAR_LITERAL)) {
            return STRING_KEYS;
        }

        // Numbers
        if (tokenType.equals(PascalTokenTypes.INTEGER_LITERAL) ||
            tokenType.equals(PascalTokenTypes.FLOAT_LITERAL) ||
            tokenType.equals(PascalTokenTypes.HEX_LITERAL)) {
            return NUMBER_KEYS;
        }

        // Comments
        if (tokenType.equals(PascalTokenTypes.LINE_COMMENT) ||
            tokenType.equals(PascalTokenTypes.BLOCK_COMMENT)) {
            return COMMENT_KEYS;
        }

        // Compiler directives
        if (tokenType.equals(PascalTokenTypes.COMPILER_DIRECTIVE)) {
            return DIRECTIVE_KEYS;
        }

        // Identifier
        if (tokenType.equals(PascalTokenTypes.IDENTIFIER)) {
            return IDENTIFIER_KEYS;
        }

        // Parentheses
        if (tokenType.equals(PascalTokenTypes.LPAREN) ||
            tokenType.equals(PascalTokenTypes.RPAREN)) {
            return PARENTHESES_KEYS;
        }

        // Brackets
        if (tokenType.equals(PascalTokenTypes.LBRACKET) ||
            tokenType.equals(PascalTokenTypes.RBRACKET)) {
            return BRACKETS_KEYS;
        }

        // Semicolon
        if (tokenType.equals(PascalTokenTypes.SEMI)) {
            return SEMICOLON_KEYS;
        }

        // Comma
        if (tokenType.equals(PascalTokenTypes.COMMA)) {
            return COMMA_KEYS;
        }

        // Dot
        if (tokenType.equals(PascalTokenTypes.DOT)) {
            return DOT_KEYS;
        }

        // Operators
        if (tokenType.equals(PascalTokenTypes.ASSIGN) ||
            tokenType.equals(PascalTokenTypes.EQ) ||
            tokenType.equals(PascalTokenTypes.NE) ||
            tokenType.equals(PascalTokenTypes.LT) ||
            tokenType.equals(PascalTokenTypes.GT) ||
            tokenType.equals(PascalTokenTypes.LE) ||
            tokenType.equals(PascalTokenTypes.GE) ||
            tokenType.equals(PascalTokenTypes.PLUS) ||
            tokenType.equals(PascalTokenTypes.MINUS) ||
            tokenType.equals(PascalTokenTypes.MULT) ||
            tokenType.equals(PascalTokenTypes.DIVIDE) ||
            tokenType.equals(PascalTokenTypes.AT) ||
            tokenType.equals(PascalTokenTypes.CARET) ||
            tokenType.equals(PascalTokenTypes.DOTDOT) ||
            tokenType.equals(PascalTokenTypes.COLON)) {
            return OPERATOR_KEYS;
        }

        return EMPTY_KEYS;
    }

    private boolean isKeyword(IElementType tokenType) {
        return tokenType.equals(PascalTokenTypes.KW_PROGRAM) ||
               tokenType.equals(PascalTokenTypes.KW_UNIT) ||
               tokenType.equals(PascalTokenTypes.KW_LIBRARY) ||
               tokenType.equals(PascalTokenTypes.KW_USES) ||
               tokenType.equals(PascalTokenTypes.KW_INTERFACE) ||
               tokenType.equals(PascalTokenTypes.KW_IMPLEMENTATION) ||
               tokenType.equals(PascalTokenTypes.KW_INITIALIZATION) ||
               tokenType.equals(PascalTokenTypes.KW_FINALIZATION) ||
               tokenType.equals(PascalTokenTypes.KW_VAR) ||
               tokenType.equals(PascalTokenTypes.KW_CONST) ||
               tokenType.equals(PascalTokenTypes.KW_TYPE) ||
               tokenType.equals(PascalTokenTypes.KW_RESOURCESTRING) ||
               tokenType.equals(PascalTokenTypes.KW_LABEL) ||
               tokenType.equals(PascalTokenTypes.KW_THREADVAR) ||
               tokenType.equals(PascalTokenTypes.KW_CLASS) ||
               tokenType.equals(PascalTokenTypes.KW_RECORD) ||
               tokenType.equals(PascalTokenTypes.KW_OBJECT) ||
               tokenType.equals(PascalTokenTypes.KW_ARRAY) ||
               tokenType.equals(PascalTokenTypes.KW_SET) ||
               tokenType.equals(PascalTokenTypes.KW_FILE) ||
               tokenType.equals(PascalTokenTypes.KW_STRING) ||
               tokenType.equals(PascalTokenTypes.KW_PACKED) ||
               tokenType.equals(PascalTokenTypes.KW_PROCEDURE) ||
               tokenType.equals(PascalTokenTypes.KW_FUNCTION) ||
               tokenType.equals(PascalTokenTypes.KW_CONSTRUCTOR) ||
               tokenType.equals(PascalTokenTypes.KW_DESTRUCTOR) ||
               tokenType.equals(PascalTokenTypes.KW_PROPERTY) ||
               tokenType.equals(PascalTokenTypes.KW_BEGIN) ||
               tokenType.equals(PascalTokenTypes.KW_END) ||
               tokenType.equals(PascalTokenTypes.KW_IF) ||
               tokenType.equals(PascalTokenTypes.KW_THEN) ||
               tokenType.equals(PascalTokenTypes.KW_ELSE) ||
               tokenType.equals(PascalTokenTypes.KW_CASE) ||
               tokenType.equals(PascalTokenTypes.KW_OF) ||
               tokenType.equals(PascalTokenTypes.KW_FOR) ||
               tokenType.equals(PascalTokenTypes.KW_TO) ||
               tokenType.equals(PascalTokenTypes.KW_DOWNTO) ||
               tokenType.equals(PascalTokenTypes.KW_DO) ||
               tokenType.equals(PascalTokenTypes.KW_WHILE) ||
               tokenType.equals(PascalTokenTypes.KW_REPEAT) ||
               tokenType.equals(PascalTokenTypes.KW_UNTIL) ||
               tokenType.equals(PascalTokenTypes.KW_WITH) ||
               tokenType.equals(PascalTokenTypes.KW_GOTO) ||
               tokenType.equals(PascalTokenTypes.KW_BREAK) ||
               tokenType.equals(PascalTokenTypes.KW_CONTINUE) ||
               tokenType.equals(PascalTokenTypes.KW_EXIT) ||
               tokenType.equals(PascalTokenTypes.KW_TRY) ||
               tokenType.equals(PascalTokenTypes.KW_EXCEPT) ||
               tokenType.equals(PascalTokenTypes.KW_FINALLY) ||
               tokenType.equals(PascalTokenTypes.KW_RAISE) ||
               tokenType.equals(PascalTokenTypes.KW_ON) ||
               tokenType.equals(PascalTokenTypes.KW_NIL) ||
               tokenType.equals(PascalTokenTypes.KW_SELF) ||
               tokenType.equals(PascalTokenTypes.KW_RESULT) ||
               tokenType.equals(PascalTokenTypes.KW_INHERITED) ||
               tokenType.equals(PascalTokenTypes.KW_TRUE) ||
               tokenType.equals(PascalTokenTypes.KW_FALSE) ||
               tokenType.equals(PascalTokenTypes.KW_AND) ||
               tokenType.equals(PascalTokenTypes.KW_OR) ||
               tokenType.equals(PascalTokenTypes.KW_NOT) ||
               tokenType.equals(PascalTokenTypes.KW_XOR) ||
               tokenType.equals(PascalTokenTypes.KW_DIV) ||
               tokenType.equals(PascalTokenTypes.KW_MOD) ||
               tokenType.equals(PascalTokenTypes.KW_SHL) ||
               tokenType.equals(PascalTokenTypes.KW_SHR) ||
               tokenType.equals(PascalTokenTypes.KW_IN) ||
               tokenType.equals(PascalTokenTypes.KW_IS) ||
               tokenType.equals(PascalTokenTypes.KW_AS) ||
               tokenType.equals(PascalTokenTypes.KW_PRIVATE) ||
               tokenType.equals(PascalTokenTypes.KW_PROTECTED) ||
               tokenType.equals(PascalTokenTypes.KW_PUBLIC) ||
               tokenType.equals(PascalTokenTypes.KW_PUBLISHED) ||
               tokenType.equals(PascalTokenTypes.KW_STRICT) ||
               tokenType.equals(PascalTokenTypes.KW_VIRTUAL) ||
               tokenType.equals(PascalTokenTypes.KW_OVERRIDE) ||
               tokenType.equals(PascalTokenTypes.KW_ABSTRACT) ||
               tokenType.equals(PascalTokenTypes.KW_DYNAMIC) ||
               tokenType.equals(PascalTokenTypes.KW_REINTRODUCE) ||
               tokenType.equals(PascalTokenTypes.KW_OVERLOAD) ||
               tokenType.equals(PascalTokenTypes.KW_STATIC) ||
               tokenType.equals(PascalTokenTypes.KW_EXTERNAL) ||
               tokenType.equals(PascalTokenTypes.KW_FORWARD) ||
               tokenType.equals(PascalTokenTypes.KW_INLINE) ||
               tokenType.equals(PascalTokenTypes.KW_ASSEMBLER) ||
               tokenType.equals(PascalTokenTypes.KW_CDECL) ||
               tokenType.equals(PascalTokenTypes.KW_STDCALL) ||
               tokenType.equals(PascalTokenTypes.KW_REGISTER) ||
               tokenType.equals(PascalTokenTypes.KW_PASCAL) ||
               tokenType.equals(PascalTokenTypes.KW_SAFECALL) ||
               tokenType.equals(PascalTokenTypes.KW_MESSAGE) ||
               tokenType.equals(PascalTokenTypes.KW_DISPID) ||
               tokenType.equals(PascalTokenTypes.KW_DEPRECATED) ||
               tokenType.equals(PascalTokenTypes.KW_EXPERIMENTAL) ||
               tokenType.equals(PascalTokenTypes.KW_PLATFORM) ||
               tokenType.equals(PascalTokenTypes.KW_READ) ||
               tokenType.equals(PascalTokenTypes.KW_WRITE) ||
               tokenType.equals(PascalTokenTypes.KW_DEFAULT) ||
               tokenType.equals(PascalTokenTypes.KW_STORED) ||
               tokenType.equals(PascalTokenTypes.KW_NODEFAULT) ||
               tokenType.equals(PascalTokenTypes.KW_INDEX) ||
               tokenType.equals(PascalTokenTypes.KW_IMPLEMENTS) ||
               tokenType.equals(PascalTokenTypes.KW_REFERENCE) ||
               tokenType.equals(PascalTokenTypes.KW_HELPER) ||
               tokenType.equals(PascalTokenTypes.KW_SEALED) ||
               tokenType.equals(PascalTokenTypes.KW_ABSOLUTE) ||
               tokenType.equals(PascalTokenTypes.KW_OUT) ||
               tokenType.equals(PascalTokenTypes.KW_DISPINTERFACE) ||
               tokenType.equals(PascalTokenTypes.KW_NAME);
    }
}
