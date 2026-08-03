package xyz.ronella.gosu.lsp.handlers;

import xyz.ronella.gosu.lsp.analysis.GosuSourceParser;
import xyz.ronella.gosu.lsp.document.DocumentManager;
import xyz.ronella.gosu.lsp.grammar.GosuLanguageLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.*;

import java.util.Collections;
import java.util.List;

/**
 * Handles textDocument/formatting and textDocument/rangeFormatting requests.
 *
 * Uses a token-stream pass over the ANTLR-parsed token list to reconstruct
 * the source with consistent indentation and spacing.  Whitespace is skipped
 * by the lexer (-> skip), so all spacing is derived from token-type rules.
 *
 * Known limitation: {@code <} and {@code >} tokens are not given spaces
 * because they double as generic-type delimiters; relational comparisons
 * such as {@code x < y} will therefore appear without spaces.
 */
public class FormattingHandler {

    private final DocumentManager documents;
    private final GosuSourceParser sourceParser = new GosuSourceParser();

    /** Creates a new {@code FormattingHandler} backed by the given {@link DocumentManager}. */
    public FormattingHandler(DocumentManager documents) {
        this.documents = documents;
    }

    // ── Public API ────────────────────────────────────────────────

    /** Returns a full-document formatting edit for the document at {@code uri}. */
    public List<TextEdit> format(String uri, FormattingOptions opts) {
        String content = documents.getContent(uri);
        if (content == null || content.isEmpty()) return Collections.emptyList();

        String formatted = reformat(content, opts);
        if (formatted.equals(content)) return Collections.emptyList();

        String[] lines = content.split("\n", -1);
        Range fullDoc = new Range(
                new Position(0, 0),
                new Position(lines.length - 1, lines[lines.length - 1].length()));
        return List.of(new TextEdit(fullDoc, formatted));
    }

    /** Returns a range-scoped formatting edit for the given lines in {@code uri}. */
    public List<TextEdit> formatRange(String uri, Range range, FormattingOptions opts) {
        String content = documents.getContent(uri);
        if (content == null || content.isEmpty()) return Collections.emptyList();

        String[] lines = content.split("\n", -1);
        int start = Math.max(0, range.getStart().getLine());
        int end   = Math.min(range.getEnd().getLine(), lines.length - 1);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            sb.append(lines[i]);
            if (i < end) sb.append('\n');
        }
        String rangeText = sb.toString();
        String formatted  = reformat(rangeText, opts);
        if (formatted.equals(rangeText)) return Collections.emptyList();

