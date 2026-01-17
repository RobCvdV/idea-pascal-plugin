package nl.akiar.pascal.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
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
        // LOG.info("[PascalParser] Starting parse of root: " + root);
        PsiBuilder.Marker rootMarker = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == PascalTokenTypes.KW_TYPE) {
                // LOG.info("[PascalParser] Found 'type' section at offset " + builder.getCurrentOffset());
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
        // LOG.info("[PascalParser] Entering parseTypeSection at offset " + builder.getCurrentOffset());
        // Consume 'type' keyword
        builder.advanceLexer();
        skipWhitespaceAndComments(builder);

        // Look for type definitions until we hit another section keyword
        while (!builder.eof() && !isSectionKeyword(builder.getTokenType())) {
            IElementType tokenType = builder.getTokenType();
            if (tokenType == PascalTokenTypes.IDENTIFIER) {
                String id = builder.getTokenText();
                // LOG.info("[PascalParser] Evaluating identifier in type section: '" + id + "' at offset " + builder.getCurrentOffset());
                // Check if this looks like a type definition: IDENTIFIER = class|record|interface
                if (isTypeDefinitionStart(builder)) {
                    // LOG.info("[PascalParser] Identified type definition start: " + id);
                    parseTypeDefinition(builder);
                } else if (isOtherTypeDefinition(builder)) {
                    // LOG.info("[PascalParser] Identified other type definition: " + id);
                    parseOtherTypeDefinition(builder);
                } else {
                    // LOG.info("[PascalParser] Identifier '" + id + "' is not a type definition start, skipping");
                    builder.advanceLexer();
                }
            } else {
                // LOG.info("[PascalParser] Skipping non-identifier token in type section: " + tokenType + " ('" + builder.getTokenText() + "')");
                builder.advanceLexer();
            }
            skipWhitespaceAndComments(builder);
        }
        // LOG.info("[PascalParser] Leaving parseTypeSection at offset " + builder.getCurrentOffset());
    }

    /**
     * Check if current position is a type definition that's not class/record/interface.
     * Pattern: IDENTIFIER [<...>] = (something other than class/record/interface)
     */
    private boolean isOtherTypeDefinition(PsiBuilder builder) {
        if (builder.getTokenType() != PascalTokenTypes.IDENTIFIER) {
            return false;
        }

        String id = builder.getTokenText();
        PsiBuilder.Marker lookAhead = builder.mark();
        builder.advanceLexer(); // consume identifier
        skipWhitespaceAndComments(builder);

        // Handle generic parameters: <T, K: class>
        if (builder.getTokenType() == PascalTokenTypes.LT) {
            // LOG.info("[PascalParser] isOtherTypeDefinition lookahead: found '<' for " + id);
            int depth = 1;
            builder.advanceLexer();
            while (!builder.eof() && depth > 0) {
                if (builder.getTokenType() == PascalTokenTypes.LT) depth++;
                else if (builder.getTokenType() == PascalTokenTypes.GT) depth--;
                builder.advanceLexer();
            }
            skipWhitespaceAndComments(builder);
        }

        boolean isOtherType = builder.getTokenType() == PascalTokenTypes.EQ;
        // if (isOtherType) {
        //     LOG.info("[PascalParser] isOtherTypeDefinition lookahead: " + id + " has '=' at offset " + builder.getCurrentOffset());
        // }
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
     * Parse other type definitions (aliases, procedure types, etc.)
     * to at least capture their name and generic parameters.
     */
    private void parseOtherTypeDefinition(PsiBuilder builder) {
        PsiBuilder.Marker typeMarker = builder.mark();
        String typeName = builder.getTokenText();

        // Consume identifier
        builder.advanceLexer();
        skipWhitespaceAndComments(builder);

        // Consume generic parameters if present: <T>
        if (builder.getTokenType() == PascalTokenTypes.LT) {
            parseGenericParameters(builder);
        }

        // Consume '='
        if (builder.getTokenType() == PascalTokenTypes.EQ) {
            builder.advanceLexer();
            skipWhitespaceAndComments(builder);
        }

        LOG.info("[PascalParser] Parsing other type definition: " + typeName + " starting at " + builder.getTokenText());

        // Handle procedure types and method references
        // e.g., TMyProc = procedure(A: Integer);
        //       TMyRef = reference to procedure;
        if (builder.getTokenType() == PascalTokenTypes.KW_REFERENCE) {
            builder.advanceLexer();
            skipWhitespaceAndComments(builder);
            if (builder.getTokenType() == PascalTokenTypes.KW_TO) {
                builder.advanceLexer();
                skipWhitespaceAndComments(builder);
            }
        }

        if (builder.getTokenType() == PascalTokenTypes.KW_PROCEDURE || builder.getTokenType() == PascalTokenTypes.KW_FUNCTION) {
            builder.advanceLexer();
            skipWhitespaceAndComments(builder);
            // Optional parameters (...)
            if (builder.getTokenType() == PascalTokenTypes.LPAREN) {
                int parenDepth = 1;
                builder.advanceLexer();
                while (!builder.eof() && parenDepth > 0) {
                    if (builder.getTokenType() == PascalTokenTypes.LPAREN) parenDepth++;
                    else if (builder.getTokenType() == PascalTokenTypes.RPAREN) parenDepth--;
                    builder.advanceLexer();
                }
                skipWhitespaceAndComments(builder);
            }
            // Optional return type for function: : ReturnType
            if (builder.getTokenType() == PascalTokenTypes.COLON) {
                builder.advanceLexer();
                skipWhitespaceAndComments(builder);
                if (builder.getTokenType() == PascalTokenTypes.IDENTIFIER) {
                    builder.advanceLexer();
                    skipWhitespaceAndComments(builder);
                    // Might be generic: TResult<T>
                    if (builder.getTokenType() == PascalTokenTypes.LT) {
                        int ltDepth = 1;
                        builder.advanceLexer();
                        while (!builder.eof() && ltDepth > 0) {
                            if (builder.getTokenType() == PascalTokenTypes.LT) ltDepth++;
                            else if (builder.getTokenType() == PascalTokenTypes.GT) ltDepth--;
                            builder.advanceLexer();
                        }
                    }
                }
            }
            // Optional directives: stdcall; static; etc.
            while (!builder.eof() && builder.getTokenType() != PascalTokenTypes.SEMI && !isSectionKeyword(builder.getTokenType())) {
                builder.advanceLexer();
                skipWhitespaceAndComments(builder);
            }
        }

        // Consume until semicolon
        skipToSemicolon(builder);

        typeMarker.done(PascalElementTypes.TYPE_DEFINITION);
    }

    /**
     * Check if current position is the start of a type definition.
     * Pattern: IDENTIFIER [<...>] = class|record|interface (but NOT "class of" which is a metaclass)
     */
    private boolean isTypeDefinitionStart(PsiBuilder builder) {
        if (builder.getTokenType() != PascalTokenTypes.IDENTIFIER) {
            return false;
        }

        String id = builder.getTokenText();
        // Look ahead for '=' followed by class/record/interface
        PsiBuilder.Marker lookAhead = builder.mark();
        builder.advanceLexer(); // consume identifier
        skipWhitespaceAndComments(builder);

        // Handle generic parameters: <T, K: class>
        if (builder.getTokenType() == PascalTokenTypes.LT) {
            // LOG.info("[PascalParser] isTypeDefinitionStart lookahead: found '<' for " + id);
            int depth = 1;
            builder.advanceLexer();
            while (!builder.eof() && depth > 0) {
                if (builder.getTokenType() == PascalTokenTypes.LT) depth++;
                else if (builder.getTokenType() == PascalTokenTypes.GT) depth--;
                builder.advanceLexer();
            }
            skipWhitespaceAndComments(builder);
        }

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
                // if (!isTypeDef) {
                //     LOG.info("[PascalParser] isTypeDefinitionStart lookahead: " + id + " = class of (metaclass), not a main type definition");
                // } else {
                //     LOG.info("[PascalParser] isTypeDefinitionStart lookahead: " + id + " = class (main type definition)");
                // }
            } else {
                isTypeDef = afterEquals == PascalTokenTypes.KW_RECORD
                        || afterEquals == PascalTokenTypes.KW_INTERFACE;
                // if (isTypeDef) {
                //     LOG.info("[PascalParser] isTypeDefinitionStart lookahead: " + id + " = " + afterEquals + " (main type definition)");
                // }
            }
        }

        lookAhead.rollbackTo();
        return isTypeDef;
    }

    /**
     * Parse a type definition: TypeName[<...>] = class|record|interface...end;
     */
    private void parseTypeDefinition(PsiBuilder builder) {
        PsiBuilder.Marker typeMarker = builder.mark();

        // Get type name for logging
        String typeName = builder.getTokenText();
        // LOG.info("[PascalParser] Starting parseTypeDefinition for: " + typeName);

        // Consume identifier
        builder.advanceLexer();
        skipWhitespaceAndComments(builder);

        // Consume generic parameters if present: <T: constructor>
        if (builder.getTokenType() == PascalTokenTypes.LT) {
            // LOG.info("[PascalParser] Found generic parameters for: " + typeName);
            parseGenericParameters(builder);
        }

        // Consume '='
        if (builder.getTokenType() == PascalTokenTypes.EQ) {
            builder.advanceLexer();
            skipWhitespaceAndComments(builder);
        } else {
            // LOG.info("[PascalParser] Missing '=' in type definition for: " + typeName + ", found: " + builder.getTokenText());
        }

        // Get type kind for logging
        IElementType kindToken = builder.getTokenType();
        String typeKind = kindToken != null ? kindToken.toString() : "UNKNOWN";

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
            // LOG.info("[PascalParser] Parsed forward declaration: " + typeName + " = " + typeKind);
            builder.advanceLexer();
        } else if (builder.getTokenType() == PascalTokenTypes.SEMI) {
             // LOG.info("[PascalParser] Parsed forward declaration: " + typeName + " = " + typeKind);
             builder.advanceLexer();
        } else {
            // Full declaration - consume until 'end' followed by semicolon
            // LOG.info("[PascalParser] Parsing full declaration body for: " + typeName + " at offset " + builder.getCurrentOffset());
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
                        // "class function", "class procedure", etc. are method declarations, NOT nested classes
                        PsiBuilder.Marker lookAhead = builder.mark();
                        builder.advanceLexer();
                        skipWhitespaceAndComments(builder);
                        IElementType afterClass = builder.getTokenType();
                        boolean isClassMethod = afterClass == PascalTokenTypes.KW_FUNCTION
                                || afterClass == PascalTokenTypes.KW_PROCEDURE
                                || afterClass == PascalTokenTypes.KW_CONSTRUCTOR
                                || afterClass == PascalTokenTypes.KW_DESTRUCTOR
                                || afterClass == PascalTokenTypes.KW_PROPERTY
                                || afterClass == PascalTokenTypes.KW_VAR;
                        boolean hasBody = !isClassMethod
                                && afterClass != PascalTokenTypes.SEMI
                                && afterClass != PascalTokenTypes.RPAREN
                                && afterClass != PascalTokenTypes.COMMA;
                        lookAhead.rollbackTo();

                        if (hasBody) {
                            // LOG.info("[PascalParser] body: found nested structure start: " + current + ", nestedLevel -> " + (nestedLevel + 1));
                            nestedLevel++;
                        }
                    } else {
                        // LOG.info("[PascalParser] body: found structure start: " + current + ", nestedLevel -> " + (nestedLevel + 1));
                        nestedLevel++;
                    }
                } else if (current == PascalTokenTypes.KW_END) {
                    nestedLevel--;
                    // LOG.info("[PascalParser] body: found 'end', nestedLevel -> " + nestedLevel);
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
            // LOG.info("[PascalParser] Parsed type definition: " + typeName + " = " + typeKind + " at offset " + builder.getCurrentOffset());
        }

        typeMarker.done(PascalElementTypes.TYPE_DEFINITION);
    }

    private void parseGenericParameters(PsiBuilder builder) {
        // LOG.info("[PascalParser] Entering parseGenericParameters at '" + builder.getTokenText() + "' (" + builder.getTokenType() + ")");
        builder.advanceLexer(); // consume '<'
        skipWhitespaceAndComments(builder);

        while (!builder.eof() && builder.getTokenType() != PascalTokenTypes.GT) {
            IElementType tokenType = builder.getTokenType();
            String tokenText = builder.getTokenText();
            // LOG.info("[PascalParser] Generic parameter loop, current token: '" + tokenText + "' (" + tokenType + ")");

            if (tokenType == PascalTokenTypes.IDENTIFIER) {
                // LOG.info("[PascalParser] Found generic parameter identifier: " + tokenText);
                PsiBuilder.Marker paramMarker = builder.mark();
                builder.advanceLexer();
                paramMarker.done(PascalElementTypes.GENERIC_PARAMETER);
            } else {
                // LOG.info("[PascalParser] Generic parameter skip token: " + (tokenType != null ? tokenType.toString() : "null") + " ('" + tokenText + "')");
                builder.advanceLexer();
            }
            skipWhitespaceAndComments(builder);

            IElementType nextType = builder.getTokenType();
            if (nextType == PascalTokenTypes.COMMA) {
                // LOG.info("[PascalParser] Found comma in generic parameters");
                builder.advanceLexer();
                skipWhitespaceAndComments(builder);
            } else if (nextType == PascalTokenTypes.COLON) {
                // Constraint: T: class, constructor, TSomeBase
                // LOG.info("[PascalParser] Found colon, parsing generic constraint for '" + tokenText + "'");
                builder.advanceLexer();
                skipWhitespaceAndComments(builder);
                while (!builder.eof() && builder.getTokenType() != PascalTokenTypes.COMMA && builder.getTokenType() != PascalTokenTypes.GT) {
                    // LOG.info("[PascalParser] Consuming constraint token: " + builder.getTokenType() + " ('" + builder.getTokenText() + "')");
                    builder.advanceLexer();
                    skipWhitespaceAndComments(builder);
                }
                // After constraints, we might have a comma before the next parameter
                if (builder.getTokenType() == PascalTokenTypes.COMMA) {
                    // LOG.info("[PascalParser] Found comma after constraint");
                    builder.advanceLexer();
                    skipWhitespaceAndComments(builder);
                }
            }
        }

        if (builder.getTokenType() == PascalTokenTypes.GT) {
            // LOG.info("[PascalParser] Found closing '>'");
            builder.advanceLexer();
        } else {
            // LOG.info("[PascalParser] Warning: Generic parameters did not end with '>', found: '" + builder.getTokenText() + "' (" + builder.getTokenType() + ")");
        }
        skipWhitespaceAndComments(builder);
        // LOG.info("[PascalParser] Leaving parseGenericParameters");
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
