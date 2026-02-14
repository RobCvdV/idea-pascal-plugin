package nl.akiar.pascal.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.patterns.PlatformPatterns;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;

/**
 * Completion contributor for Object Pascal.
 * Registers providers for different completion contexts:
 * member access (after DOT), type positions, uses clauses, and general identifiers.
 */
public class PascalCompletionContributor extends CompletionContributor {

    public PascalCompletionContributor() {
        // Member completion (after DOT) — highest priority
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(PascalTokenTypes.IDENTIFIER)
                        .afterLeaf(PlatformPatterns.psiElement(PascalTokenTypes.DOT)),
                new PascalMemberCompletionProvider());

        // Type completion (inside TYPE_REFERENCE)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(PascalTokenTypes.IDENTIFIER)
                        .inside(PlatformPatterns.psiElement(PascalElementTypes.TYPE_REFERENCE)),
                new PascalTypeCompletionProvider());

        // Uses clause completion (inside USES_SECTION or UNIT_REFERENCE)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(PascalTokenTypes.IDENTIFIER)
                        .inside(PlatformPatterns.psiElement(PascalElementTypes.USES_SECTION)),
                new PascalUsesCompletionProvider());

        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(PascalTokenTypes.IDENTIFIER)
                        .inside(PlatformPatterns.psiElement(PascalElementTypes.UNIT_REFERENCE)),
                new PascalUsesCompletionProvider());

        // General identifier completion (broadest scope, lowest priority)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(PascalTokenTypes.IDENTIFIER),
                new PascalIdentifierCompletionProvider());
    }
}
