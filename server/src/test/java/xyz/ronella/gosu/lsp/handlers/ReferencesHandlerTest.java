package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReferencesHandlerTest {

    private DocumentManager documents;
    private ReferencesHandler handler;

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        handler = new ReferencesHandler(documents);
    }

    @Test
    void references_unknownUri_returnsEmpty() {
        List<Location> result = handler.references("file:///unknown.gs", new Position(0, 0), true);
        assertTrue(result.isEmpty());
    }

    @Test
    void references_emptyDocument_returnsEmpty() {
        documents.open("file:///foo.gs", "");
        List<Location> result = handler.references("file:///foo.gs", new Position(0, 0), true);
        assertTrue(result.isEmpty());
    }

    @Test
    void references_includeDeclaration_findsDeclarationAndUsages() {
        String src = "class Foo {}\nvar x : Foo = null";
        documents.open("file:///foo.gs", src);
        List<Location> result = handler.references("file:///foo.gs", new Position(1, 10), true);
        assertFalse(result.isEmpty());
        assertTrue(result.size() >= 2, "Should find class declaration and usage");
    }

    @Test
    void references_excludeDeclaration_returnsOnlyUsages() {
        String src = "class Foo {}\nvar x : Foo = null\nvar y : Foo = null";
        documents.open("file:///foo.gs", src);
        List<Location> result = handler.references("file:///foo.gs", new Position(0, 6), false);
        assertTrue(result.stream().allMatch(l -> l.getRange().getStart().getLine() > 0),
                "Declaration on line 0 should be excluded");
    }

    @Test
    void references_crossFile_findsInMultipleFiles() {
        documents.open("file:///a.gs", "class SharedClass {}");
        documents.open("file:///b.gs", "var obj : SharedClass = null");
        List<Location> result = handler.references("file:///a.gs", new Position(0, 6), true);
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(l -> "file:///b.gs".equals(l.getUri())));
    }

    @Test
    void references_noOccurrencesInOtherFile_scopedToSource() {
        documents.open("file:///a.gs", "class Alpha {}");
        documents.open("file:///b.gs", "class Beta {}");
        List<Location> result = handler.references("file:///a.gs", new Position(0, 6), true);
        assertTrue(result.stream().allMatch(l -> "file:///a.gs".equals(l.getUri())));
    }

    @Test
    void references_resultHasCorrectUri() {
        String src = "class Foo {}\nvar x : Foo";
        documents.open("file:///test.gs", src);
        List<Location> result = handler.references("file:///test.gs", new Position(0, 6), true);
        assertTrue(result.stream().allMatch(l -> "file:///test.gs".equals(l.getUri())));
    }

    @Test
    void references_resultHasNonNullRange() {
        documents.open("file:///foo.gs", "class Foo {}");
        List<Location> result = handler.references("file:///foo.gs", new Position(0, 6), true);
        result.forEach(l -> assertNotNull(l.getRange()));
    }
}
