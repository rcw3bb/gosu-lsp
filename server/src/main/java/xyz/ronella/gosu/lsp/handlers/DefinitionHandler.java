package xyz.ronella.gosu.lsp.handlers;

import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor;
import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor.Symbol;
import xyz.ronella.gosu.lsp.document.DocumentManager;
import org.eclipse.lsp4j.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Handles textDocument/definition requests.
 *
 * Phase 1 strategy: search for the symbol's declaration within:
 *   1. The current open document.
 *   2. All other open documents (cross-file).
 */
public class DefinitionHandler {

    private static final Logger LOG = Logger.getLogger(DefinitionHandler.class.getName());

    private final DocumentManager documents;
    private final GosuSymbolExtractor extractor = new GosuSymbolExtractor();

    /** Creates a new {@code DefinitionHandler} backed by the given {@link DocumentManager}. */
    public DefinitionHandler(DocumentManager documents) {
        this.documents = documents;
    }

    /** Returns declaration locations for the symbol at {@code position} in {@code uri}. */
    public List<Location> definition(String uri, Position position) {
        List<Location> results = new ArrayList<>();

        String content = documents.getContent(uri);
        if (content == null) return results;

        String word = GosuSymbolExtractor.wordAt(content, position.getLine(), position.getCharacter());
        if (word.isEmpty()) return results;
        LOG.fine("definition: uri=" + uri + " word=" + word);

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
        if (!results.isEmpty()) return results;

        // 3. Scan workspace files on disk
        Path rootPath = resolveSearchRoot(uri);
        LOG.fine("definition: scanning workspace root=" + rootPath);
        if (rootPath != null) {
            findInWorkspace(rootPath, uri, word, results);
        }

        return results;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Returns the directory to scan: configured workspace root, or the nearest project root derived from the file URI. */
    private Path resolveSearchRoot(String fileUri) {
        String configured = documents.getWorkspaceRoot();
        LOG.fine("resolveSearchRoot: configured=" + configured);
        if (configured != null) {
            try {
                Path p = Paths.get(new URI(configured));
                if (Files.isDirectory(p)) return p;
            } catch (URISyntaxException | IllegalArgumentException ignored) {
                // fall through to heuristic
            }
        }
        return deriveProjectRoot(fileUri);
    }

    /** Walks up from the file's directory until a project-marker file is found. */
    private static Path deriveProjectRoot(String fileUri) {
        Path file;
        try {
            file = Paths.get(new URI(fileUri));
        } catch (URISyntaxException | IllegalArgumentException e) {
            LOG.warning("deriveProjectRoot: invalid URI " + fileUri);
            return null;
        }
        Path dir = Files.isDirectory(file) ? file : file.getParent();
        while (dir != null) {
            if (Files.exists(dir.resolve(".git"))
                    || Files.exists(dir.resolve("settings.gradle"))
                    || Files.exists(dir.resolve("build.gradle"))
                    || Files.exists(dir.resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        LOG.fine("deriveProjectRoot: no project marker found for " + fileUri);
        return null;
    }

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

    private void findInWorkspace(Path root, String skipUri, String word, List<Location> out) {
        if (!Files.isDirectory(root)) return;

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".gs") || name.endsWith(".gsx");
            }).forEach(p -> {
                String fileUri = p.toUri().toString();
                if (fileUri.equals(skipUri)) return;
                try {
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    findInDocument(fileUri, content, word, out);
                } catch (IOException ignored) {
                    // unreadable file — skip
                }
            });
        } catch (IOException ignored) {
            // walk failure — skip
        }
    }
}
