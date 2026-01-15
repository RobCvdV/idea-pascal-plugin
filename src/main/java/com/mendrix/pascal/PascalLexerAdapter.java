package com.mendrix.pascal;

import com.intellij.lexer.FlexAdapter;

/**
 * Adapter for the Pascal lexer to integrate with IntelliJ's lexer interface
 */
public class PascalLexerAdapter extends FlexAdapter {
    public PascalLexerAdapter() {
        super(new PascalLexer());
    }
}
