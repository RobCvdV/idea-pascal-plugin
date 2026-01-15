package com.mendrix.dfm;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Token types for DFM language
 */
public interface DfmTokenTypes {
    IElementType COMMENT = new DfmTokenType("COMMENT");
    
    IElementType OBJECT = new DfmTokenType("OBJECT");
    IElementType INHERITED = new DfmTokenType("INHERITED");
    IElementType INLINE = new DfmTokenType("INLINE");
    IElementType END = new DfmTokenType("END");
    IElementType ITEM = new DfmTokenType("ITEM");
    
    IElementType IDENTIFIER = new DfmTokenType("IDENTIFIER");
    IElementType OBJECT_NAME = new DfmTokenType("OBJECT_NAME");
    IElementType CLASS_NAME = new DfmTokenType("CLASS_NAME");
    IElementType PROPERTY_NAME = new DfmTokenType("PROPERTY_NAME");
    IElementType BOOLEAN = new DfmTokenType("BOOLEAN");
    IElementType STRING = new DfmTokenType("STRING");
    IElementType NUMBER = new DfmTokenType("NUMBER");
    
    IElementType EQUALS = new DfmTokenType("EQUALS");
    IElementType COLON = new DfmTokenType("COLON");
    IElementType LBRACKET = new DfmTokenType("LBRACKET");
    IElementType RBRACKET = new DfmTokenType("RBRACKET");
    IElementType LPAREN = new DfmTokenType("LPAREN");
    IElementType RPAREN = new DfmTokenType("RPAREN");
    IElementType LT = new DfmTokenType("LT");
    IElementType GT = new DfmTokenType("GT");
    IElementType COMMA = new DfmTokenType("COMMA");
    IElementType DOT = new DfmTokenType("DOT");
    IElementType PLUS = new DfmTokenType("PLUS");
    IElementType MINUS = new DfmTokenType("MINUS");
    
    IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;
    IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
}

