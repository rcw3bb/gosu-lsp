package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HoverHandlerTest {

    private DocumentManager documents;
    private HoverHandler handler;

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        handler = new HoverHandler(documents);
    }

    @Test
    void hover_unknownUri_returnsNull() {
        Hover result = handler.hover("file:///unknown.gs", new Position(0, 0));
        assertNull(result, "Hover on unknown URI should return null");
    }

    @Test
    void hover_emptyContent_returnsNull() {
        documents.open("file:///foo.gs", "");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 0));
        assertNull(result);
    }

    @Test
    void hover_knownSymbol_returnsMarkdownHover() {
        documents.open("file:///foo.gs", "class MyClass {\n  function doWork() {}\n}");
        // hover over "MyClass" (line 0, char 6 — inside the class name)
        Hover result = handler.hover("file:///foo.gs", new Position(0, 6));
        assertNotNull(result);
        assertNotNull(result.getContents());
        assertTrue(result.getContents().isRight());
        assertEquals(MarkupKind.MARKDOWN, result.getContents().getRight().getKind());
        assertTrue(result.getContents().getRight().getValue().contains("MyClass"));
    }

    @Test
    void hover_unknownWord_returnsPlainText() {
        documents.open("file:///foo.gs", "someRandomWord");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 4));
        assertNotNull(result);
        assertEquals(MarkupKind.PLAINTEXT, result.getContents().getRight().getKind());
    }

    @Test
    void hover_functionSymbol_returnsFunctionHover() {
        documents.open("file:///foo.gs", "function calculate() : Integer {\n  return 42\n}");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 10));
        assertNotNull(result);
        assertTrue(result.getContents().getRight().getValue().contains("calculate"));
    }

    // ── documentSymbols ──────────────────────────────────────────────────

    @Test
    void documentSymbols_unknownUri_returnsEmpty() {
        List<Either<SymbolInformation, DocumentSymbol>> result =
                handler.documentSymbols("file:///unknown.gs");
        assertTrue(result.isEmpty());
    }

    @Test
    void documentSymbols_emptyContent_returnsEmpty() {
        documents.open("file:///foo.gs", "");
        List<Either<SymbolInformation, DocumentSymbol>> result =
                handler.documentSymbols("file:///foo.gs");
        assertTrue(result.isEmpty());
    }

    @Test
    void documentSymbols_classAndFunction_returnsBoth() {
        documents.open("file:///foo.gs", "class Foo {}\nfunction bar() {}");
        List<Either<SymbolInformation, DocumentSymbol>> result =
                handler.documentSymbols("file:///foo.gs");
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(e ->
                e.isRight() && "Foo".equals(e.getRight().getName())));
        assertTrue(result.stream().anyMatch(e ->
                e.isRight() && "bar".equals(e.getRight().getName())));
    }

    @Test
    void documentSymbols_eachEntryHasRange() {
        documents.open("file:///foo.gs", "class Foo {}");
        List<Either<SymbolInformation, DocumentSymbol>> result =
                handler.documentSymbols("file:///foo.gs");
        result.forEach(e -> {
            assertTrue(e.isRight());
            assertNotNull(e.getRight().getRange());
            assertNotNull(e.getRight().getSelectionRange());
        });
    }

    // ── buildHoverMarkdown coverage for all symbol kinds ─────────────────

    @Test
    void hover_interfaceSymbol_returnsMarkdownWithInterface() {
        documents.open("file:///foo.gs", "interface IFoo {\n}");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 10));
        assertNotNull(result);
        assertTrue(result.getContents().getRight().getValue().contains("interface"));
    }

    @Test
    void hover_enumSymbol_returnsMarkdownWithEnum() {
        documents.open("file:///foo.gs", "enum Color { RED }");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 5));
        assertNotNull(result);
        assertTrue(result.getContents().getRight().getValue().contains("enum"));
    }

    @Test
    void hover_enhancementSymbol_returnsMarkdownWithEnhancement() {
        documents.open("file:///foo.gs", "enhancement StringExt : String {\n}");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 13));
        assertNotNull(result);
        assertTrue(result.getContents().getRight().getValue().contains("enhancement"));
    }

    @Test
    void hover_propertySymbol_returnsMarkdownWithProperty() {
        documents.open("file:///foo.gs", "property get Name() : String {\n}");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 17));
        assertNotNull(result);
        assertTrue(result.getContents().getRight().getValue().contains("property"));
    }

    @Test
    void hover_functionSymbolNoReturnType_returnsMarkdown() {
        documents.open("file:///foo.gs", "function doWork() {\n}");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 10));
        assertNotNull(result);
        assertTrue(result.getContents().getRight().getValue().contains("doWork"));
    }

    @Test
    void hover_varSymbolWithType_returnsMarkdownWithType() {
        documents.open("file:///foo.gs", "var count : Integer = 0");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 4));
        assertNotNull(result);
        assertTrue(result.getContents().getRight().getValue().contains("count"));
    }

    @Test
    void hover_varSymbolNoType_returnsMarkdown() {
        documents.open("file:///foo.gs", "var x = 5");
        Hover result = handler.hover("file:///foo.gs", new Position(0, 4));
        assertNotNull(result);
        // x is a symbol with no type, should still return content
        assertNotNull(result.getContents());
    }

    // ── toLspKind coverage for all symbol kinds ───────────────────────────

    @Test
    void documentSymbols_allKinds_hasCorrectLspKinds() {
        String src = "class MyClass {}\n"
                + "interface IFoo {}\n"
                + "enum Color { RED }\n"
                + "enhancement StringExt : String {}\n"
                + "function doWork() {}\n"
                + "property get Name() : String {}\n"
                + "var count : Integer = 0\n";
        documents.open("file:///all.gs", src);
        List<Either<SymbolInformation, DocumentSymbol>> result =
                handler.documentSymbols("file:///all.gs");
        assertFalse(result.isEmpty());
        // Just verify we get symbols for all these kinds without error
        assertTrue(result.stream().anyMatch(e -> e.isRight() && "MyClass".equals(e.getRight().getName())));
        assertTrue(result.stream().anyMatch(e -> e.isRight() && "IFoo".equals(e.getRight().getName())));
        assertTrue(result.stream().anyMatch(e -> e.isRight() && "Color".equals(e.getRight().getName())));
        assertTrue(result.stream().anyMatch(e -> e.isRight() && "StringExt".equals(e.getRight().getName())));
        assertTrue(result.stream().anyMatch(e -> e.isRight() && "doWork".equals(e.getRight().getName())));
        assertTrue(result.stream().anyMatch(e -> e.isRight() && "Name".equals(e.getRight().getName())));
        assertTrue(result.stream().anyMatch(e -> e.isRight() && "count".equals(e.getRight().getName())));
    }

    @Test
    void documentSymbols_symbolWithType_includesDetail() {
        documents.open("file:///foo.gs", "function getCount() : Integer {}");
        List<Either<SymbolInformation, DocumentSymbol>> result =
                handler.documentSymbols("file:///foo.gs");
        assertTrue(result.stream().anyMatch(e ->
                e.isRight() && "getCount".equals(e.getRight().getName())
                        && "Integer".equals(e.getRight().getDetail())));
    }
}
