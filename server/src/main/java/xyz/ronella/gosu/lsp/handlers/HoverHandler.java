package xyz.ronella.gosu.lsp.handlers;

import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor;
import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor.Symbol;
import xyz.ronella.gosu.lsp.document.DocumentManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles textDocument/hover and textDocument/documentSymbol requests.
 */
public class HoverHandler {

    private final DocumentManager documents;
    private final GosuSymbolExtractor extractor = new GosuSymbolExtractor();

    /** Creates a new {@code HoverHandler} backed by the given {@link DocumentManager}. */
    public HoverHandler(DocumentManager documents) {
        this.documents = documents;
    }

    // ── Hover ─────────────────────────────────────────────────────────────

    /**
     * Returns hover information for the word at {@code position} in the given document.
     * Looks up the word among the symbols declared in the same file.
     */
    public Hover hover(String uri, Position position) {
        String content = documents.getContent(uri);
        if (content == null) return null;

        String word = GosuSymbolExtractor.wordAt(content, position.getLine(), position.getCharacter());
        if (word.isEmpty()) return null;

        // Search for a matching symbol declaration
        List<Symbol> symbols = extractor.extract(content);
        for (Symbol sym : symbols) {
            if (sym.name.equals(word)) {
                String markdown = buildHoverMarkdown(sym);
                MarkupContent mc = new MarkupContent(MarkupKind.MARKDOWN, markdown);
                return new Hover(mc);
            }
        }

        // No symbol found — return the word itself as plain text
        MarkupContent mc = new MarkupContent(MarkupKind.PLAINTEXT, word);
        return new Hover(mc);
    }

    // ── Document symbols (outline) ────────────────────────────────────────

    /** Returns all declared symbols in the document at {@code uri} for the outline view. */
    public List<Either<SymbolInformation, DocumentSymbol>> documentSymbols(String uri) {
        String content = documents.getContent(uri);
        List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
        if (content == null) return result;

        List<Symbol> symbols = extractor.extract(content);
        for (Symbol sym : symbols) {
            DocumentSymbol ds = new DocumentSymbol();
            ds.setName(sym.name);
            ds.setKind(toLspKind(sym));
            if (sym.type != null) ds.setDetail(sym.type);
            Range range = new Range(
                    new Position(sym.line, sym.startChar),
                    new Position(sym.line, sym.startChar + sym.name.length())
            );
            ds.setRange(range);
            ds.setSelectionRange(range);
            result.add(Either.forRight(ds));
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String buildHoverMarkdown(Symbol sym) {
        StringBuilder sb = new StringBuilder();
        sb.append("```gosu\n");
        switch (sym.kind) {
            case CLASS       -> sb.append("class ").append(sym.name);
            case INTERFACE   -> sb.append("interface ").append(sym.name);
            case ENUM        -> sb.append("enum ").append(sym.name);
            case ENHANCEMENT -> sb.append("enhancement ").append(sym.name);
            case FUNCTION    -> {
                sb.append("function ").append(sym.name).append("(...)");
                if (sym.type != null) sb.append(": ").append(sym.type);
            }
            case PROPERTY    -> sb.append("property ").append(sym.name);
            default          -> {
                sb.append("var ").append(sym.name);
                if (sym.type != null) sb.append(": ").append(sym.type);
            }
        }
        sb.append("\n```");
        return sb.toString();
    }

    private static SymbolKind toLspKind(Symbol sym) {
        return switch (sym.kind) {
            case CLASS, ENHANCEMENT -> SymbolKind.Class;
            case INTERFACE          -> SymbolKind.Interface;
            case ENUM               -> SymbolKind.Enum;
            case FUNCTION           -> SymbolKind.Function;
            case PROPERTY           -> SymbolKind.Property;
            case VARIABLE, FIELD    -> SymbolKind.Variable;
        };
    }
}
