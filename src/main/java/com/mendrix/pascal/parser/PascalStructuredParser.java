package com.mendrix.pascal.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.mendrix.pascal.PascalTokenTypes;
import com.mendrix.pascal.psi.PascalElementTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Structured parser for Pascal files.
 * Creates PSI nodes for type definitions (class, record, interface).
 * Everything else is parsed as flat tokens for syntax highlighting.
 */
public class PascalStructuredParser implements PsiParser {
    private static final Logger LOG = Logger.getInstance(PascalStructuredParser.class);

    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker rootMarker = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == PascalTokenTypes.KW_TYPE) {
                parseTypeSection(builder);
            } else {
                builder.advanceLexer();
            }
        }

        rootMarker.done(root);
        return builder.getTreeBuilt();
    }

    /**
     * Parse a type section, looking for type definitions.
     * type
     *   TMyClass = class...end;
     *   TMyRecord = record...end;
     */
    private void parseTypeSection(PsiBuilder builder) {

        // Consume 'type' keyword
        builder.advanceLexer();
        skipWhitespaceAndComments(builder);

        // Look for type definitions until we hit another section keyword
        while (!builder.eof() && !isSectionKeyword(builder.getTokenType())) {
            if (builder.getTokenType() == PascalTokenTypes.IDENTIFIER) {
                // Check if this looks like a type definition: IDENTIFIER = class|record|interface
                if (isTypeDefinitionStart(builder)) {
                    parseTypeDefinition(builder);
                } else if (isOtherTypeDefinition(builder)) {
                    // Skip other type definitions (like procedure types, type aliases)
                    // by consuming until semicolon
                    skipToSemicolon(builder);
                } else {
                    builder.advanceLexer();
                }
            } else {
                builder.advanceLexer();
            }
        }
    }

    /**
     * Check if current position is a type definition that's not class/record/interface.
     * Pattern: IDENTIFIER = (something other than class/record/interface)
     */
    private boolean isOtherTypeDefinition(PsiBuilder builder) {
        if (builder.getTokenType() != PascalTokenTypes.IDENTIFIER) {
            return false;
        }

        PsiBuilder.Marker lookAhead = builder.mark();
        builder.advanceLexer(); // consume identifier
        skipWhitespaceAndComments(builder);

        boolean isOtherType = builder.getTokenType() == PascalTokenTypes.EQ;
        lookAhead.rollbackTo();
        return isOtherType;
    }

    /**
     * Skip tokens until we hit a semicolon (end of type definition).
     */
    private void skipToSemicolon(PsiBuilder builder) {
        while (!builder.eof()) {
            if (builder.getTokenType() == PascalTokenTypes.SEMI) {
                builder.advanceLexer(); // consume the semicolon
                return;
            }
            builder.advanceLexer();
        }
    }

    /**
     * Check if current position is the start of a type definition.
     * Pattern: IDENTIFIER = class|record|interface (but NOT "class of" which is a metaclass)
     */
    private boolean isTypeDefinitionStart(PsiBuilder builder) {
        if (builder.getTokenType() != PascalTokenTypes.IDENTIFIER) {
            return false;
        }

        // Look ahead for '=' followed by class/record/interface
        PsiBuilder.Marker lookAhead = builder.mark();
        builder.advanceLexer(); // consume identifier
        skipWhitespaceAndComments(builder);

        boolean isTypeDef = false;
        if (builder.getTokenType() == PascalTokenTypes.EQ) {
            builder.advanceLexer(); // consume '='
            skipWhitespaceAndComments(builder);

            IElementType afterEquals = builder.getTokenType();
            if (afterEquals == PascalTokenTypes.KW_CLASS) {
                // Check it's not "class of" (metaclass type alias)
                builder.advanceLexer(); // consume 'class'
                skipWhitespaceAndComments(builder);
                isTypeDef = builder.getTokenType() != PascalTokenTypes.KW_OF;
            } else {
                isTypeDef = afterEquals == PascalTokenTypes.KW_RECORD
                        || afterEquals == PascalTokenTypes.KW_INTERFACE;
            }
        }

        lookAhead.rollbackTo();
        return isTypeDef;
    }

    /**
     * Parse a type definition: TypeName = class|record|interface...end;
     */
    private void parseTypeDefinition(PsiBuilder builder) {
        PsiBuilder.Marker typeMarker = builder.mark();

        // Get type name for logging
        String typeName = builder.getTokenText();

        // Consume identifier
        builder.advanceLexer();
        skipWhitespaceAndComments(builder);

        // Consume '='
        if (builder.getTokenType() == PascalTokenTypes.EQ) {
            builder.advanceLexer();
            skipWhitespaceAndComments(builder);
        }

        // Get type kind for logging
        String typeKind = builder.getTokenText();

        // Consume class/record/interface keyword
        builder.advanceLexer();

        // For forward declarations, we're done after semicolon
        // Forward declarations can be:
        //   TFoo = class;
        //   TFoo = class(TBar);  <- with inheritance but no body
        // For full declarations, consume until 'end;'
        skipWhitespaceAndComments(builder);

        // Handle optional inheritance: class(Parent) or interface(Parent)
        if (builder.getTokenType() == PascalTokenTypes.LPAREN) {
            // Skip past the inheritance clause: (Parent, IInterface, ...)
            int parenDepth = 1;
            builder.advanceLexer(); // consume '('
            while (!builder.eof() && parenDepth > 0) {
                if (builder.getTokenType() == PascalTokenTypes.LPAREN) {
                    parenDepth++;
                } else if (builder.getTokenType() == PascalTokenTypes.RPAREN) {
                    parenDepth--;
                }
                builder.advanceLexer();
            }
            skipWhitespaceAndComments(builder);
        }

        // Handle optional GUID for interfaces: ['{...}']
        if (builder.getTokenType() == PascalTokenTypes.LBRACKET) {
            while (!builder.eof() && builder.getTokenType() != PascalTokenTypes.RBRACKET) {
                builder.advanceLexer();
            }
            if (builder.getTokenType() == PascalTokenTypes.RBRACKET) {
                builder.advanceLexer();
            }
            skipWhitespaceAndComments(builder);
        }

        if (builder.getTokenType() == PascalTokenTypes.SEMI) {
            // Forward declaration (with or without inheritance)
            LOG.info("[PascalParser] Parsed forward declaration: " + typeName + " = " + typeKind);
            builder.advanceLexer();
        } else {
            // Full declaration - consume until 'end' followed by semicolon
            int nestedLevel = 1;
            while (!builder.eof() && nestedLevel > 0) {
                IElementType current = builder.getTokenType();

                // Track nested structures that have their own 'end'
                if (current == PascalTokenTypes.KW_BEGIN
                        || current == PascalTokenTypes.KW_RECORD
                        || current == PascalTokenTypes.KW_CASE
                        || current == PascalTokenTypes.KW_CLASS
                        || current == PascalTokenTypes.KW_INTERFACE) {
                    // Check if this is a nested type definition (has 'end'), not just a type reference
                    // A nested type has: class/record/interface NOT followed by a semicolon
                    if (current == PascalTokenTypes.KW_CLASS
                            || current == PascalTokenTypes.KW_RECORD
                            || current == PascalTokenTypes.KW_INTERFACE) {
                        // Look ahead to see if this is a type with body or just a reference
                        PsiBuilder.Marker lookAhead = builder.mark();
                        builder.advanceLexer();
                        skipWhitespaceAndComments(builder);
                        boolean hasBody = builder.getTokenType() != PascalTokenTypes.SEMI
                                && builder.getTokenType() != PascalTokenTypes.RPAREN
                                && builder.getTokenType() != PascalTokenTypes.COMMA;
                        lookAhead.rollbackTo();

                        if (hasBody) {
                            nestedLevel++;
                        }
                    } else {
                        nestedLevel++;
                    }
                } else if (current == PascalTokenTypes.KW_END) {
                    nestedLevel--;
                    if (nestedLevel == 0) {
                        builder.advanceLexer(); // consume 'end'
                        skipWhitespaceAndComments(builder);
                        if (builder.getTokenType() == PascalTokenTypes.SEMI) {
                            builder.advanceLexer(); // consume ';'
                        }
                        break;
                    }
                }
                builder.advanceLexer();
            }
            LOG.info("[PascalParser] Parsed type definition: " + typeName + " = " + typeKind);
        }

        typeMarker.done(PascalElementTypes.TYPE_DEFINITION);
    }

    private void skipWhitespaceAndComments(PsiBuilder builder) {
        while (!builder.eof()) {
            IElementType type = builder.getTokenType();
            if (type == PascalTokenTypes.WHITE_SPACE
                    || type == PascalTokenTypes.LINE_COMMENT
                    || type == PascalTokenTypes.BLOCK_COMMENT
                    || type == PascalTokenTypes.COMPILER_DIRECTIVE) {
                builder.advanceLexer();
            } else {
                break;
            }
        }
    }

    /**
     * Check if token is a section keyword that ends the type section.
     */
    private boolean isSectionKeyword(IElementType type) {
        return type == PascalTokenTypes.KW_VAR
                || type == PascalTokenTypes.KW_CONST
                || type == PascalTokenTypes.KW_TYPE
                || type == PascalTokenTypes.KW_PROCEDURE
                || type == PascalTokenTypes.KW_FUNCTION
                || type == PascalTokenTypes.KW_CONSTRUCTOR
                || type == PascalTokenTypes.KW_DESTRUCTOR
                || type == PascalTokenTypes.KW_IMPLEMENTATION
                || type == PascalTokenTypes.KW_INITIALIZATION
                || type == PascalTokenTypes.KW_FINALIZATION
                || type == PascalTokenTypes.KW_BEGIN
                || type == PascalTokenTypes.KW_END;
    }
}
