package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompletionHandlerTest {

    private DocumentManager documents;
    private CompletionHandler handler;

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        handler = new CompletionHandler(documents);
    }

    @Test
    void complete_unknownUri_returnsKeywordsAndBuiltins() {
        // document not open — content is null; still returns static keywords/types
        List<CompletionItem> items = handler.complete("file:///unknown.gs", new Position(0, 0));
        assertNotNull(items);
        assertFalse(items.isEmpty(), "Should return at least keywords when no prefix");
    }

    @Test
    void complete_emptyDoc_emptyPrefix_includesKeywords() {
        documents.open("file:///foo.gs", "");
        List<CompletionItem> items = handler.complete("file:///foo.gs", new Position(0, 0));
        assertTrue(items.stream().anyMatch(i -> "class".equals(i.getLabel())));
        assertTrue(items.stream().anyMatch(i -> "function".equals(i.getLabel())));
    }

    @Test
    void complete_keywordPrefix_filteredCorrectly() {
        documents.open("file:///foo.gs", "cl");
        List<CompletionItem> items = handler.complete("file:///foo.gs", new Position(0, 2));
        assertTrue(items.stream().allMatch(i ->
                i.getLabel().toLowerCase().startsWith("cl") ||
                i.getKind() != CompletionItemKind.Keyword),
                "Keyword items should all match prefix 'cl'");
        assertTrue(items.stream().anyMatch(i ->
                "class".equals(i.getLabel()) && i.getKind() == CompletionItemKind.Keyword));
    }

    @Test
    void complete_builtinTypePrefix_includesMatchingTypes() {
        documents.open("file:///foo.gs", "Str");
        List<CompletionItem> items = handler.complete("file:///foo.gs", new Position(0, 3));
        assertTrue(items.stream().anyMatch(i ->
                "String".equals(i.getLabel()) && i.getKind() == CompletionItemKind.Class));
    }

    @Test
    void complete_symbolFromDocument_included() {
        String src = "class MyClass {}\nfunc";
        documents.open("file:///foo.gs", src);
        // cursor is at end of "func" prefix on line 1
        List<CompletionItem> items = handler.complete("file:///foo.gs", new Position(1, 4));
        assertTrue(items.stream().anyMatch(i ->
                "function".equals(i.getLabel()) && i.getKind() == CompletionItemKind.Keyword));
    }

    @Test
    void complete_documentSymbolInCompletion() {
        String src = "class CustomerOrder {}\nCus";
        documents.open("file:///foo.gs", src);
        List<CompletionItem> items = handler.complete("file:///foo.gs", new Position(1, 3));
        assertTrue(items.stream().anyMatch(i -> "CustomerOrder".equals(i.getLabel())),
                "User-defined class symbol should appear in completions");
    }

    @Test
    void complete_prefixBeyondLineLength_returnsItems() {
        documents.open("file:///foo.gs", "class");
        // character position beyond line — should not throw
        assertDoesNotThrow(() -> handler.complete("file:///foo.gs", new Position(0, 999)));
    }
}
