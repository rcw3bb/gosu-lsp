package com.gosu.lsp;

import com.gosu.lsp.analysis.DiagnosticsPublisher;
import com.gosu.lsp.document.DocumentManager;
import com.gosu.lsp.handlers.CompletionHandler;
import com.gosu.lsp.handlers.DefinitionHandler;
import com.gosu.lsp.handlers.HoverHandler;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Handles all textDocument/* LSP requests.
 */
public class GosuTextDocumentService implements TextDocumentService {

    private static final Logger LOG = Logger.getLogger(GosuTextDocumentService.class.getName());

    private final GosuLanguageServer server;
    private final DocumentManager documents = new DocumentManager();
    private final DiagnosticsPublisher diagnostics = new DiagnosticsPublisher();
    private final CompletionHandler completionHandler = new CompletionHandler(documents);
    private final HoverHandler hoverHandler = new HoverHandler(documents);
    private final DefinitionHandler definitionHandler = new DefinitionHandler(documents);

    private LanguageClient client;

    public GosuTextDocumentService(GosuLanguageServer server) {
        this.server = server;
    }

    public void connect(LanguageClient client) {
        this.client = client;
        diagnostics.connect(client);
    }

    // ── Document lifecycle ────────────────────────────────────────────────

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri     = params.getTextDocument().getUri();
        String content = params.getTextDocument().getText();
        LOG.fine("didOpen: " + uri);
        documents.open(uri, content);
        diagnostics.publishDiagnostics(uri, content);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        LOG.fine("didChange: " + uri);
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (changes.isEmpty()) return;

        // Apply incremental or full changes
        String current = documents.getContent(uri);
        if (current == null) current = "";
        for (TextDocumentContentChangeEvent change : changes) {
            if (change.getRange() == null) {
                // Full content replacement
                current = change.getText();
            } else {
                current = applyChange(current, change);
            }
        }
        documents.update(uri, current);
        diagnostics.publishDiagnostics(uri, current);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        LOG.fine("didClose: " + uri);
        documents.close(uri);
        // Clear diagnostics on close
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        LOG.fine("didSave: " + uri);
        String content = documents.getContent(uri);
        if (content != null) {
            diagnostics.publishDiagnostics(uri, content);
        }
    }

    // ── Language features ─────────────────────────────────────────────────

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            CompletionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletionItem> items = completionHandler.complete(
                    params.getTextDocument().getUri(),
                    params.getPosition()
            );
            return Either.forLeft(items);
        });
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() ->
                hoverHandler.hover(params.getTextDocument().getUri(), params.getPosition())
        );
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            List<Location> locations = definitionHandler.definition(
                    params.getTextDocument().getUri(),
                    params.getPosition()
            );
            return Either.forLeft(locations);
        });
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return CompletableFuture.supplyAsync(() ->
                hoverHandler.documentSymbols(params.getTextDocument().getUri())
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Applies a single incremental text change to {@code content}.
     */
    private static String applyChange(String content, TextDocumentContentChangeEvent change) {
        Range range = change.getRange();
        String[] lines = content.split("\n", -1);

        int startLine = range.getStart().getLine();
        int startChar = range.getStart().getCharacter();
        int endLine   = range.getEnd().getLine();
        int endChar   = range.getEnd().getCharacter();

        // Compute flat offsets
        int startOffset = flatOffset(lines, startLine, startChar);
        int endOffset   = flatOffset(lines, endLine,   endChar);

        if (startOffset < 0 || endOffset > content.length() || startOffset > endOffset) {
            // Fallback: full replacement
            return change.getText();
        }
        return content.substring(0, startOffset) + change.getText() + content.substring(endOffset);
    }

    private static int flatOffset(String[] lines, int line, int character) {
        int offset = 0;
        for (int i = 0; i < line && i < lines.length; i++) {
            offset += lines[i].length() + 1; // +1 for '\n'
        }
        return offset + Math.min(character, line < lines.length ? lines[line].length() : 0);
    }

    public void shutdown() {
        // Nothing to tear down for now
    }
}
