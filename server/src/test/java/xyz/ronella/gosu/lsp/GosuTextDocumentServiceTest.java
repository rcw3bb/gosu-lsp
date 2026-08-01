package xyz.ronella.gosu.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GosuTextDocumentServiceTest {

    @Mock
    private LanguageClient client;

    private GosuLanguageServer server;
    private GosuTextDocumentService service;

    @BeforeEach
    void setUp() {
        server  = new GosuLanguageServer();
        service = (GosuTextDocumentService) server.getTextDocumentService();
        service.connect(client);
    }

    // ── didOpen ───────────────────────────────────────────────────────────

    @Test
    void didOpen_storesDocument() {
        service.didOpen(openParams("file:///foo.gs", "class Foo {}"));
        // completion should see the document symbol
        List<CompletionItem> items = getCompletionItems("file:///foo.gs", new Position(0, 1));
        assertFalse(items.isEmpty());
    }

    @Test
    void didOpen_publishesDiagnostics() {
        service.didOpen(openParams("file:///foo.gs", "class Foo {}"));
        verify(client, atLeastOnce()).publishDiagnostics(any(PublishDiagnosticsParams.class));
    }

    // ── didChange ─────────────────────────────────────────────────────────

    @Test
    void didChange_emptyChangesList_doesNothing() {
        service.didOpen(openParams("file:///foo.gs", "original"));
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(new VersionedTextDocumentIdentifier("file:///foo.gs", 2));
        params.setContentChanges(List.of());
        assertDoesNotThrow(() -> service.didChange(params));
    }

    @Test
    void didChange_fullReplacement_updatesContent() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "original content"));

        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(new VersionedTextDocumentIdentifier("file:///foo.gs", 2));
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setRange(null); // null range = full replacement
        change.setText("brand new content");
        params.setContentChanges(List.of(change));

        service.didChange(params);
        // Hover should work on new content's symbols
        assertDoesNotThrow(() -> service.hover(hoverParams("file:///foo.gs", 0, 0)).get());
    }

    @Test
    void didChange_incrementalChange_appliesCorrectly() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "class OldName {}"));

        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(new VersionedTextDocumentIdentifier("file:///foo.gs", 2));
        // Replace "OldName" (positions 6–13) with "NewName"
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setRange(new Range(new Position(0, 6), new Position(0, 13)));
        change.setText("NewName");
        params.setContentChanges(List.of(change));

        service.didChange(params);
        // After change, content should be "class NewName {}"
        // documentSymbol should have NewName
        List<Either<SymbolInformation, DocumentSymbol>> symbols =
                service.documentSymbol(docSymbolParams("file:///foo.gs")).get();
        assertTrue(symbols.stream().anyMatch(e ->
                e.isRight() && "NewName".equals(e.getRight().getName())));
    }

    @Test
    void didChange_outOfBoundsRange_fallsBackToFullReplacement() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "abc"));

        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(new VersionedTextDocumentIdentifier("file:///foo.gs", 2));
        // Range well beyond content length → fallback
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setRange(new Range(new Position(0, 0), new Position(99, 99)));
        change.setText("fallback");
        params.setContentChanges(List.of(change));

        service.didChange(params);
        assertDoesNotThrow(() -> service.hover(hoverParams("file:///foo.gs", 0, 0)).get());
    }

    @Test
    void didChange_documentNotYetOpen_treatsAsEmpty() {
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(new VersionedTextDocumentIdentifier("file:///new.gs", 1));
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setRange(null);
        change.setText("class Late {}");
        params.setContentChanges(List.of(change));
        assertDoesNotThrow(() -> service.didChange(params));
    }

    // ── didClose ──────────────────────────────────────────────────────────

    @Test
    void didClose_removesDocument_and_clearsDiagnostics() {
        service.didOpen(openParams("file:///foo.gs", "class Foo {}"));
        service.didClose(closeParams("file:///foo.gs"));
        verify(client, atLeastOnce()).publishDiagnostics(
                argThat(p -> p.getUri().equals("file:///foo.gs") && p.getDiagnostics().isEmpty()));
    }

    // ── didSave ───────────────────────────────────────────────────────────

    @Test
    void didSave_documentOpen_publishesDiagnostics() {
        service.didOpen(openParams("file:///foo.gs", "class Foo {}"));
        clearInvocations(client);
        service.didSave(saveParams("file:///foo.gs"));
        verify(client, times(1)).publishDiagnostics(any(PublishDiagnosticsParams.class));
    }

    @Test
    void didSave_documentNotOpen_doesNotPublish() {
        // document was never opened — getContent returns null → no publish
        service.didSave(saveParams("file:///never.gs"));
        verify(client, never()).publishDiagnostics(any());
    }

    // ── LSP features ──────────────────────────────────────────────────────

    @Test
    void completion_returnsNonNullFuture() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "cl"));
        CompletableFuture<Either<List<CompletionItem>, CompletionList>> future =
                service.completion(completionParams("file:///foo.gs", 0, 2));
        assertNotNull(future);
        assertNotNull(future.get());
    }

    @Test
    void hover_returnsNonNullFuture() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "class Foo {}"));
        CompletableFuture<Hover> future = service.hover(hoverParams("file:///foo.gs", 0, 6));
        assertNotNull(future);
        // result may be null if no symbol found, but future itself must complete
        future.get(); // should not throw
    }

    @Test
    void definition_returnsNonNullFuture() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "class Bar {}"));
        CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> future =
                service.definition(definitionParams("file:///foo.gs", 0, 6));
        assertNotNull(future);
        assertNotNull(future.get());
    }

    @Test
    void documentSymbol_returnsNonNullFuture() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "class Baz {}"));
        CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> future =
                service.documentSymbol(docSymbolParams("file:///foo.gs"));
        assertNotNull(future);
        List<Either<SymbolInformation, DocumentSymbol>> symbols = future.get();
        assertFalse(symbols.isEmpty());
    }

    @Test
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(() -> service.shutdown());
    }

    @Test
    void rename_returnsNonNullFuture() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "class Foo {}"));
        RenameParams params = new RenameParams();
        params.setTextDocument(new TextDocumentIdentifier("file:///foo.gs"));
        params.setPosition(new Position(0, 6));
        params.setNewName("Bar");
        CompletableFuture<WorkspaceEdit> future = service.rename(params);
        assertNotNull(future);
        assertNotNull(future.get());
    }

    @Test
    void references_returnsNonNullFuture() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "class Foo {}"));
        ReferenceParams params = new ReferenceParams();
        params.setTextDocument(new TextDocumentIdentifier("file:///foo.gs"));
        params.setPosition(new Position(0, 6));
        params.setContext(new ReferenceContext(true));
        CompletableFuture<List<? extends Location>> future = service.references(params);
        assertNotNull(future);
        assertNotNull(future.get());
    }

    @Test
    void prepareRename_returnsNonNullFuture() throws Exception {
        service.didOpen(openParams("file:///foo.gs", "class Foo {}"));
        PrepareRenameParams params = new PrepareRenameParams();
        params.setTextDocument(new TextDocumentIdentifier("file:///foo.gs"));
        params.setPosition(new Position(0, 6));
        CompletableFuture<?> future = service.prepareRename(params);
        assertNotNull(future);
        future.get(); // must complete without exception
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static DidOpenTextDocumentParams openParams(String uri, String text) {
        TextDocumentItem item = new TextDocumentItem(uri, "gosu", 1, text);
        return new DidOpenTextDocumentParams(item);
    }

    private static DidCloseTextDocumentParams closeParams(String uri) {
        DidCloseTextDocumentParams p = new DidCloseTextDocumentParams();
        p.setTextDocument(new TextDocumentIdentifier(uri));
        return p;
    }

    private static DidSaveTextDocumentParams saveParams(String uri) {
        DidSaveTextDocumentParams p = new DidSaveTextDocumentParams();
        p.setTextDocument(new TextDocumentIdentifier(uri));
        return p;
    }

    private static CompletionParams completionParams(String uri, int line, int character) {
        CompletionParams p = new CompletionParams();
        p.setTextDocument(new TextDocumentIdentifier(uri));
        p.setPosition(new Position(line, character));
        return p;
    }

    private static HoverParams hoverParams(String uri, int line, int character) {
        HoverParams p = new HoverParams();
        p.setTextDocument(new TextDocumentIdentifier(uri));
        p.setPosition(new Position(line, character));
        return p;
    }

    private static DefinitionParams definitionParams(String uri, int line, int character) {
        DefinitionParams p = new DefinitionParams();
        p.setTextDocument(new TextDocumentIdentifier(uri));
        p.setPosition(new Position(line, character));
        return p;
    }

    private static DocumentSymbolParams docSymbolParams(String uri) {
        DocumentSymbolParams p = new DocumentSymbolParams();
        p.setTextDocument(new TextDocumentIdentifier(uri));
        return p;
    }

    private List<CompletionItem> getCompletionItems(String uri, Position position) {
        try {
            return service.completion(completionParams(uri, position.getLine(), position.getCharacter()))
                    .get().getLeft();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
