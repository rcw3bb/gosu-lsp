package xyz.ronella.gosu.lsp.handlers;

import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor;
import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor.Symbol;
import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor.SymbolKind;
import xyz.ronella.gosu.lsp.document.DocumentManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.*;
import java.util.regex.*;

/**
 * Handles textDocument/inlayHint requests.
 *
 * Produces two kinds of hints:
 * <ul>
 *   <li><b>Type hints</b> — appended after a {@code var} declaration whose
 *       type was inferred by {@link GosuSymbolExtractor}: {@code var x: String}</li>
 *   <li><b>Parameter-name hints</b> — prepended to each argument at a call
 *       site whose function is declared in the same file:
 *       {@code foo(/*count:*&#47; 1, /*name:*&#47; "a")}</li>
 * </ul>
 *
 * Both hint types are restricted to the {@link Range} requested by the client.
 */
public class InlayHintHandler {

    /** Matches function declarations and captures (name, parameter-list). */
    private static final Pattern FUNC_DECL = Pattern.compile(
            "\\bfunction\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)",
            Pattern.MULTILINE);

    /** Matches call sites and captures (functionName, argument-list). */
    private static final Pattern CALL_SITE = Pattern.compile(
            "\\b(\\w+)\\s*\\(([^)]*)\\)",
            Pattern.MULTILINE);

    private final DocumentManager documents;
    private final GosuSymbolExtractor extractor = new GosuSymbolExtractor();

    /** Creates a new {@code InlayHintHandler} backed by the given {@link DocumentManager}. */
    public InlayHintHandler(DocumentManager documents) {
        this.documents = documents;
    }

    // ── Public API ────────────────────────────────────────────────

    /** Returns inlay hints (type and parameter) for the given range in {@code uri}. */
    public List<InlayHint> getHints(String uri, Range range) {
        String content = documents.getContent(uri);
        if (content == null || content.isEmpty()) return Collections.emptyList();

        List<InlayHint> hints = new ArrayList<>();
        hints.addAll(variableTypeHints(content, range));
        hints.addAll(parameterNameHints(content, range));
        return hints;
    }

    // ── Variable type hints ───────────────────────────────────────────────

    private List<InlayHint> variableTypeHints(String content, Range range) {
        List<InlayHint> hints = new ArrayList<>();
        for (Symbol sym : extractor.extract(content)) {
            if (sym.kind != SymbolKind.VARIABLE) continue;
            if (sym.type == null || sym.type.isBlank()) continue;
            if (!inRange(sym.line, range)) continue;

            Position pos = new Position(sym.line, sym.startChar + sym.name.length());
            InlayHint hint = new InlayHint(pos, Either.forLeft(": " + sym.type));
            hint.setKind(InlayHintKind.Type);
            hint.setPaddingLeft(false);
            hint.setPaddingRight(true);
            hints.add(hint);
        }
        return hints;
    }

    // ── Parameter name hints ──────────────────────────────────────────────

    private List<InlayHint> parameterNameHints(String content, Range range) {
        Map<String, List<String>> funcParams = buildParamMap(content);
        if (funcParams.isEmpty()) return Collections.emptyList();

        List<InlayHint> hints = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        int startLine = range.getStart().getLine();
        int endLine   = Math.min(range.getEnd().getLine(), lines.length - 1);

        for (int lineIdx = startLine; lineIdx <= endLine; lineIdx++) {
            String line = lines[lineIdx];
            Matcher cm = CALL_SITE.matcher(line);
            while (cm.find()) {
                String funcName = cm.group(1);
                List<String> params = funcParams.get(funcName);
                if (params == null || params.isEmpty()) continue;

                String argText = cm.group(2).trim();
                if (argText.isEmpty()) continue;

                String[] args  = argText.split(",");
                int count      = Math.min(args.length, params.size());
                int argStart   = cm.start(2);

                for (int a = 0; a < count; a++) {
                    // Advance argStart past previous arguments
                    if (a > 0) argStart += args[a - 1].length() + 1; // +1 for the comma

                    // Skip leading whitespace within the argument
                    int col = argStart;
                    while (col < line.length() && line.charAt(col) == ' ') col++;

                    InlayHint hint = new InlayHint(
                            new Position(lineIdx, col),
                            Either.forLeft(params.get(a) + ":"));
                    hint.setKind(InlayHintKind.Parameter);
                    hint.setPaddingRight(true);
                    hints.add(hint);
                }
            }
        }
        return hints;
    }

    /** Builds a map from function name to its ordered parameter names. */
    private static Map<String, List<String>> buildParamMap(String content) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        Matcher m = FUNC_DECL.matcher(content);
        while (m.find()) {
            String funcName  = m.group(1);
            String paramText = m.group(2).trim();
            if (paramText.isEmpty()) continue;

            List<String> params = new ArrayList<>();
            for (String p : paramText.split(",")) {
                // Parameter format examples: "x : String", "final x", "x = 0"
                String pName = p.trim().split("[:\\s=]")[0].trim();
                if (!pName.isEmpty()) params.add(pName);
            }
            if (!params.isEmpty()) map.put(funcName, params);
        }
        return map;
    }

    private static boolean inRange(int line, Range range) {
        return line >= range.getStart().getLine() && line <= range.getEnd().getLine();
    }
}
