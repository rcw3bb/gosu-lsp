package xyz.ronella.gosu.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point — starts the Gosu LSP server reading JSON-RPC from stdin / writing to stdout.
 */
public class GosuLanguageServerLauncher {

    private static final Logger LOG = Logger.getLogger(GosuLanguageServerLauncher.class.getName());

    public static void main(String[] args) throws Exception {
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
