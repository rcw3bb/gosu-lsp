package xyz.ronella.gosu.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Root language server: declares capabilities and wires up services.
 */
public class GosuLanguageServer implements LanguageServer, LanguageClientAware {

    private static final Logger LOG = Logger.getLogger(GosuLanguageServer.class.getName());

    private LanguageClient client;
    private final GosuTextDocumentService textDocumentService;
    private final GosuWorkspaceService workspaceService;

    /** 0 = shutdown was requested; 1 = still running */
    private int exitCode = 1;

    public GosuLanguageServer() {
        textDocumentService = new GosuTextDocumentService(this);
        workspaceService    = new GosuWorkspaceService(this);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        LOG.info("initialize() called");

        ServerCapabilities caps = new ServerCapabilities();

        // --- Document synchronisation (incremental) ---
        TextDocumentSyncOptions syncOpts = new TextDocumentSyncOptions();
        syncOpts.setOpenClose(true);
        syncOpts.setChange(TextDocumentSyncKind.Incremental);
        syncOpts.setSave(new SaveOptions(true));
        caps.setTextDocumentSync(syncOpts);

        // --- Completion ---
        CompletionOptions completionOpts = new CompletionOptions();
        completionOpts.setTriggerCharacters(Arrays.asList(".", " ", ":"));
        completionOpts.setResolveProvider(false);
        caps.setCompletionProvider(completionOpts);

        // --- Hover ---
        caps.setHoverProvider(true);

        // --- Go-to-definition ---
        caps.setDefinitionProvider(true);

        // --- Document symbols (outline) ---
        DocumentSymbolOptions symOpts = new DocumentSymbolOptions();
        symOpts.setLabel("Gosu");
        caps.setDocumentSymbolProvider(symOpts);

        ServerInfo info = new ServerInfo("Gosu Language Server", "0.1.0");
        InitializeResult result = new InitializeResult(caps);
        result.setServerInfo(info);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(InitializedParams params) {
        LOG.info("Client initialized.");
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        LOG.info("shutdown() called");
        exitCode = 0;
        textDocumentService.shutdown();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        LOG.info("exit() — process exiting with code " + exitCode);
        System.exit(exitCode);
    }

    // ── Services ───────────────────────────────────────────────────────────

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    // ── Client connection ──────────────────────────────────────────────────

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        textDocumentService.connect(client);
        workspaceService.connect(client);
    }

    public LanguageClient getClient() {
        return client;
    }
}
