package com.gosu.lsp.analysis;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts ANTLR4 parse errors into LSP {@link Diagnostic} objects and
 * sends them via {@code textDocument/publishDiagnostics}.
 */
public class DiagnosticsPublisher {

    private static final String SOURCE = "gosu";

    private LanguageClient client;
    private final GosuSourceParser parser = new GosuSourceParser();

    public void connect(LanguageClient client) {
        this.client = client;
    }

    /**
     * Parses {@code content} and publishes resulting diagnostics for {@code uri}.
     * Call this on every didOpen / didChange / didSave.
     */
    public void publishDiagnostics(String uri, String content) {
        if (client == null) return;

        GosuSourceParser.ParseResult result = parser.parse(content);
        List<Diagnostic> diags = toDiagnostics(result.errors);
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diags));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static List<Diagnostic> toDiagnostics(List<GosuSourceParser.ParseError> errors) {
        List<Diagnostic> diags = new ArrayList<>(errors.size());
        for (GosuSourceParser.ParseError err : errors) {
            // ANTLR lines are 1-based; LSP lines are 0-based
            int line = Math.max(0, err.line - 1);
            int col  = Math.max(0, err.charPositionInLine);

            Range range = new Range(
                    new Position(line, col),
                    new Position(line, col + 1)   // highlight at least one character
            );

            Diagnostic d = new Diagnostic(range, err.message, DiagnosticSeverity.Error, SOURCE);
            diags.add(d);
        }
        return diags;
    }
}
