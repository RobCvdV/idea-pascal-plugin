package nl.akiar.pascal.dfm;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Syntax highlighter for DFM files
 */
public class DfmSyntaxHighlighter extends SyntaxHighlighterBase {
    
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("DFM_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    
    public static final TextAttributesKey STRING =
            createTextAttributesKey("DFM_STRING", DefaultLanguageHighlighterColors.STRING);
    
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("DFM_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    
    public static final TextAttributesKey COMMENT =
            createTextAttributesKey("DFM_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("DFM_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

    public static final TextAttributesKey OBJECT_NAME =
            createTextAttributesKey("DFM_OBJECT_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

    public static final TextAttributesKey CLASS_NAME =
            createTextAttributesKey("DFM_CLASS_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);

    public static final TextAttributesKey PROPERTY_NAME =
            createTextAttributesKey("DFM_PROPERTY_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

    public static final TextAttributesKey BOOLEAN =
            createTextAttributesKey("DFM_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);

    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("DFM_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    
    public static final TextAttributesKey BRACES =
            createTextAttributesKey("DFM_BRACES", DefaultLanguageHighlighterColors.BRACES);
    
    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("DFM_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    
    public static final TextAttributesKey PARENTHESES =
            createTextAttributesKey("DFM_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);
    
    private static final TextAttributesKey[] KEYWORD_KEYS = new TextAttributesKey[]{KEYWORD};
    private static final TextAttributesKey[] STRING_KEYS = new TextAttributesKey[]{STRING};
    private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[]{NUMBER};
    private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[]{IDENTIFIER};
    private static final TextAttributesKey[] OBJECT_NAME_KEYS = new TextAttributesKey[]{OBJECT_NAME};
    private static final TextAttributesKey[] CLASS_NAME_KEYS = new TextAttributesKey[]{CLASS_NAME};
    private static final TextAttributesKey[] PROPERTY_NAME_KEYS = new TextAttributesKey[]{PROPERTY_NAME};
    private static final TextAttributesKey[] BOOLEAN_KEYS = new TextAttributesKey[]{BOOLEAN};
    private static final TextAttributesKey[] OPERATOR_KEYS = new TextAttributesKey[]{OPERATOR};
    private static final TextAttributesKey[] BRACES_KEYS = new TextAttributesKey[]{BRACES};
    private static final TextAttributesKey[] BRACKETS_KEYS = new TextAttributesKey[]{BRACKETS};
    private static final TextAttributesKey[] PARENTHESES_KEYS = new TextAttributesKey[]{PARENTHESES};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new DfmLexerAdapter();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(DfmTokenTypes.OBJECT) || 
            tokenType.equals(DfmTokenTypes.INHERITED) ||
            tokenType.equals(DfmTokenTypes.INLINE) ||
            tokenType.equals(DfmTokenTypes.END) ||
            tokenType.equals(DfmTokenTypes.ITEM)) {
            return KEYWORD_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.STRING)) {
            return STRING_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.NUMBER)) {
            return NUMBER_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.COMMENT)) {
            return COMMENT_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.IDENTIFIER)) {
            return IDENTIFIER_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.OBJECT_NAME)) {
            return OBJECT_NAME_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.CLASS_NAME)) {
            return CLASS_NAME_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.PROPERTY_NAME)) {
            return PROPERTY_NAME_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.BOOLEAN)) {
            return BOOLEAN_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.EQUALS) ||
                   tokenType.equals(DfmTokenTypes.COLON) ||
                   tokenType.equals(DfmTokenTypes.DOT) ||
                   tokenType.equals(DfmTokenTypes.COMMA) ||
                   tokenType.equals(DfmTokenTypes.PLUS) ||
                   tokenType.equals(DfmTokenTypes.MINUS)) {
            return OPERATOR_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.LT) ||
                   tokenType.equals(DfmTokenTypes.GT)) {
            return BRACES_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.LBRACKET) ||
                   tokenType.equals(DfmTokenTypes.RBRACKET)) {
            return BRACKETS_KEYS;
        } else if (tokenType.equals(DfmTokenTypes.LPAREN) ||
                   tokenType.equals(DfmTokenTypes.RPAREN)) {
            return PARENTHESES_KEYS;
        }
        return EMPTY_KEYS;
    }
}

