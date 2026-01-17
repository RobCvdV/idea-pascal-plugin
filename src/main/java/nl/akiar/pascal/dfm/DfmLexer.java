package nl.akiar.pascal.dfm;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Hand-written lexer for DFM files.
 * This lexer works directly with CharSequence for proper IntelliJ integration.
 */
public class DfmLexer implements FlexLexer {
    private CharSequence myBuffer;
    private int myBufferEnd;
    private int myTokenStart;
    private int myTokenEnd;
    private int myState;

    // State constants for object declaration parsing
    private static final int STATE_NORMAL = 0;
    private static final int STATE_AFTER_OBJECT_KEYWORD = 1;  // After object/inherited/inline, expecting name
    private static final int STATE_AFTER_OBJECT_NAME = 2;     // After name, expecting : or newline
    private static final int STATE_AFTER_COLON = 3;           // After :, expecting class name
    private static final int STATE_AFTER_EQUALS = 4;          // After =, expecting value

    public DfmLexer() {
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

    public int getState() {
        return myState;
    }

    @Override
    public int yystate() {
        return myState;
    }

    @Override
    public void yybegin(int state) {
        myState = state;
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
            boolean hasNewline = (c == '\n' || c == '\r');
            while (myTokenEnd < myBufferEnd && Character.isWhitespace(myBuffer.charAt(myTokenEnd))) {
                char wc = myBuffer.charAt(myTokenEnd);
                if (wc == '\n' || wc == '\r') hasNewline = true;
                myTokenEnd++;
            }
            // Reset state on newline (end of object declaration line)
            if (hasNewline && myState != STATE_NORMAL) {
                myState = STATE_NORMAL;
            }
            return TokenType.WHITE_SPACE;
        }

        // Line comment //
        if (c == '/' && myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '/') {
            myTokenEnd++;
            while (myTokenEnd < myBufferEnd) {
                char nc = myBuffer.charAt(myTokenEnd);
                if (nc == '\n' || nc == '\r') break;
                myTokenEnd++;
            }
            return DfmTokenTypes.COMMENT;
        }

        // Block comment { }
        if (c == '{') {
            while (myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) != '}') {
                myTokenEnd++;
            }
            if (myTokenEnd < myBufferEnd) myTokenEnd++; // consume }
            return DfmTokenTypes.COMMENT;
        }

        // Block comment (* *)
        if (c == '(' && myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '*') {
            myTokenEnd++;
            while (myTokenEnd + 1 < myBufferEnd) {
                if (myBuffer.charAt(myTokenEnd) == '*' && myBuffer.charAt(myTokenEnd + 1) == ')') {
                    myTokenEnd += 2;
                    break;
                }
                myTokenEnd++;
            }
            return DfmTokenTypes.COMMENT;
        }

        // String literal 'text'
        if (c == '\'') {
            while (myTokenEnd < myBufferEnd) {
                char sc = myBuffer.charAt(myTokenEnd);
                myTokenEnd++;
                if (sc == '\'') {
                    // Check for escaped quote ''
                    if (myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '\'') {
                        myTokenEnd++; // skip escaped quote
                    } else {
                        break; // end of string
                    }
                }
            }
            return DfmTokenTypes.STRING;
        }

        // Hex number $ABCD
        if (c == '$') {
            while (myTokenEnd < myBufferEnd) {
                char hc = myBuffer.charAt(myTokenEnd);
                if ((hc >= '0' && hc <= '9') || (hc >= 'a' && hc <= 'f') || (hc >= 'A' && hc <= 'F')) {
                    myTokenEnd++;
                } else {
                    break;
                }
            }
            return DfmTokenTypes.NUMBER;
        }

        // Number
        if (c >= '0' && c <= '9') {
            while (myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) >= '0' && myBuffer.charAt(myTokenEnd) <= '9') {
                myTokenEnd++;
            }
            // Check for decimal part
            if (myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) == '.') {
                int dotPos = myTokenEnd;
                myTokenEnd++;
                if (myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) >= '0' && myBuffer.charAt(myTokenEnd) <= '9') {
                    while (myTokenEnd < myBufferEnd && myBuffer.charAt(myTokenEnd) >= '0' && myBuffer.charAt(myTokenEnd) <= '9') {
                        myTokenEnd++;
                    }
                } else {
                    myTokenEnd = dotPos; // not a decimal, rollback
                }
            }
            return DfmTokenTypes.NUMBER;
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

            // Check for keywords first
            switch (word) {
                case "object":
                    myState = STATE_AFTER_OBJECT_KEYWORD;
                    return DfmTokenTypes.OBJECT;
                case "inherited":
                    myState = STATE_AFTER_OBJECT_KEYWORD;
                    return DfmTokenTypes.INHERITED;
                case "inline":
                    myState = STATE_AFTER_OBJECT_KEYWORD;
                    return DfmTokenTypes.INLINE;
                case "end":
                    myState = STATE_NORMAL;
                    return DfmTokenTypes.END;
                case "item":
                    return DfmTokenTypes.ITEM;
                case "true":
                case "false":
                    myState = STATE_NORMAL;
                    return DfmTokenTypes.BOOLEAN;
            }

            // Handle identifiers based on current state
            if (myState == STATE_AFTER_OBJECT_KEYWORD) {
                myState = STATE_AFTER_OBJECT_NAME;
                return DfmTokenTypes.OBJECT_NAME;
            } else if (myState == STATE_AFTER_COLON) {
                myState = STATE_NORMAL;
                return DfmTokenTypes.CLASS_NAME;
            } else if (myState == STATE_AFTER_EQUALS) {
                // Identifier after = is an object reference
                myState = STATE_NORMAL;
                return DfmTokenTypes.OBJECT_NAME;
            }

            // Check if this identifier is followed by = (property name)
            if (myState == STATE_NORMAL && isFollowedByEquals()) {
                return DfmTokenTypes.PROPERTY_NAME;
            }

            return DfmTokenTypes.IDENTIFIER;
        }

        // Single character tokens
        switch (c) {
            case '=':
                myState = STATE_AFTER_EQUALS;
                return DfmTokenTypes.EQUALS;
            case ':':
                if (myState == STATE_AFTER_OBJECT_NAME) {
                    myState = STATE_AFTER_COLON;
                }
                return DfmTokenTypes.COLON;
            case '[': return DfmTokenTypes.LBRACKET;
            case ']': return DfmTokenTypes.RBRACKET;
            case '(': return DfmTokenTypes.LPAREN;
            case ')': return DfmTokenTypes.RPAREN;
            case '<': return DfmTokenTypes.LT;
            case '>': return DfmTokenTypes.GT;
            case ',': return DfmTokenTypes.COMMA;
            case '.': return DfmTokenTypes.DOT;
            case '+': return DfmTokenTypes.PLUS;
            case '-': return DfmTokenTypes.MINUS;
            default: return TokenType.BAD_CHARACTER;
        }
    }

    /**
     * Check if the current token is followed by '=' (skipping whitespace).
     * Used to detect property names in "PropertyName = value" patterns.
     */
    private boolean isFollowedByEquals() {
        int pos = myTokenEnd;
        // Skip whitespace (but not newlines - property must be on same line)
        while (pos < myBufferEnd) {
            char c = myBuffer.charAt(pos);
            if (c == ' ' || c == '\t') {
                pos++;
            } else if (c == '=') {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }
}
