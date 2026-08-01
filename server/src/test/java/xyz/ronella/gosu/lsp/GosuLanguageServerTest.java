package xyz.ronella.gosu.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GosuLanguageServerTest {

    @Mock
    private LanguageClient client;

    private GosuLanguageServer server;

    @BeforeEach
    void setUp() {
        server = new GosuLanguageServer();
    }

    @Test
    void constructor_textDocumentServiceIsNotNull() {
        assertNotNull(server.getTextDocumentService());
    }

    @Test
    void constructor_workspaceServiceIsNotNull() {
        assertNotNull(server.getWorkspaceService());
    }

    @Test
    void connect_setsClient() {
        server.connect(client);
        assertEquals(client, server.getClient());
    }

    @Test
    void getClient_returnsNullBeforeConnect() {
        assertNull(server.getClient());
    }

    @Test
    void initialize_returnsCompletionProviderCapability() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();
        assertNotNull(result.getCapabilities().getCompletionProvider());
    }

    @Test
    void initialize_returnsHoverCapability() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();
        assertTrue(result.getCapabilities().getHoverProvider().getLeft());
    }

    @Test
    void initialize_returnsDefinitionCapability() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();
        assertTrue(result.getCapabilities().getDefinitionProvider().getLeft());
    }

    @Test
    void initialize_returnsDocumentSymbolCapability() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();
        assertNotNull(result.getCapabilities().getDocumentSymbolProvider());
    }

    @Test
    void initialize_returnsSyncOptions() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();
        assertNotNull(result.getCapabilities().getTextDocumentSync());
    }

    @Test
    void initialize_serverInfoPresent() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();
        assertNotNull(result.getServerInfo());
        assertEquals("Gosu Language Server", result.getServerInfo().getName());
    }

    @Test
    void initialized_doesNotThrow() {
        assertDoesNotThrow(() -> server.initialized(new InitializedParams()));
    }

    @Test
    void shutdown_returnsCompletedFuture() throws Exception {
        CompletableFuture<Object> future = server.shutdown();
        assertNotNull(future);
        assertNull(future.get());
    }
}
