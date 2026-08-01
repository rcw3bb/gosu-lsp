package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenameHandlerTest {

    private DocumentManager documents;
    private RenameHandler handler;

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        handler = new RenameHandler(documents);
    }

    // ── prepareRename ─────────────────────────────────────────────────────

    @Test
    void prepareRename_unknownUri_returnsNull() {
        assertNull(handler.prepareRename("file:///unknown.gs", new Position(0, 0)));
    }

    @Test
    void prepareRename_emptyDocument_returnsNull() {
        documents.open("file:///foo.gs", "");
        assertNull(handler.prepareRename("file:///foo.gs", new Position(0, 0)));
    }

    @Test
    void prepareRename_onSymbol_returnsRange() {
        documents.open("file:///foo.gs", "class Foo {}");
        Range range = handler.prepareRename("file:///foo.gs", new Position(0, 6));
        assertNotNull(range);
        assertEquals(0, range.getStart().getLine());
        assertEquals(0, range.getEnd().getLine());
    }

    @Test
    void prepareRename_rangeCoversWord() {
        documents.open("file:///foo.gs", "class Foo {}");
        Range range = handler.prepareRename("file:///foo.gs", new Position(0, 6));
        assertNotNull(range);
        // "Foo" starts at char 6 and ends at char 9
        assertEquals(6, range.getStart().getCharacter());
        assertEquals(9, range.getEnd().getCharacter());
    }

    // ── rename ────────────────────────────────────────────────────────────

    @Test
    void rename_unknownUri_returnsEmptyEdit() {
        WorkspaceEdit edit = handler.rename("file:///unknown.gs", new Position(0, 0), "NewName");
        assertNotNull(edit);
        assertTrue(edit.getChanges() == null || edit.getChanges().isEmpty());
    }

    @Test
    void rename_blankNewName_returnsEmptyEdit() {
        documents.open("file:///foo.gs", "class Foo {}");
        WorkspaceEdit edit = handler.rename("file:///foo.gs", new Position(0, 6), "   ");
        assertNotNull(edit);
        assertTrue(edit.getChanges() == null || edit.getChanges().isEmpty());
    }

    @Test
    void rename_singleDoc_renamesAll() {
        String src = "class Foo {}\nvar x : Foo = null";
        documents.open("file:///foo.gs", src);
        WorkspaceEdit edit = handler.rename("file:///foo.gs", new Position(0, 6), "Bar");
        assertNotNull(edit);
        Map<String, List<TextEdit>> changes = edit.getChanges();
        assertFalse(changes.isEmpty());
        List<TextEdit> edits = changes.get("file:///foo.gs");
        assertNotNull(edits);
        assertTrue(edits.size() >= 2, "Both 'Foo' occurrences should be renamed");
        assertTrue(edits.stream().allMatch(e -> "Bar".equals(e.getNewText())));
    }

    @Test
    void rename_crossFile_renamesInBothDocs() {
        documents.open("file:///a.gs", "class Shared {}");
        documents.open("file:///b.gs", "var x : Shared = null");
        WorkspaceEdit edit = handler.rename("file:///a.gs", new Position(0, 6), "Renamed");
        assertNotNull(edit);
        Map<String, List<TextEdit>> changes = edit.getChanges();
        assertTrue(changes.containsKey("file:///a.gs"), "Should rename in a.gs");
        assertTrue(changes.containsKey("file:///b.gs"), "Should rename in b.gs");
    }

    @Test
    void rename_editsHaveCorrectNewText() {
        documents.open("file:///foo.gs", "function doWork() {}");
        WorkspaceEdit edit = handler.rename("file:///foo.gs", new Position(0, 9), "execute");
        Map<String, List<TextEdit>> changes = edit.getChanges();
        List<TextEdit> edits = changes.get("file:///foo.gs");
        assertNotNull(edits);
        assertTrue(edits.stream().allMatch(e -> "execute".equals(e.getNewText())));
    }

    @Test
    void rename_wordNotInDoc_returnsEmptyEdit() {
        // Only whitespace — wordAt returns "" so no edits are produced
        documents.open("file:///foo.gs", "   ");
        WorkspaceEdit edit = handler.rename("file:///foo.gs", new Position(0, 1), "NewName");
        assertNotNull(edit);
        assertTrue(edit.getChanges() == null || edit.getChanges().isEmpty());
    }
}
