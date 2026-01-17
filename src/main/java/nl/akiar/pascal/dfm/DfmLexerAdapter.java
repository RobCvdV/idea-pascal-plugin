package nl.akiar.pascal.dfm;

import com.intellij.lexer.FlexAdapter;

/**
 * Lexer adapter for DFM files that wraps the JFlex-generated DfmLexer
 */
public class DfmLexerAdapter extends FlexAdapter {
    public DfmLexerAdapter() {
        super(new DfmLexer());
    }
}