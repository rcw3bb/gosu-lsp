package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionHandlerTest {

    private DocumentManager documents;
    private DefinitionHandler handler;

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        handler = new DefinitionHandler(documents);
    }

    @Test
    void definition_unknownUri_returnsEmpty() {
        List<Location> result = handler.definition("file:///unknown.gs", new Position(0, 0));
        assertTrue(result.isEmpty());
    }

    @Test
    void definition_emptyDocument_returnsEmpty() {
        documents.open("file:///foo.gs", "");
        List<Location> result = handler.definition("file:///foo.gs", new Position(0, 0));
        assertTrue(result.isEmpty());
    }

    @Test
    void definition_symbolInSameDoc_returnsLocation() {
        String src = "class MyService {}\nvar s : MyService = null";
        documents.open("file:///foo.gs", src);
        // Hover over "MyService" usage on line 1 (char 8 = inside 'MyService')
        List<Location> result = handler.definition("file:///foo.gs", new Position(1, 10));
        assertFalse(result.isEmpty(), "Should find definition of MyService in the same file");
        assertEquals("file:///foo.gs", result.get(0).getUri());
    }

    @Test
    void definition_symbolNotDeclared_returnsEmpty() {
        documents.open("file:///foo.gs", "var x = undeclaredSymbol");
        List<Location> result = handler.definition("file:///foo.gs", new Position(0, 16));
        assertTrue(result.isEmpty());
    }

    @Test
    void definition_crossFile_findsSymbolInOtherDoc() {
        documents.open("file:///a.gs", "class SharedClass {}");
        documents.open("file:///b.gs", "var obj : SharedClass = null");
        // hover over "SharedClass" usage in b.gs
        List<Location> result = handler.definition("file:///b.gs", new Position(0, 12));
        assertFalse(result.isEmpty(), "Should find SharedClass defined in a.gs");
        assertTrue(result.stream().anyMatch(l -> "file:///a.gs".equals(l.getUri())));
    }

    @Test
    void definition_locationHasCorrectRange() {
        documents.open("file:///foo.gs", "class FooBar {}\nvar f : FooBar");
        List<Location> result = handler.definition("file:///foo.gs", new Position(1, 9));
        assertFalse(result.isEmpty());
        assertNotNull(result.get(0).getRange());
        assertEquals(0, result.get(0).getRange().getStart().getLine());
    }

    @Test
    void definition_functionSymbol_foundInSameDoc() {
        String src = "function compute() : Integer {}\nvar x = compute()";
        documents.open("file:///foo.gs", src);
        List<Location> result = handler.definition("file:///foo.gs", new Position(1, 10));
        assertFalse(result.isEmpty());
        assertEquals("file:///foo.gs", result.get(0).getUri());
    }
}
