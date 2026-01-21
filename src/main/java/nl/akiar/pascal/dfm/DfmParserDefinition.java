package nl.akiar.pascal.dfm;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Parser definition for DFM language
 */
public class DfmParserDefinition implements ParserDefinition {
    private static final Logger LOG = Logger.getInstance("#nl.akiar.pascal.dfm.DfmParserDefinition");
    public static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
    public static final TokenSet COMMENTS = TokenSet.create(DfmTokenTypes.COMMENT);
    public static final TokenSet STRINGS = TokenSet.create(DfmTokenTypes.STRING);

    public static final IFileElementType FILE = new IFileElementType(DfmLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        LOG.info("DFM-PLUGIN: createLexer called");
        try {
            return new DfmLexerAdapter();
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("DFM-PLUGIN: Error creating lexer", e);
            throw e;
        }
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
    public PsiParser createParser(final Project project) {
        LOG.info("DFM-PLUGIN: createParser called");
        try {
            return new DfmParser();
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("DFM-PLUGIN: Error creating parser", e);
            throw e;
        }
    }

    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
//         LOG.info("DFM-PLUGIN: createFile called for " + viewProvider.getVirtualFile().getName());
        try {
            return new DfmFile(viewProvider);
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("DFM-PLUGIN: Error creating file", e);
            throw e;
        }
    }

    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return new DfmPsiElement(node);
    }
}

