package nl.akiar.pascal.dfm;

import com.intellij.lang.Language;

/**
 * Language definition for Delphi Form (DFM) files
 */
public class DfmLanguage extends Language {
    public static final DfmLanguage INSTANCE = new DfmLanguage();

    private DfmLanguage() {
        super("DFM");
    }
}

