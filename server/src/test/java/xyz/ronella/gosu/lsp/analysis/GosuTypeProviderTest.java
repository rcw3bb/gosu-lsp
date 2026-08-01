package xyz.ronella.gosu.lsp.analysis;

import org.eclipse.lsp4j.CompletionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GosuTypeProviderTest {

    private GosuTypeProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GosuTypeProvider();
    }

    @Test
    void getMembersOf_null_returnsEmpty() {
        assertTrue(provider.getMembersOf(null).isEmpty());
    }

    @Test
    void getMembersOf_blank_returnsEmpty() {
        assertTrue(provider.getMembersOf("   ").isEmpty());
    }

    @Test
    void getMembersOf_unknownType_returnsEmpty() {
        assertTrue(provider.getMembersOf("com.unknown.VeryObscureClass99999").isEmpty());
    }

    @Test
    void getMembersOf_String_returnsMembers() {
        List<CompletionItem> items = provider.getMembersOf("String");
        assertFalse(items.isEmpty(), "java.lang.String should have members");
        assertTrue(items.stream().anyMatch(i -> i.getLabel().startsWith("length")),
                "String should expose 'length()'");
        assertTrue(items.stream().anyMatch(i -> i.getLabel().startsWith("substring")),
                "String should expose 'substring()'");
    }

    @Test
    void getMembersOf_Integer_returnsMembers() {
        List<CompletionItem> items = provider.getMembersOf("Integer");
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.getLabel().startsWith("parseInt")));
    }

    @Test
    void getMembersOf_fqnString_returnsMembers() {
        List<CompletionItem> items = provider.getMembersOf("java.lang.String");
        assertFalse(items.isEmpty(), "FQN lookup should also work");
    }

    @Test
    void getMembersOf_StringBuilder_returnsMembers() {
        List<CompletionItem> items = provider.getMembersOf("StringBuilder");
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.getLabel().startsWith("append")));
    }

    @Test
    void getMembersOf_noDuplicateLabels() {
        List<CompletionItem> items = provider.getMembersOf("String");
        long unique = items.stream().map(CompletionItem::getLabel).distinct().count();
        assertEquals(unique, items.size(), "No duplicate completion labels");
    }
}
