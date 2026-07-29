package xyz.ronella.gosu.lsp.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GosuSymbolExtractorTest {

    private GosuSymbolExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new GosuSymbolExtractor();
    }

    // ── extract ──────────────────────────────────────────────────────────

    @Test
    void extract_null_returnsEmpty() {
        assertTrue(extractor.extract(null).isEmpty());
    }

    @Test
    void extract_empty_returnsEmpty() {
        assertTrue(extractor.extract("").isEmpty());
    }

    @Test
    void extract_classDeclaration() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("class MyClass {\n}");
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("MyClass") && s.kind == GosuSymbolExtractor.SymbolKind.CLASS));
    }

    @Test
    void extract_interfaceDeclaration() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("interface IFoo {\n}");
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("IFoo") && s.kind == GosuSymbolExtractor.SymbolKind.INTERFACE));
    }

    @Test
    void extract_enumDeclaration() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("enum Color { RED, GREEN }");
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("Color") && s.kind == GosuSymbolExtractor.SymbolKind.ENUM));
    }

    @Test
    void extract_enhancementDeclaration() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("enhancement StringExt : String {\n}");
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("StringExt") && s.kind == GosuSymbolExtractor.SymbolKind.ENHANCEMENT));
    }

    @Test
    void extract_functionDeclaration() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("function doSomething() : String {\n}");
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("doSomething") && s.kind == GosuSymbolExtractor.SymbolKind.FUNCTION));
    }

    @Test
    void extract_functionReturnType() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("function getCount() : Integer {\n}");
        GosuSymbolExtractor.Symbol sym = symbols.stream()
                .filter(s -> s.name.equals("getCount"))
                .findFirst().orElse(null);
        assertNotNull(sym);
        assertEquals("Integer", sym.type);
    }

    @Test
    void extract_propertyDeclaration() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("property get Name() : String {\n}");
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("Name") && s.kind == GosuSymbolExtractor.SymbolKind.PROPERTY));
    }

    @Test
    void extract_varDeclaration() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("var count : Integer = 0");
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("count") && s.kind == GosuSymbolExtractor.SymbolKind.VARIABLE));
    }

    @Test
    void extract_lineNumberIsZeroBased() {
        String src = "class TopLevel {\n  function myFunc() {}\n}";
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract(src);
        GosuSymbolExtractor.Symbol classSymbol = symbols.stream()
                .filter(s -> s.name.equals("TopLevel"))
                .findFirst().orElse(null);
        assertNotNull(classSymbol);
        assertEquals(0, classSymbol.line);
    }

    // ── extractWithPrefix ─────────────────────────────────────────────────

    @Test
    void extractWithPrefix_nullPrefix_returnsAll() {
        String src = "function alpha() {}\nfunction beta() {}";
        List<GosuSymbolExtractor.Symbol> all = extractor.extract(src);
        List<GosuSymbolExtractor.Symbol> withNull = extractor.extractWithPrefix(src, null);
        assertEquals(all.size(), withNull.size());
    }

    @Test
    void extractWithPrefix_emptyPrefix_returnsAll() {
        String src = "function alpha() {}\nfunction beta() {}";
        List<GosuSymbolExtractor.Symbol> all = extractor.extract(src);
        List<GosuSymbolExtractor.Symbol> withEmpty = extractor.extractWithPrefix(src, "");
        assertEquals(all.size(), withEmpty.size());
    }

    @Test
    void extractWithPrefix_filters_caseInsensitive() {
        String src = "function AlphaOne() {}\nfunction Beta() {}\nfunction alphaTwo() {}";
        List<GosuSymbolExtractor.Symbol> result = extractor.extractWithPrefix(src, "alpha");
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.name.toLowerCase().startsWith("alpha")));
    }

    @Test
    void extractWithPrefix_noMatch_returnsEmpty() {
        String src = "function Alpha() {}";
        List<GosuSymbolExtractor.Symbol> result = extractor.extractWithPrefix(src, "xyz");
        assertTrue(result.isEmpty());
    }

    // ── wordAt ────────────────────────────────────────────────────────────

    @Test
    void wordAt_returnsWordAtPosition() {
        String src = "var myVar = 5";
        assertEquals("myVar", GosuSymbolExtractor.wordAt(src, 0, 5));
    }

    @Test
    void wordAt_emptyContent_returnsEmpty() {
        assertEquals("", GosuSymbolExtractor.wordAt("", 0, 0));
    }

    @Test
    void wordAt_outOfBoundsLine_returnsEmpty() {
        assertEquals("", GosuSymbolExtractor.wordAt("var x = 1", 99, 0));
    }

    @Test
    void wordAt_outOfBoundsChar_returnsEmpty() {
        assertEquals("", GosuSymbolExtractor.wordAt("abc", 0, 99));
    }

    // ── lineOf / lineStart ────────────────────────────────────────────────

    @Test
    void lineOf_firstLine_returnsZero() {
        assertEquals(0, GosuSymbolExtractor.lineOf("hello world", 3));
    }

    @Test
    void lineOf_secondLine_returnsOne() {
        String src = "line1\nline2";
        assertEquals(1, GosuSymbolExtractor.lineOf(src, 6));
    }

    @Test
    void lineStart_firstLine_returnsZero() {
        assertEquals(0, GosuSymbolExtractor.lineStart("hello", 3));
    }

    @Test
    void lineStart_secondLine_returnsCorrectOffset() {
        String src = "line1\nline2";
        // offset 6 is 'l' of "line2"; lineStart should return 6
        assertEquals(6, GosuSymbolExtractor.lineStart(src, 8));
    }

    // ── isWordChar / wordAt boundary coverage ─────────────────────────────

    @Test
    void wordAt_wordWithUnderscore_included() {
        String src = "var _myVar = 5";
        assertEquals("_myVar", GosuSymbolExtractor.wordAt(src, 0, 6));
    }

    @Test
    void wordAt_wordWithDollar_included() {
        String src = "var $price = 5";
        assertEquals("$price", GosuSymbolExtractor.wordAt(src, 0, 6));
    }

    @Test
    void wordAt_cursorAtEndOfWord_returnsWord() {
        String src = "class Foo {}";
        // character at position 9 (just past end of "Foo")
        assertEquals("Foo", GosuSymbolExtractor.wordAt(src, 0, 9));
    }

    @Test
    void wordAt_cursorAtStartOfWord_returnsWord() {
        String src = "class Foo {}";
        // character at position 6 (start of "Foo")
        assertEquals("Foo", GosuSymbolExtractor.wordAt(src, 0, 6));
    }

    @Test
    void extract_varWithNoType_symbolTypeIsNull() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("var x = 5");
        GosuSymbolExtractor.Symbol sym = symbols.stream()
                .filter(s -> "x".equals(s.name))
                .findFirst().orElse(null);
        assertNotNull(sym);
        assertNull(sym.type);
    }

    @Test
    void extract_functionWithNoReturnType_typeIsNull() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("function doWork() {\n}");
        GosuSymbolExtractor.Symbol sym = symbols.stream()
                .filter(s -> "doWork".equals(s.name))
                .findFirst().orElse(null);
        assertNotNull(sym);
        assertNull(sym.type);
    }

    @Test
    void extractWithPrefix_multipleFilesOfSymbols_filtered() {
        String src = "class Alpha {}\nclass Beta {}\nclass Almond {}";
        List<GosuSymbolExtractor.Symbol> result = extractor.extractWithPrefix(src, "Al");
        assertEquals(2, result.size());
    }
}
