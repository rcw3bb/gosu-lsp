package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InlayHintHandlerTest {

    private DocumentManager documents;
    private InlayHintHandler handler;

    private static final String URI = "file:///foo.gs";
    /** A range that spans the whole document (lines 0–99). */
    private static final Range FULL = new Range(new Position(0, 0), new Position(99, 0));

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        handler = new InlayHintHandler(documents);
    }

    // ── Unknown / empty document ──────────────────────────────────────────

    @Test
    void getHints_unknownUri_returnsEmpty() {
        assertTrue(handler.getHints("file:///unknown.gs", FULL).isEmpty());
    }

    @Test
    void getHints_emptyContent_returnsEmpty() {
        documents.open(URI, "");
        assertTrue(handler.getHints(URI, FULL).isEmpty());
    }

    // ── Variable type hints ───────────────────────────────────────────────

    @Test
    void getHints_varWithType_producesTypeHint() {
        documents.open(URI, "var count : Integer = 0");
        List<InlayHint> hints = handler.getHints(URI, FULL);

        assertTrue(hints.stream().anyMatch(h ->
                h.getLabel().getLeft().contains("Integer")),
                "Should produce a type hint containing 'Integer'");
    }

    @Test
    void getHints_typeHintKind_isType() {
        documents.open(URI, "var count : Integer = 0");
        List<InlayHint> hints = handler.getHints(URI, FULL);

        InlayHint typeHint = hints.stream()
                .filter(h -> h.getLabel().getLeft().contains("Integer"))
                .findFirst().orElse(null);
        assertNotNull(typeHint);
        assertEquals(InlayHintKind.Type, typeHint.getKind());
    }

    @Test
    void getHints_typeHintPosition_afterVariableName() {
        documents.open(URI, "var count : Integer = 0");
        List<InlayHint> hints = handler.getHints(URI, FULL);

        InlayHint typeHint = hints.stream()
                .filter(h -> h.getLabel().getLeft().contains("Integer"))
                .findFirst().orElse(null);
        assertNotNull(typeHint);
        // "var " is 4 chars; "count" starts at col 4 and ends at col 9
        assertEquals(0, typeHint.getPosition().getLine());
        assertTrue(typeHint.getPosition().getCharacter() >= 4,
                "Type hint must be positioned after the variable name");
    }

    @Test
    void getHints_varWithoutType_producesNoTypeHint() {
        documents.open(URI, "var x = 42");
        List<InlayHint> hints = handler.getHints(URI, FULL);
        // No type annotation → no type hint for x
        assertTrue(hints.stream()
                .filter(h -> h.getKind() == InlayHintKind.Type)
                .noneMatch(h -> h.getLabel().getLeft().contains("x")));
    }

    // ── Parameter name hints ──────────────────────────────────────────────

    @Test
    void getHints_callSiteMatchingDeclaration_producesParamHints() {
        String code = "function greet(name : String, times : Integer) {}\n"
                    + "greet(\"World\", 3)";
        documents.open(URI, code);
        List<InlayHint> hints = handler.getHints(URI, FULL);

        assertTrue(hints.stream().anyMatch(h ->
                h.getKind() == InlayHintKind.Parameter
                && h.getLabel().getLeft().contains("name")),
                "Should produce a 'name:' parameter hint");
        assertTrue(hints.stream().anyMatch(h ->
                h.getKind() == InlayHintKind.Parameter
                && h.getLabel().getLeft().contains("times")),
                "Should produce a 'times:' parameter hint");
    }

    @Test
    void getHints_paramHintKind_isParameter() {
        String code = "function add(a : Integer, b : Integer) {}\nadd(1, 2)";
        documents.open(URI, code);
        List<InlayHint> hints = handler.getHints(URI, FULL);

        assertTrue(hints.stream().anyMatch(h -> h.getKind() == InlayHintKind.Parameter),
                "Parameter hints must have kind Parameter");
    }

    @Test
    void getHints_callSiteNoMatchingDeclaration_producesNoParamHints() {
        // Call site for 'unknown' — not declared in this file
        documents.open(URI, "unknown(1, 2)");
        List<InlayHint> hints = handler.getHints(URI, FULL);

        assertTrue(hints.stream()
                .noneMatch(h -> h.getKind() == InlayHintKind.Parameter),
                "No param hints for undeclared functions");
    }

    // ── Range filtering ───────────────────────────────────────────────────

    @Test
    void getHints_outOfRange_returnsEmpty() {
        documents.open(URI, "var x : Integer = 0");
        // Request hints for line 5 only — content is on line 0
        Range outOfRange = new Range(new Position(5, 0), new Position(5, 0));
        List<InlayHint> hints = handler.getHints(URI, outOfRange);
        assertTrue(hints.isEmpty(), "Hints outside the requested range must not be returned");
    }
}
