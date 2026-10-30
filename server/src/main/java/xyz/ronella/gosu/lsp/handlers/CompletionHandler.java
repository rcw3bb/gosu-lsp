package xyz.ronella.gosu.lsp.handlers;

import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor;
import xyz.ronella.gosu.lsp.analysis.GosuSymbolExtractor.Symbol;
import xyz.ronella.gosu.lsp.analysis.GosuTypeProvider;
import xyz.ronella.gosu.lsp.document.DocumentManager;
import org.eclipse.lsp4j.*;

import java.util.*;

/**
 * Provides code-completion items for a given cursor position.
 *
 * Phase 2 strategy:
 *   0. Dot-triggered type-aware completions (members of the receiver''s declared type).
 *   1. All Gosu keywords.
 *   2. Common built-in types.
 *   3. Symbols (vars, functions, classes) found anywhere in the current file.
 */
public class CompletionHandler {

    // -- Gosu keywords -------------------------------------------------------

    private static final List<String> KEYWORDS = List.of(
            "package", "uses", "class", "interface", "enum", "enhancement",
            "extends", "implements", "represents",
            "construct", "function", "property", "var", "delegate",
            "as", "readonly", "get", "set",
            "if", "else", "try", "catch", "finally", "throw",
            "return", "for", "foreach", "in", "index", "iterator",
            "while", "do", "switch", "case", "default", "break", "continue",
            "assert", "using", "eval", "new", "this", "super",
            "typeis", "typeas", "typeof", "statictypeof", "block",
            "or", "and", "not",
            "true", "false", "null", "NaN", "Infinity",
            "private", "internal", "protected", "public",
            "static", "abstract", "override", "final", "transient", "reified"
    );

    // -- Common built-in types -----------------------------------------------

    private static final List<String> BUILTIN_TYPES = List.of(
            "String", "Integer", "Long", "Short", "Byte", "Float", "Double",
            "Boolean", "Character", "Number", "BigDecimal", "BigInteger",
            "Object", "void",
            "List", "ArrayList", "LinkedList",
            "Map", "HashMap", "LinkedHashMap", "TreeMap",
            "Set", "HashSet", "LinkedHashSet", "TreeSet",
            "Iterator", "Iterable", "Collection",
            "Date", "Calendar", "LocalDate", "LocalDateTime",
            "StringBuilder", "StringBuffer",
            "Exception", "RuntimeException", "Error", "Throwable",
            "Thread", "Runnable"
    );

    // ------------------------------------------------------------------------

    private final DocumentManager documents;
    private final GosuSymbolExtractor extractor = new GosuSymbolExtractor();
    private final GosuTypeProvider typeProvider = new GosuTypeProvider();

    /** Creates a new {@code CompletionHandler} backed by the given {@link DocumentManager}. */
    public CompletionHandler(DocumentManager documents) {
        this.documents = documents;
    }

    /** Returns completion items for the cursor position in the document at {@code uri}. */
    public List<CompletionItem> complete(String uri, Position position) {
        String content = documents.getContent(uri);

        // 0. Dot-triggered type-aware completions
        if (isDotTriggered(content, position)) {
            String receiverType = resolveReceiverType(content, position);
            if (receiverType != null) {
                List<CompletionItem> members = typeProvider.getMembersOf(receiverType);
                if (!members.isEmpty()) return members;
            }
        }

        String prefix  = currentWordPrefix(content, position);
        List<CompletionItem> items = new ArrayList<>();

        // 1. Keywords
        for (String kw : KEYWORDS) {
            if (kw.startsWith(prefix)) {
                CompletionItem item = new CompletionItem(kw);
                item.setKind(CompletionItemKind.Keyword);
                items.add(item);
            }
        }

        // 2. Built-in types
        for (String type : BUILTIN_TYPES) {
            if (type.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                CompletionItem item = new CompletionItem(type);
                item.setKind(CompletionItemKind.Class);
                items.add(item);
            }
        }

        // 3. Symbols from current document
        if (content != null) {
            List<Symbol> symbols = extractor.extractWithPrefix(content, prefix);
            for (Symbol sym : symbols) {
                CompletionItem item = new CompletionItem(sym.name);
                item.setKind(toCompletionKind(sym));
                if (sym.type != null) item.setDetail(sym.type);
                items.add(item);
            }
        }

        return items;
    }

    // -- Helpers -------------------------------------------------------------

    /** Returns true when there is a '.' immediately before the cursor. */
    private static boolean isDotTriggered(String content, Position pos) {
        if (content == null) return false;
        String[] lines = content.split("\n", -1);
        int line = pos.getLine();
        if (line < 0 || line >= lines.length) return false;
        String lineText = lines[line];
        int col = pos.getCharacter();
        if (col <= 0) return false;
        // Clamp col to line length before any charAt access
        int i = Math.min(col, lineText.length());
        while (i > 0 && isWordChar(lineText.charAt(i - 1))) i--;
        return i > 0 && lineText.charAt(i - 1) == '.';
    }

    /**
     * Walks back from the cursor to find the receiver word before the dot and
     * looks up its declared type in the document symbol table.
     */
    private String resolveReceiverType(String content, Position pos) {
        if (content == null) return null;
        String[] lines = content.split("\n", -1);
        int line = pos.getLine();
        if (line < 0 || line >= lines.length) return null;
        String lineText = lines[line];
        int col = pos.getCharacter();
        if (col <= 0) return null;

        // Skip back past partial word, then past the dot
        int dotIdx = Math.min(col, lineText.length());
        while (dotIdx > 0 && isWordChar(lineText.charAt(dotIdx - 1))) dotIdx--;
        dotIdx--; // now at potential '.' position
        if (dotIdx < 0 || lineText.charAt(dotIdx) != '.') return null;

        // Extract receiver word before the dot
        int receiverEnd   = dotIdx;
        int receiverStart = receiverEnd;
        while (receiverStart > 0 && isWordChar(lineText.charAt(receiverStart - 1))) receiverStart--;
        if (receiverStart == receiverEnd) return null;

        String receiverName = lineText.substring(receiverStart, receiverEnd);

        // Resolve declared type from document symbols
        for (Symbol sym : extractor.extract(content)) {
            if (sym.name.equals(receiverName) && sym.type != null) {
                return sym.type;
            }
        }
        return null;
    }

    /** Returns the partial word the cursor is sitting on (may be empty). */
    private static String currentWordPrefix(String content, Position pos) {
        if (content == null) return "";
        String[] lines = content.split("\n", -1);
        int line = pos.getLine();
        int col  = pos.getCharacter();
        if (line < 0 || line >= lines.length) return "";
        String lineText = lines[line];
        if (col <= 0 || col > lineText.length()) return "";

        int start = col;
        while (start > 0 && isWordChar(lineText.charAt(start - 1))) start--;
        return lineText.substring(start, col);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static CompletionItemKind toCompletionKind(Symbol sym) {
        return switch (sym.kind) {
            case CLASS, ENHANCEMENT -> CompletionItemKind.Class;
            case INTERFACE          -> CompletionItemKind.Interface;
            case ENUM               -> CompletionItemKind.Enum;
            case ANNOTATION         -> CompletionItemKind.Reference;
            case FUNCTION           -> CompletionItemKind.Function;
            case PROPERTY           -> CompletionItemKind.Property;
            case VARIABLE, FIELD    -> CompletionItemKind.Variable;
        };
    }
}
