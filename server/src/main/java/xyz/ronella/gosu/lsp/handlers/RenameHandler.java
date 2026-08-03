package xyz.ronella.gosu.lsp.handlers;

import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor;
import xyz.ronella.gosu.lsp.document.DocumentManager;
import org.eclipse.lsp4j.*;

import java.util.*;

/**
 * Handles textDocument/rename and textDocument/prepareRename requests.
 *
 * Renames every whole-word occurrence of the symbol at the cursor across all
 * open documents.
 */
public class RenameHandler {

    private final DocumentManager documents;
    private final GosuSymbolExtractor extractor = new GosuSymbolExtractor();

    /** Creates a new {@code RenameHandler} backed by the given {@link DocumentManager}. */
    public RenameHandler(DocumentManager documents) {
        this.documents = documents;
    }

    /**
     * Returns the range of the identifier at {@code position}, or {@code null} if
     * the cursor is not on a word.
     */
    public Range prepareRename(String uri, Position position) {
        String content = documents.getContent(uri);
        if (content == null) return null;

        String word = GosuSymbolExtractor.wordAt(content, position.getLine(), position.getCharacter());
        if (word.isEmpty()) return null;

        // Find the occurrence that covers the cursor position
        for (Range r : extractor.findOccurrences(content, word)) {
            if (r.getStart().getLine() == position.getLine()
                    && r.getStart().getCharacter() <= position.getCharacter()
                    && r.getEnd().getCharacter() >= position.getCharacter()) {
                return r;
            }
        }

        // Fallback: compute range directly from word boundaries
        String[] lines = content.split("\n", -1);
        int line = position.getLine();
        if (line < lines.length) {
            String lineText = lines[line];
            int start = position.getCharacter();
            while (start > 0 && isWordChar(lineText.charAt(start - 1))) start--;
            return new Range(
                    new Position(line, start),
                    new Position(line, start + word.length()));
        }
        return null;
    }

    /** Renames every occurrence of the symbol at {@code position} to {@code newName} across all open files. */
    public WorkspaceEdit rename(String uri, Position position, String newName) {
        String content = documents.getContent(uri);
        if (content == null || newName == null || newName.isBlank()) {
            return new WorkspaceEdit(Collections.emptyMap());
        }

        String word = GosuSymbolExtractor.wordAt(content, position.getLine(), position.getCharacter());
        if (word.isEmpty()) return new WorkspaceEdit(Collections.emptyMap());

        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : documents.snapshot().entrySet()) {
            List<Range> occurrences = extractor.findOccurrences(entry.getValue(), word);
            if (!occurrences.isEmpty()) {
                List<TextEdit> edits = new ArrayList<>(occurrences.size());
                for (Range r : occurrences) {
                    edits.add(new TextEdit(r, newName));
                }
                changes.put(entry.getKey(), edits);
            }
        }
        return new WorkspaceEdit(changes);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
