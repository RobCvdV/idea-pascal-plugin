package nl.akiar.pascal;

import com.intellij.lang.Language;

/**
 * Language definition for Object Pascal (Delphi-style Pascal)
 */
public class PascalLanguage extends Language {
    public static final PascalLanguage INSTANCE = new PascalLanguage();

    private PascalLanguage() {
        super("ObjectPascal");
    }
}
