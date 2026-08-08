package xyz.ronella.gosu.lsp;

import xyz.ronella.gosu.lsp.analysis.DiagnosticsPublisher;
import xyz.ronella.gosu.lsp.document.DocumentManager;
import xyz.ronella.gosu.lsp.handlers.CodeActionHandler;
import xyz.ronella.gosu.lsp.handlers.CompletionHandler;
import xyz.ronella.gosu.lsp.handlers.DefinitionHandler;
import xyz.ronella.gosu.lsp.handlers.FormattingHandler;
import xyz.ronella.gosu.lsp.handlers.HoverHandler;
import xyz.ronella.gosu.lsp.handlers.InlayHintHandler;
import xyz.ronella.gosu.lsp.handlers.RenameHandler;
import xyz.ronella.gosu.lsp.handlers.ReferencesHandler;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles all textDocument/* LSP requests.
 */
public class GosuTextDocumentService implements TextDocumentService {

    private static final Logger LOG = Logger.getLogger(GosuTextDocumentService.class.getName());

    @SuppressWarnings("PMD.UnusedPrivateField")
    private final GosuLanguageServer server;
    private final DocumentManager documents = new DocumentManager();
    private final DiagnosticsPublisher diagnostics = new DiagnosticsPublisher();
    private final CompletionHandler completionHandler   = new CompletionHandler(documents);
    private final HoverHandler hoverHandler             = new HoverHandler(documents);
    private final DefinitionHandler definitionHandler   = new DefinitionHandler(documents);
    private final RenameHandler renameHandler           = new RenameHandler(documents);
    private final ReferencesHandler referencesHandler   = new ReferencesHandler(documents);
    private final FormattingHandler formattingHandler   = new FormattingHandler(documents);
    private final CodeActionHandler codeActionHandler   = new CodeActionHandler();
    private final InlayHintHandler inlayHintHandler     = new InlayHintHandler(documents);
    private final Map<String, List<Either<SymbolInformation, DocumentSymbol>>> symbolCache = new ConcurrentHashMap<>();

    private LanguageClient client;

    /** Creates a service attached to the given parent {@link GosuLanguageServer}. */
    public GosuTextDocumentService(GosuLanguageServer server) {
        this.server = server;
    }

    /** Wires the given {@link LanguageClient} to enable diagnostics publishing. */
    public void connect(LanguageClient client) {
        this.client = client;
        diagnostics.connect(client);
    }

    /** Passes the workspace root URI to the document store for cross-file definition lookup. */
    public void setWorkspaceRoot(String rootUri) {
        documents.setWorkspaceRoot(rootUri);
    }

    // ── Document lifecycle ────────────────────────────────────────────────

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri     = params.getTextDocument().getUri();
        String content = params.getTextDocument().getText();
        LOG.fine("didOpen: " + uri);
        documents.open(uri, content);
        symbolCache.remove(uri);
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
        symbolCache.remove(uri);
        diagnostics.publishDiagnostics(uri, current);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        LOG.fine("didClose: " + uri);
        documents.close(uri);
        symbolCache.remove(uri);
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
        String uri = params.getTextDocument().getUri();
        List<Either<SymbolInformation, DocumentSymbol>> cached = symbolCache.get(uri);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return CompletableFuture.supplyAsync(() -> {
            List<Either<SymbolInformation, DocumentSymbol>> result = hoverHandler.documentSymbols(uri);
            symbolCache.put(uri, result);
            return result;
        });
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFuture.supplyAsync(() ->
                referencesHandler.references(
                        params.getTextDocument().getUri(),
                        params.getPosition(),
                        params.getContext().isIncludeDeclaration()
                )
        );
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return CompletableFuture.supplyAsync(() ->
                renameHandler.rename(
                        params.getTextDocument().getUri(),
                        params.getPosition(),
                        params.getNewName()
                )
        );
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        return CompletableFuture.supplyAsync(() -> {
            Range range = renameHandler.prepareRename(
                    params.getTextDocument().getUri(),
                    params.getPosition()
            );
            return range != null
                    ? Either3.forFirst(range)
                    : Either3.forThird(new PrepareRenameDefaultBehavior(false));
        });
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return CompletableFuture.supplyAsync(() ->
                formattingHandler.format(
                        params.getTextDocument().getUri(),
                        params.getOptions())
        );
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(
            DocumentRangeFormattingParams params) {
        return CompletableFuture.supplyAsync(() ->
                formattingHandler.formatRange(
                        params.getTextDocument().getUri(),
                        params.getRange(),
                        params.getOptions())
        );
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return CompletableFuture.supplyAsync(() ->
                codeActionHandler.getActions(
                        params.getTextDocument().getUri(),
                        params.getRange(),
                        params.getContext().getDiagnostics())
        );
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return CompletableFuture.supplyAsync(() ->
                inlayHintHandler.getHints(
                        params.getTextDocument().getUri(),
                        params.getRange())
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

    /** Releases background resources held by this service. */
    public void shutdown() {
        // Nothing to tear down for now
    }
}
