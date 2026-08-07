package xyz.ronella.gosu.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.InputStream;
import java.util.concurrent.Future;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Entry point — starts the Gosu LSP server reading JSON-RPC from stdin / writing to stdout.
 */
public class GosuLanguageServerLauncher {

    private static final Logger LOG = Logger.getLogger(GosuLanguageServerLauncher.class.getName());

    /** Launches the Gosu LSP server on stdin/stdout and blocks until the connection closes. */
    @SuppressWarnings("PMD.CloseResource") // err wraps FileDescriptor.err — it must stay open for JVM lifetime
    public static void main(String[] args) throws Exception {
        // Load logging configuration from the classpath before anything else.
        try (InputStream cfg = GosuLanguageServerLauncher.class.getResourceAsStream("/logging.properties")) {
            if (cfg != null) {
                LogManager.getLogManager().readConfiguration(cfg);
            }
        }

        // Redirect System.err so accidental prints don't corrupt the LSP stream
        java.io.PrintStream err = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err));
        System.setErr(err);

        GosuLanguageServer server = new GosuLanguageServer();

        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                server,
                System.in,
                System.out
        );

        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);

        LOG.info("Gosu Language Server started.");
        Future<?> future = launcher.startListening();
        future.get(); // block until the connection closes
    }
}
