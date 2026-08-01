package xyz.ronella.gosu.lsp.handlers;

import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor;
import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor.Symbol;
import xyz.ronella.gosu.lsp.document.DocumentManager;
import org.eclipse.lsp4j.*;

import java.util.*;
import java.util.regex.*;

/**
 * Handles textDocument/references requests.
 *
 * Searches all open documents for every identifier occurrence of the word at
 * the cursor, optionally excluding the declaration site.
 */
public class ReferencesHandler {

    private final DocumentManager documents;
    private final GosuSymbolExtractor extractor = new GosuSymbolExtractor();

    public ReferencesHandler(DocumentManager documents) {
        this.documents = documents;
    }

    public List<Location> references(String uri, Position position, boolean includeDeclaration) {
        String content = documents.getContent(uri);
        if (content == null) return Collections.emptyList();

        String word = GosuSymbolExtractor.wordAt(content, position.getLine(), position.getCharacter());
        if (word.isEmpty()) return Collections.emptyList();

        List<Location> results = new ArrayList<>();
        for (Map.Entry<String, String> entry : documents.snapshot().entrySet()) {
            collectLocations(entry.getKey(), entry.getValue(), word, includeDeclaration, results);
        }
        return results;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void collectLocations(String docUri, String content, String word,
                                   boolean includeDeclaration, List<Location> out) {
        List<Range> occurrences = extractor.findOccurrences(content, word);
        if (occurrences.isEmpty()) return;

        if (includeDeclaration) {
            for (Range r : occurrences) out.add(new Location(docUri, r));
            return;
        }

        // Build set of declaration positions (line:col) to exclude
        Set<String> declPositions = new HashSet<>();
        String[] lines = content.split("\n", -1);
        for (Symbol sym : extractor.extract(content)) {
            if (sym.name.equals(word)) {
                declPositions.add(sym.line + ":" + nameColumnOnLine(lines, sym));
            }
        }

        for (Range r : occurrences) {
            String key = r.getStart().getLine() + ":" + r.getStart().getCharacter();
            if (!declPositions.contains(key)) {
                out.add(new Location(docUri, r));
            }
        }
    }

    /** Finds the column where sym.name appears as a word boundary on its declared line. */
    private static int nameColumnOnLine(String[] lines, Symbol sym) {
        if (sym.line >= lines.length) return sym.startChar;
        String lineText = lines[sym.line];
        Pattern p = Pattern.compile("\\b" + Pattern.quote(sym.name) + "\\b");
        Matcher m = p.matcher(lineText);
        if (m.find(sym.startChar)) return m.start();
        m.reset();
        return m.find() ? m.start() : sym.startChar;
    }
}
