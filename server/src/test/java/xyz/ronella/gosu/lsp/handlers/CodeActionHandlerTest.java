package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeActionHandlerTest {

    private DocumentManager documents;
    private CodeActionHandler handler;

    private static final String URI = "file:///foo.gs";

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        documents.open(URI, "class Foo {}");
        handler = new CodeActionHandler();
    }

    // ── No diagnostics ────────────────────────────────────────────────────

    @Test
    void getActions_nullDiagnostics_returnsEmpty() {
        Range range = new Range(new Position(0, 0), new Position(0, 5));
        assertTrue(handler.getActions(URI, range, null).isEmpty());
    }

    @Test
    void getActions_emptyDiagnostics_returnsEmpty() {
        Range range = new Range(new Position(0, 0), new Position(0, 5));
        assertTrue(handler.getActions(URI, range, List.of()).isEmpty());
    }

    // ── Missing semicolon ─────────────────────────────────────────────────

    @Test
    void getActions_missingSemicolon_producesInsertEdit() {
        Diagnostic diag = new Diagnostic(
                new Range(new Position(0, 10), new Position(0, 11)),
                "missing ';' at '}'",
                DiagnosticSeverity.Error, "gosu");

        List<Either<Command, CodeAction>> actions =
                handler.getActions(URI, diag.getRange(), List.of(diag));

        assertEquals(1, actions.size());
        CodeAction action = actions.get(0).getRight();
        assertNotNull(action);
        assertEquals(CodeActionKind.QuickFix, action.getKind());
        assertEquals("Insert ';'", action.getTitle());

        List<TextEdit> edits = action.getEdit().getChanges().get(URI);
        assertNotNull(edits);
        assertEquals(1, edits.size());
        assertEquals(";", edits.get(0).getNewText());
    }

    @Test
    void getActions_missingSemicolon_editInsertedAtEndPosition() {
        Position end = new Position(2, 15);
        Diagnostic diag = new Diagnostic(
                new Range(new Position(2, 14), end),
                "missing ';' at 'EOF'",
                DiagnosticSeverity.Error, "gosu");

        List<Either<Command, CodeAction>> actions =
                handler.getActions(URI, diag.getRange(), List.of(diag));

        assertFalse(actions.isEmpty());
        TextEdit edit = actions.get(0).getRight().getEdit().getChanges().get(URI).get(0);
        // Insert position must be at the end of the diagnostic range
        assertEquals(end, edit.getRange().getStart());
        assertEquals(end, edit.getRange().getEnd());
    }

    // ── Extraneous / mismatched input ─────────────────────────────────────

    @Test
    void getActions_extraneousInput_producesDeleteEdit() {
        Diagnostic diag = new Diagnostic(
                new Range(new Position(1, 4), new Position(1, 7)),
                "extraneous input 'foo' expecting '}'",
                DiagnosticSeverity.Error, "gosu");

        List<Either<Command, CodeAction>> actions =
                handler.getActions(URI, diag.getRange(), List.of(diag));

        assertEquals(1, actions.size());
        CodeAction action = actions.get(0).getRight();
        assertEquals("Remove unexpected token", action.getTitle());

        TextEdit edit = action.getEdit().getChanges().get(URI).get(0);
        assertEquals("", edit.getNewText(), "Extraneous-input fix must delete the range");
        assertEquals(diag.getRange(), edit.getRange());
    }

    @Test
    void getActions_mismatchedInput_producesDeleteEdit() {
        Diagnostic diag = new Diagnostic(
                new Range(new Position(0, 6), new Position(0, 9)),
                "mismatched input 'bar' expecting '{'",
                DiagnosticSeverity.Error, "gosu");

        List<Either<Command, CodeAction>> actions =
                handler.getActions(URI, diag.getRange(), List.of(diag));

        assertFalse(actions.isEmpty());
        assertEquals("Remove unexpected token", actions.get(0).getRight().getTitle());
    }

    @Test
    void getActions_noViableAlternative_producesDeleteEdit() {
        Diagnostic diag = new Diagnostic(
                new Range(new Position(0, 0), new Position(0, 3)),
                "no viable alternative at input '###'",
                DiagnosticSeverity.Error, "gosu");

        List<Either<Command, CodeAction>> actions =
                handler.getActions(URI, diag.getRange(), List.of(diag));

        assertFalse(actions.isEmpty());
        assertEquals("Remove unexpected token", actions.get(0).getRight().getTitle());
    }

    // ── Unrecognised message ──────────────────────────────────────────────

    @Test
    void getActions_unknownDiagnostic_returnsEmpty() {
        Diagnostic diag = new Diagnostic(
                new Range(new Position(0, 0), new Position(0, 5)),
                "some unknown error",
                DiagnosticSeverity.Error, "gosu");

        List<Either<Command, CodeAction>> actions =
                handler.getActions(URI, diag.getRange(), List.of(diag));

        assertTrue(actions.isEmpty(), "Unknown diagnostic must produce no actions");
    }

    // ── Multiple diagnostics ──────────────────────────────────────────────

    @Test
    void getActions_multipleDiagnostics_producesOneActionEach() {
        Diagnostic d1 = new Diagnostic(
                new Range(new Position(0, 5), new Position(0, 6)),
                "missing ';' at '}'",
                DiagnosticSeverity.Error, "gosu");
        Diagnostic d2 = new Diagnostic(
                new Range(new Position(1, 0), new Position(1, 3)),
                "extraneous input 'xxx' expecting '}'",
                DiagnosticSeverity.Error, "gosu");

        List<Either<Command, CodeAction>> actions =
                handler.getActions(URI, new Range(new Position(0, 0), new Position(1, 3)),
                        List.of(d1, d2));

        assertEquals(2, actions.size());
    }
}
