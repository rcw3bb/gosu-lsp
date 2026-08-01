package xyz.ronella.gosu.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Handles workspace/* LSP notifications (configuration changes, watched files, etc.).
 */
public class GosuWorkspaceService implements WorkspaceService {

    private static final Logger LOG = Logger.getLogger(GosuWorkspaceService.class.getName());

    private final GosuLanguageServer server;
    private LanguageClient client;

    public GosuWorkspaceService(GosuLanguageServer server) {
        this.server = server;
    }

    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        LOG.info("didChangeConfiguration");
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        LOG.fine("didChangeWatchedFiles: " + params.getChanges().size() + " events");
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        LOG.info("executeCommand: " + params.getCommand());
        return CompletableFuture.completedFuture(null);
    }
}
