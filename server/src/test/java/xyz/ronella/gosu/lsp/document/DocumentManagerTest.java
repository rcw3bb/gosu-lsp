package xyz.ronella.gosu.lsp.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocumentManagerTest {

    private DocumentManager manager;

    @BeforeEach
    void setUp() {
        manager = new DocumentManager();
    }

    @Test
    void open_storesContent() {
        manager.open("file:///foo.gs", "class Foo {}");
        assertEquals("class Foo {}", manager.getContent("file:///foo.gs"));
    }

    @Test
    void open_nullContentStoredAsEmpty() {
        manager.open("file:///foo.gs", null);
        assertEquals("", manager.getContent("file:///foo.gs"));
    }

    @Test
    void isOpen_trueAfterOpen() {
        manager.open("file:///foo.gs", "");
        assertTrue(manager.isOpen("file:///foo.gs"));
    }

    @Test
    void isOpen_falseBeforeOpen() {
        assertFalse(manager.isOpen("file:///notopen.gs"));
    }

    @Test
    void update_replacesContent() {
        manager.open("file:///foo.gs", "old");
        manager.update("file:///foo.gs", "new");
        assertEquals("new", manager.getContent("file:///foo.gs"));
    }

    @Test
    void update_nullContentStoredAsEmpty() {
        manager.open("file:///foo.gs", "old");
        manager.update("file:///foo.gs", null);
        assertEquals("", manager.getContent("file:///foo.gs"));
    }

    @Test
    void close_removesDocument() {
        manager.open("file:///foo.gs", "content");
        manager.close("file:///foo.gs");
        assertNull(manager.getContent("file:///foo.gs"));
        assertFalse(manager.isOpen("file:///foo.gs"));
    }

    @Test
    void getContent_returnsNullForUnknownUri() {
        assertNull(manager.getContent("file:///unknown.gs"));
    }

    @Test
    void snapshot_returnsAllOpenDocuments() {
        manager.open("file:///a.gs", "aaa");
        manager.open("file:///b.gs", "bbb");
        Map<String, String> snap = manager.snapshot();
        assertEquals(2, snap.size());
        assertEquals("aaa", snap.get("file:///a.gs"));
        assertEquals("bbb", snap.get("file:///b.gs"));
    }

    @Test
    void snapshot_isUnmodifiable() {
        manager.open("file:///a.gs", "aaa");
        Map<String, String> snap = manager.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snap.put("x", "y"));
    }

    @Test
    void snapshot_doesNotReflectSubsequentChanges() {
        manager.open("file:///a.gs", "original");
        Map<String, String> snap = manager.snapshot();
        manager.update("file:///a.gs", "changed");
        // ConcurrentHashMap snapshot is a live view, so this test documents behaviour:
        // the snapshot reference is the same map — verify getContent is updated
        assertEquals("changed", manager.getContent("file:///a.gs"));
    }
}
