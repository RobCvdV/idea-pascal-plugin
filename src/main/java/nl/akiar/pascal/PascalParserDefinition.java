package nl.akiar.pascal;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import nl.akiar.pascal.parser.PascalStructuredParser;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.impl.PascalTypeDefinitionImpl;
import nl.akiar.pascal.psi.impl.PascalVariableDefinitionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Parser definition for Pascal language
 */
public class PascalParserDefinition implements ParserDefinition {
    public static final IStubFileElementType<PsiFileStub<PascalFile>> FILE =
            new IStubFileElementType<>("PASCAL_FILE", PascalLanguage.INSTANCE);

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
        return new PascalStructuredParser();
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
        if (node.getElementType() == PascalElementTypes.TYPE_DEFINITION) {
            return new PascalTypeDefinitionImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.VARIABLE_DEFINITION) {
            return new PascalVariableDefinitionImpl(node);
        }
        return new PascalPsiElement(node);
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new PascalFile(viewProvider);
    }
}
