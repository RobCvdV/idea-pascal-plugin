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
import nl.akiar.pascal.parser.PascalSonarParser;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.impl.PascalTypeDefinitionImpl;
import nl.akiar.pascal.psi.impl.PascalVariableDefinitionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Parser definition for Pascal language
 */
public class PascalParserDefinition implements ParserDefinition {
    public static final IStubFileElementType<PsiFileStub<PascalFile>> FILE =
            new PascalStubFileElementType();

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
        return new PascalSonarParser();
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
        if (node.getElementType() == PascalElementTypes.ROUTINE_DECLARATION) {
            return new nl.akiar.pascal.psi.impl.PascalRoutineImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.PROPERTY_DEFINITION) {
            return new nl.akiar.pascal.psi.impl.PascalPropertyImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.ATTRIBUTE_DEFINITION) {
            return new nl.akiar.pascal.psi.impl.PascalAttributeImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.INTERFACE_GUID) {
            return new nl.akiar.pascal.psi.impl.PascalInterfaceGuidImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.TYPE_REFERENCE) {
            return new nl.akiar.pascal.psi.impl.PascalTypeReferenceElement(node);
        }
        if (node.getElementType() == PascalElementTypes.ROUTINE_SIGNATURE) {
            return new nl.akiar.pascal.psi.impl.PascalRoutineSignatureImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.METHOD_NAME_REFERENCE) {
            return new nl.akiar.pascal.psi.impl.PascalMethodNameReferenceImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.FORMAL_PARAMETER_LIST) {
            return new nl.akiar.pascal.psi.impl.PascalFormalParameterListImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.RETURN_TYPE) {
            return new nl.akiar.pascal.psi.impl.PascalReturnTypeImpl(node);
        }
        if (node.getElementType() == PascalElementTypes.CLASS_TYPE_REFERENCE) {
            return new nl.akiar.pascal.psi.impl.PascalClassTypeReferenceImpl(node);
        }
        return new PascalPsiElement(node);
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new PascalFile(viewProvider);
    }
}
