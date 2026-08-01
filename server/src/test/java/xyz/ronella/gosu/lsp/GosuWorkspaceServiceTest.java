package xyz.ronella.gosu.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GosuWorkspaceServiceTest {

    @Mock
    private LanguageClient client;

    private GosuWorkspaceService service;

    @BeforeEach
    void setUp() {
        GosuLanguageServer server = new GosuLanguageServer();
        service = (GosuWorkspaceService) server.getWorkspaceService();
    }

    @Test
    void connect_doesNotThrow() {
        assertDoesNotThrow(() -> service.connect(client));
    }

    @Test
    void didChangeConfiguration_doesNotThrow() {
        assertDoesNotThrow(() ->
                service.didChangeConfiguration(new DidChangeConfigurationParams()));
    }

    @Test
    void didChangeWatchedFiles_doesNotThrow() {
        DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams();
        params.setChanges(List.of(new FileEvent("file:///foo.gs", FileChangeType.Changed)));
        assertDoesNotThrow(() -> service.didChangeWatchedFiles(params));
    }

    @Test
    void didChangeWatchedFiles_emptyChanges_doesNotThrow() {
        DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams();
        params.setChanges(List.of());
        assertDoesNotThrow(() -> service.didChangeWatchedFiles(params));
    }

    @Test
    void executeCommand_returnsCompletedFuture() throws Exception {
        ExecuteCommandParams params = new ExecuteCommandParams();
        params.setCommand("gosu.doSomething");
        CompletableFuture<Object> future = service.executeCommand(params);
        assertNotNull(future);
        assertNull(future.get());
    }
}