        Range editRange = new Range(new Position(start, 0), new Position(end, lines[end].length()));
        return List.of(new TextEdit(editRange, formatted));
    }

    // ── Core token-stream formatter ───────────────────────────────────────

    private String reformat(String content, FormattingOptions opts) {
        GosuSourceParser.ParseResult pr = sourceParser.parse(content);
        pr.tokens.fill();
        List<Token> tokens = pr.tokens.getTokens();

        String indent = buildIndentUnit(opts);
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean atLineStart = false;

        for (int i = 0; i < tokens.size(); i++) {
            Token tok     = tokens.get(i);
            int   type    = tok.getType();
            if (type == Token.EOF) break;

            int prevType = i > 0 ? tokens.get(i - 1).getType() : -1;
            int nextType = peekType(tokens, i + 1);

            // ── Pre-token ─────────────────────────────────────────────

            if (type == GosuLanguageLexer.RBRACE) {
                depth = Math.max(0, depth - 1);
            }

            if (atLineStart) {
                out.append(indent.repeat(depth));
                atLineStart = false;
            } else if (i > 0 && needsSpaceBefore(prevType, type)) {
                out.append(' ');
            }

            // ── Emit token ────────────────────────────────────────────

            out.append(tok.getText());

            // ── Post-token ────────────────────────────────────────────

            if (type == GosuLanguageLexer.LBRACE) {
                depth++;
                if (nextType != GosuLanguageLexer.RBRACE) {
                    out.append('\n');
                    atLineStart = true;
                }
            } else if (type == GosuLanguageLexer.RBRACE) {
                // Do not break the line when a join-word (else/catch/finally) follows
                if (!isJoinWord(nextType) && nextType != Token.EOF) {
                    out.append('\n');
                    atLineStart = true;
                }
            } else if (type == GosuLanguageLexer.SEMI) {
                if (nextType != GosuLanguageLexer.RBRACE && nextType != Token.EOF) {
                    out.append('\n');
                    atLineStart = true;
                }
            }
        }

        return out.toString().stripTrailing();
    }

    // ── Spacing decision ──────────────────────────────────────────────────

    private static boolean needsSpaceBefore(int prev, int curr) {
        // Compound operator sequences (no space inside)
        if (isCompoundPair(prev, curr)) return false;

        // Never space before
        if (curr == GosuLanguageLexer.SEMI)            return false;
        if (curr == GosuLanguageLexer.COMMA)           return false;
        if (curr == GosuLanguageLexer.RPAREN)          return false;
        if (curr == GosuLanguageLexer.RBRACK)          return false;
        if (curr == GosuLanguageLexer.DOT)             return false;
        if (curr == GosuLanguageLexer.QUESTION_DOT)    return false;
        if (curr == GosuLanguageLexer.STAR_DOT)        return false;
        if (curr == GosuLanguageLexer.LBRACK)          return false;
        if (curr == GosuLanguageLexer.HASH)            return false;
        if (curr == GosuLanguageLexer.PLUSPLUS)        return false;
        if (curr == GosuLanguageLexer.MINUSMINUS)      return false;

        // Never space after
        if (prev == GosuLanguageLexer.AT)              return false;
        if (prev == GosuLanguageLexer.DOT)             return false;
        if (prev == GosuLanguageLexer.QUESTION_DOT)    return false;
        if (prev == GosuLanguageLexer.STAR_DOT)        return false;
        if (prev == GosuLanguageLexer.HASH)            return false;
        if (prev == GosuLanguageLexer.LBRACK)          return false;
        if (prev == GosuLanguageLexer.LPAREN)          return false;
        if (prev == GosuLanguageLexer.BANG)            return false;
        if (prev == GosuLanguageLexer.TILDE)           return false;

        // LPAREN: space only after control-flow keywords (not function names)
        if (curr == GosuLanguageLexer.LPAREN) return isControlKeyword(prev);

        // LBRACE: space after ), keyword, or identifier (block opener)
        if (curr == GosuLanguageLexer.LBRACE) {
            return prev == GosuLanguageLexer.RPAREN
                || isKeyword(prev)
                || prev == GosuLanguageLexer.Ident;
        }

        // BANG: space before when used as part of != / !== (prev is identifier, literal, or ')')
        if (curr == GosuLanguageLexer.BANG) {
            return prev == GosuLanguageLexer.Ident
                || prev == GosuLanguageLexer.NumberLiteral
                || prev == GosuLanguageLexer.StringLiteral
                || prev == GosuLanguageLexer.RPAREN
                || prev == GosuLanguageLexer.RBRACK;
        }

        // Binary and assignment operators: space around
        if (isBinaryOp(curr) || isBinaryOp(prev)) return true;

        // COLON: space around (type annotations, switch labels)
        if (curr == GosuLanguageLexer.COLON || prev == GosuLanguageLexer.COLON) return true;

        // ARROW (->): space around (lambda)
        if (curr == GosuLanguageLexer.ARROW || prev == GosuLanguageLexer.ARROW) return true;

        // QUESTION (?): space around (ternary operator)
        if (curr == GosuLanguageLexer.QUESTION || prev == GosuLanguageLexer.QUESTION) return true;

        // After COMMA: space
        if (prev == GosuLanguageLexer.COMMA) return true;

        // After keyword: space
        if (isKeyword(prev)) return true;

        // Before keyword: space
        if (isKeyword(curr)) return true;

        // Between adjacent alphanumeric tokens
        if (isAlphanumericToken(prev) && isAlphanumericToken(curr)) return true;

        return false;
    }

    /** Returns true when two adjacent single-char tokens form a compound operator. */
    private static boolean isCompoundPair(int prev, int curr) {
        if (prev == GosuLanguageLexer.EQ    && curr == GosuLanguageLexer.EQ)      return true; // ==, ===
        if (prev == GosuLanguageLexer.BANG  && curr == GosuLanguageLexer.EQ)      return true; // !=, !==
        if (prev == GosuLanguageLexer.LT    && curr == GosuLanguageLexer.EQ)      return true; // <=
        if (prev == GosuLanguageLexer.GT    && curr == GosuLanguageLexer.EQ)      return true; // >=
        if (prev == GosuLanguageLexer.LT    && curr == GosuLanguageLexer.GT)      return true; // <>
        if (prev == GosuLanguageLexer.LT    && curr == GosuLanguageLexer.LT)      return true; // <<
        if (prev == GosuLanguageLexer.GT    && curr == GosuLanguageLexer.GT)      return true; // >>
        if (prev == GosuLanguageLexer.PIPE  && curr == GosuLanguageLexer.PIPE)    return true; // ||
        if (prev == GosuLanguageLexer.AMP   && curr == GosuLanguageLexer.AMP)     return true; // &&
        if (prev == GosuLanguageLexer.DOT   && curr == GosuLanguageLexer.DOT)     return true; // ..  (range)
        if (prev == GosuLanguageLexer.PIPE  && curr == GosuLanguageLexer.DOT)     return true; // |.  (range)
        if (prev == GosuLanguageLexer.DOT   && curr == GosuLanguageLexer.PIPE)    return true; // .|  (range)
        if (prev == GosuLanguageLexer.QUESTION && curr == GosuLanguageLexer.PLUS)  return true; // ?+
        if (prev == GosuLanguageLexer.QUESTION && curr == GosuLanguageLexer.MINUS) return true; // ?-
        if (prev == GosuLanguageLexer.QUESTION && curr == GosuLanguageLexer.STAR)  return true; // ?*
        if (prev == GosuLanguageLexer.QUESTION && curr == GosuLanguageLexer.SLASH) return true; // ?/
        if (prev == GosuLanguageLexer.QUESTION && curr == GosuLanguageLexer.PERCENT) return true; // ?%
        return false;
    }

    private static boolean isBinaryOp(int type) {
        return switch (type) {
            case GosuLanguageLexer.EQ,
                 GosuLanguageLexer.PLUS,  GosuLanguageLexer.MINUS,
                 GosuLanguageLexer.STAR,  GosuLanguageLexer.SLASH,  GosuLanguageLexer.PERCENT,
                 GosuLanguageLexer.AMP,   GosuLanguageLexer.PIPE,   GosuLanguageLexer.CARET,
                 GosuLanguageLexer.PLUS_EQ,  GosuLanguageLexer.MINUS_EQ,
                 GosuLanguageLexer.STAR_EQ,  GosuLanguageLexer.SLASH_EQ,
                 GosuLanguageLexer.AMP_EQ,   GosuLanguageLexer.PIPE_EQ,
                 GosuLanguageLexer.CARET_EQ, GosuLanguageLexer.PERCENT_EQ,
                 GosuLanguageLexer.QUESTION_COLON -> true;
            default -> false;
        };
    }

    private static boolean isControlKeyword(int type) {
        return switch (type) {
            case GosuLanguageLexer.IF,      GosuLanguageLexer.FOR,
                 GosuLanguageLexer.FOREACH, GosuLanguageLexer.WHILE,
                 GosuLanguageLexer.SWITCH,  GosuLanguageLexer.CATCH,
                 GosuLanguageLexer.USING,   GosuLanguageLexer.ASSERT,
                 GosuLanguageLexer.EVAL     -> true;
            default -> false;
        };
    }

    private static boolean isJoinWord(int type) {
        return switch (type) {
            case GosuLanguageLexer.ELSE, GosuLanguageLexer.CATCH, GosuLanguageLexer.FINALLY -> true;
            default -> false;
        };
    }

    private static boolean isKeyword(int type) {
        return switch (type) {
            case GosuLanguageLexer.PACKAGE,     GosuLanguageLexer.USES,
                 GosuLanguageLexer.CLASS,        GosuLanguageLexer.INTERFACE,
                 GosuLanguageLexer.ENUM,         GosuLanguageLexer.ENHANCEMENT,
                 GosuLanguageLexer.EXTENDS,      GosuLanguageLexer.IMPLEMENTS,
                 GosuLanguageLexer.REPRESENTS,   GosuLanguageLexer.CONSTRUCT,
                 GosuLanguageLexer.FUNCTION,     GosuLanguageLexer.PROPERTY,
                 GosuLanguageLexer.VAR,          GosuLanguageLexer.DELEGATE,
                 GosuLanguageLexer.AS,           GosuLanguageLexer.READONLY,
                 GosuLanguageLexer.GET,          GosuLanguageLexer.SET,
                 GosuLanguageLexer.IF,           GosuLanguageLexer.ELSE,
                 GosuLanguageLexer.TRY,          GosuLanguageLexer.CATCH,
                 GosuLanguageLexer.FINALLY,      GosuLanguageLexer.THROW,
                 GosuLanguageLexer.RETURN,       GosuLanguageLexer.FOR,
                 GosuLanguageLexer.FOREACH,      GosuLanguageLexer.IN,
                 GosuLanguageLexer.INDEX,        GosuLanguageLexer.ITERATOR,
                 GosuLanguageLexer.WHILE,        GosuLanguageLexer.DO,
                 GosuLanguageLexer.SWITCH,       GosuLanguageLexer.CASE,
                 GosuLanguageLexer.DEFAULT,      GosuLanguageLexer.BREAK,
                 GosuLanguageLexer.CONTINUE,     GosuLanguageLexer.ASSERT,
                 GosuLanguageLexer.USING,        GosuLanguageLexer.NEW,
                 GosuLanguageLexer.THIS,         GosuLanguageLexer.SUPER,
                 GosuLanguageLexer.NULL_LIT,     GosuLanguageLexer.TRUE,
                 GosuLanguageLexer.FALSE,        GosuLanguageLexer.BLOCK,
                 GosuLanguageLexer.TYPEIS,       GosuLanguageLexer.TYPEAS,
                 GosuLanguageLexer.TYPEOF,       GosuLanguageLexer.STATICTYPEOF,
                 GosuLanguageLexer.OR,           GosuLanguageLexer.AND,
                 GosuLanguageLexer.NOT,          GosuLanguageLexer.EVAL,
                 GosuLanguageLexer.ABSTRACT,     GosuLanguageLexer.FINAL,
                 GosuLanguageLexer.STATIC,       GosuLanguageLexer.OVERRIDE,
                 GosuLanguageLexer.TRANSIENT,    GosuLanguageLexer.PRIVATE,
                 GosuLanguageLexer.INTERNAL,     GosuLanguageLexer.PROTECTED,
                 GosuLanguageLexer.PUBLIC        -> true;
            default -> false;
        };
    }

    private static boolean isAlphanumericToken(int type) {
        return type == GosuLanguageLexer.Ident
            || type == GosuLanguageLexer.NumberLiteral
            || type == GosuLanguageLexer.StringLiteral
            || isKeyword(type);
    }

    private static int peekType(List<Token> tokens, int index) {
        if (index >= tokens.size()) return Token.EOF;
        return tokens.get(index).getType();
    }

    private static String buildIndentUnit(FormattingOptions opts) {
        if (opts == null) return "    ";
        if (!opts.isInsertSpaces()) return "\t";
        int size = opts.getTabSize() > 0 ? opts.getTabSize() : 4;
        return " ".repeat(size);
    }
}
