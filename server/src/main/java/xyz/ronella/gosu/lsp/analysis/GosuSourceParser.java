package xyz.ronella.gosu.lsp.analysis;

import xyz.ronella.gosu.lsp.grammar.GosuLanguageLexer;
import xyz.ronella.gosu.lsp.grammar.GosuLanguageParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the ANTLR4 Gosu parser, returning a {@link ParseResult} with the
 * parse tree and any syntax errors found.
 */
public class GosuSourceParser {

    // ── Parse result ────────────────────────────────────────────────────

    public static final class ParseResult {
        public final GosuLanguageParser.CompilationUnitContext tree;
        public final List<ParseError> errors;
        public final GosuLanguageParser parser;
        public final CommonTokenStream tokens;

        public ParseResult(GosuLanguageParser.CompilationUnitContext tree,
                           List<ParseError> errors,
                           GosuLanguageParser parser,
                           CommonTokenStream tokens) {
            this.tree   = tree;
            this.errors = errors;
            this.parser = parser;
            this.tokens = tokens;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    // ── Parse error ──────────────────────────────────────────────────────

    public static final class ParseError {
        /** 1-based line number as reported by ANTLR. */
        public final int line;
        /** 0-based character position within the line. */
        public final int charPositionInLine;
        public final String message;

        public ParseError(int line, int charPositionInLine, String message) {
            this.line               = line;
            this.charPositionInLine = charPositionInLine;
            this.message            = message;
        }
    }

    // ── Main parse method ────────────────────────────────────────────────

    public ParseResult parse(String content) {
        CharStream input = CharStreams.fromString(content != null ? content : "");

        List<ParseError> errors = new ArrayList<>();

        // Custom error listener that collects errors instead of printing them
        ANTLRErrorListener errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer,
                                    Object offendingSymbol,
                                    int line, int charPositionInLine,
                                    String msg, RecognitionException e) {
                errors.add(new ParseError(line, charPositionInLine, msg));
            }
        };

        GosuLanguageLexer lexer = new GosuLanguageLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GosuLanguageParser parser = new GosuLanguageParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        // Error-recovery: try to keep going even on syntax errors
        parser.setErrorHandler(new DefaultErrorStrategy());

        GosuLanguageParser.CompilationUnitContext tree = parser.compilationUnit();
        return new ParseResult(tree, errors, parser, tokens);
    }
}
