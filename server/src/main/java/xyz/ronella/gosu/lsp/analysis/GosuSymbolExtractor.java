package xyz.ronella.gosu.lsp.analysis;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.*;
import java.util.regex.*;

/**
 * Extracts symbols (variables, functions, classes, etc.) from Gosu source text
 * using lightweight regex patterns.  This is intentionally simple for Phase 1;
 * full AST-based extraction will be added in a later phase.
 */
public class GosuSymbolExtractor {

    // ── Regex patterns ────────────────────────────────────────────────────

    private static final Pattern VAR_DECL = Pattern.compile(
            "\\bvar\\s+(\\w+)(?:\\s*:\\s*([\\w<>,\\[\\]\\s]+?))?(?:\\s*=|\\s*$|\\s*\\n|\\s*;)",
            Pattern.MULTILINE);

    private static final Pattern FUNCTION_DECL = Pattern.compile(
            "\\bfunction\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)(?:\\s*:\\s*([\\w<>,\\[\\]]+))?",
            Pattern.MULTILINE);

    private static final Pattern PROPERTY_DECL = Pattern.compile(
            "\\bproperty\\s+(get|set)\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE);

    private static final Pattern CLASS_DECL = Pattern.compile(
            "\\b(class|interface|enum|enhancement)\\s+(\\w+)",
            Pattern.MULTILINE);

    // ^ anchors to line-start (MULTILINE) to avoid matching 'annotation' inside package/uses paths
    private static final Pattern ANNOTATION_DECL = Pattern.compile(
            "^\\s*annotation\\s+(\\w+)",
            Pattern.MULTILINE);

    // ── Symbol model ─────────────────────────────────────────────────────

    /** Classifies the kind of a declared symbol in Gosu source. */
    public enum SymbolKind {
        CLASS, INTERFACE, ENUM, ENHANCEMENT, ANNOTATION,
        FUNCTION, PROPERTY, VARIABLE, FIELD
    }

    /** An immutable descriptor for a named symbol extracted from Gosu source. */
    public static final class Symbol {
        public final String name;
        public final String type;       // may be null
        public final SymbolKind kind;
        /** 0-based line where the symbol was declared. */
        public final int line;
        public final int startChar;

        /** Constructs a symbol with the given name, optional type, kind, and source location. */
        public Symbol(String name, String type, SymbolKind kind, int line, int startChar) {
            this.name      = name;
            this.type      = type;
            this.kind      = kind;
            this.line      = line;
            this.startChar = startChar;
        }
    }

    // ── Extraction ────────────────────────────────────────────────────────

    /** Extracts all named symbols from {@code content} via regex-based pattern matching. */
    public List<Symbol> extract(String content) {
        if (content == null || content.isEmpty()) return Collections.emptyList();
        List<Symbol> symbols = new ArrayList<>();
        extractMatches(content, CLASS_DECL,      symbols, this::classSymbol);
        extractMatches(content, ANNOTATION_DECL, symbols, this::annotationSymbol);
        extractMatches(content, FUNCTION_DECL,   symbols, this::functionSymbol);
        extractMatches(content, PROPERTY_DECL,   symbols, this::propertySymbol);
        extractMatches(content, VAR_DECL,         symbols, this::varSymbol);
        return symbols;
    }

    /** Returns only the symbols whose names start with {@code prefix} (case-insensitive). */
    public List<Symbol> extractWithPrefix(String content, String prefix) {
        List<Symbol> all = extract(content);
        if (prefix == null || prefix.isEmpty()) return all;
        String lc = prefix.toLowerCase(Locale.ROOT);
        List<Symbol> filtered = new ArrayList<>();
        for (Symbol s : all) {
            if (s.name.toLowerCase(Locale.ROOT).startsWith(lc)) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Converts a regex {@link java.util.regex.Matcher} match at a source position to a {@link Symbol}. */
    @FunctionalInterface
    private interface MatchToSymbol {
        /** Maps a match at the given line/column to a symbol, or {@code null} to skip. */
        Symbol apply(Matcher m, int line, int startChar);
    }

    private void extractMatches(String content, Pattern pattern, List<Symbol> out,
                                 MatchToSymbol converter) {
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            int line = lineOf(content, m.start());
            int col  = m.start() - lineStart(content, m.start());
            Symbol s = converter.apply(m, line, col);
            if (s != null) out.add(s);
        }
    }

    private Symbol classSymbol(Matcher m, int line, int col) {
        String kind = m.group(1);
        String name = m.group(2);
        SymbolKind sk = switch (kind) {
            case "interface"   -> SymbolKind.INTERFACE;
            case "enum"        -> SymbolKind.ENUM;
            case "enhancement" -> SymbolKind.ENHANCEMENT;
            default            -> SymbolKind.CLASS;
        };
        return new Symbol(name, null, sk, line, col);
    }

    private Symbol annotationSymbol(Matcher m, int line, int col) {
        int nameCol = col + (m.start(1) - m.start());
        return new Symbol(m.group(1), null, SymbolKind.ANNOTATION, line, nameCol);
    }

    private Symbol functionSymbol(Matcher m, int line, int col) {
        String name   = m.group(1);
        String retType = m.groupCount() >= 3 ? m.group(3) : null;
        return new Symbol(name, retType != null ? retType.trim() : null, SymbolKind.FUNCTION, line, col);
    }

    private Symbol propertySymbol(Matcher m, int line, int col) {
        String name = m.group(2);
        return new Symbol(name, null, SymbolKind.PROPERTY, line, col);
    }

    private Symbol varSymbol(Matcher m, int line, int col) {
        String name = m.group(1);
        String type = m.groupCount() >= 2 ? m.group(2) : null;
        return new Symbol(name, type != null ? type.trim() : null, SymbolKind.VARIABLE, line, col);
    }

    /**
     * Returns all ranges where {@code word} appears as a whole identifier in {@code content}.
     * Uses word-boundary matching to avoid false positives (e.g., "Foo" won't match "FooBar").
     */
    public List<Range> findOccurrences(String content, String word) {
        if (content == null || content.isEmpty() || word == null || word.isEmpty()) {
            return Collections.emptyList();
        }
        List<Range> ranges = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            int line = lineOf(content, m.start());
            int col  = m.start() - lineStart(content, m.start());
            ranges.add(new Range(
                    new Position(line, col),
                    new Position(line, col + word.length())));
        }
        return ranges;
    }

    /** Returns the 0-based line number for the given character offset. */
    public static int lineOf(String content, int offset) {
        int count = 0;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') count++;
        }
        return count;
    }

    /** Returns the offset of the start of the line containing {@code offset}. */
    public static int lineStart(String content, int offset) {
        for (int i = offset - 1; i >= 0; i--) {
            if (content.charAt(i) == '\n') return i + 1;
        }
        return 0;
    }

    /** Returns the word at the given (line, character) position in {@code content}. */
    public static String wordAt(String content, int line, int character) {
        String[] lines = content.split("\n", -1);
        if (line < 0 || line >= lines.length) return "";
        String lineText = lines[line];
        if (character < 0 || character > lineText.length()) return "";

        int start = character;
        while (start > 0 && isWordChar(lineText.charAt(start - 1))) start--;
        int end = character;
        while (end < lineText.length() && isWordChar(lineText.charAt(end))) end++;
        return lineText.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
