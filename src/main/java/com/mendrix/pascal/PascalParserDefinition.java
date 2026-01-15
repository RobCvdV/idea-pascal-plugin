package com.mendrix.pascal;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Parser definition for Pascal language
 */
public class PascalParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(PascalLanguage.INSTANCE);

    private static final TokenSet WHITE_SPACES = TokenSet.create(PascalTokenTypes.WHITE_SPACE);
    private static final TokenSet COMMENTS = TokenSet.create(
            PascalTokenTypes.LINE_COMMENT,
            PascalTokenTypes.BLOCK_COMMENT,
            PascalTokenTypes.COMPILER_DIRECTIVE
    );
    private static final TokenSet STRINGS = TokenSet.create(
            PascalTokenTypes.STRING_LITERAL,
            PascalTokenTypes.CHAR_LITERAL
    );

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new PascalLexerAdapter();
    }

    @NotNull
    @Override
    public PsiParser createParser(Project project) {
        return new PascalParser();
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return new PascalPsiElement(node);
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new PascalFile(viewProvider);
    }
}
