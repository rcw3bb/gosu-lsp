package com.gosu.lsp.handlers;

import com.gosu.lsp.analysis.GosuSymbolExtractor;
import com.gosu.lsp.analysis.GosuSymbolExtractor.Symbol;
import com.gosu.lsp.document.DocumentManager;
import org.eclipse.lsp4j.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles textDocument/definition requests.
 *
 * Phase 1 strategy: search for the symbol's declaration within:
 *   1. The current open document.
 *   2. All other open documents (cross-file).
 */
public class DefinitionHandler {

    private final DocumentManager documents;
    private final GosuSymbolExtractor extractor = new GosuSymbolExtractor();

    public DefinitionHandler(DocumentManager documents) {
        this.documents = documents;
    }

    public List<Location> definition(String uri, Position position) {
        List<Location> results = new ArrayList<>();

        String content = documents.getContent(uri);
        if (content == null) return results;

        String word = GosuSymbolExtractor.wordAt(content, position.getLine(), position.getCharacter());
        if (word.isEmpty()) return results;

        // 1. Search current document first
        findInDocument(uri, content, word, results);
        if (!results.isEmpty()) return results;

        // 2. Search all other open documents
        Map<String, String> snapshot = documents.snapshot();
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            if (!entry.getKey().equals(uri)) {
                findInDocument(entry.getKey(), entry.getValue(), word, results);
            }
        }

        return results;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void findInDocument(String uri, String content, String word, List<Location> out) {
        List<Symbol> symbols = extractor.extract(content);
        for (Symbol sym : symbols) {
            if (sym.name.equals(word)) {
                Range range = new Range(
                        new Position(sym.line, sym.startChar),
                        new Position(sym.line, sym.startChar + sym.name.length())
                );
                out.add(new Location(uri, range));
            }
        }
    }
}
