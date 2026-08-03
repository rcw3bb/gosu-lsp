package xyz.ronella.gosu.lsp.document;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store of open document contents, keyed by URI.
 */
public class DocumentManager {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    /** Opens {@code uri} in the store with the given initial content. */
    public void open(String uri, String content) {
        store.put(uri, content != null ? content : "");
    }

    /** Replaces the stored content for {@code uri}. */
    public void update(String uri, String content) {
        store.put(uri, content != null ? content : "");
    }

    /** Removes {@code uri} from the store. */
    public void close(String uri) {
        store.remove(uri);
    }

    /** Returns the current text content for {@code uri}, or {@code null} if not open. */
    public String getContent(String uri) {
        return store.get(uri);
    }

    /** Returns {@code true} if {@code uri} is currently open in the store. */
    public boolean isOpen(String uri) {
        return store.containsKey(uri);
    }

    /** Returns a read-only snapshot of all open documents. */
    public Map<String, String> snapshot() {
        return Collections.unmodifiableMap(store);
    }
}
