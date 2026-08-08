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
    void extract_annotationDeclaration() {
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract("annotation StringFieldInfuser {\n}");
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("StringFieldInfuser") && s.kind == GosuSymbolExtractor.SymbolKind.ANNOTATION));
    }

    @Test
    void extract_annotationDeclarationNotFalseMatchedInPackagePath() {
        // 'annotation' inside a package/uses path must NOT be extracted as a symbol
        String src = "package xyz.example.annotation\n"
                   + "uses java.lang.annotation.Target\n"
                   + "annotation CalculatedFieldInfuser {\n}";
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract(src);
        // Only CalculatedFieldInfuser should be an ANNOTATION symbol — not 'Target' or 'uses'
        assertTrue(symbols.stream().anyMatch(s ->
                s.name.equals("CalculatedFieldInfuser") && s.kind == GosuSymbolExtractor.SymbolKind.ANNOTATION));
        assertTrue(symbols.stream().noneMatch(s ->
                s.kind == GosuSymbolExtractor.SymbolKind.ANNOTATION && !s.name.equals("CalculatedFieldInfuser")));
    }

    @Test
    void extract_annotationSymbol_startCharPointsToName() {
        String src = "annotation MyAnnotation {\n}";
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract(src);
        GosuSymbolExtractor.Symbol sym = symbols.stream()
                .filter(s -> s.name.equals("MyAnnotation"))
                .findFirst().orElse(null);
        assertNotNull(sym);
        // 'annotation ' is 11 chars, so name starts at col 11
        assertEquals(11, sym.startChar);
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

    // ── buildLineOffsets / lineAtOffset ───────────────────────────────────

    @Test
    void buildLineOffsets_singleLine_containsOnlyZero() {
        assertArrayEquals(new int[]{0}, GosuSymbolExtractor.buildLineOffsets("hello"));
    }

    @Test
    void buildLineOffsets_multipleLines_correctOffsets() {
        // "ab\ncd\nef" — line 0 at 0, line 1 at 3, line 2 at 6
        assertArrayEquals(new int[]{0, 3, 6}, GosuSymbolExtractor.buildLineOffsets("ab\ncd\nef"));
    }

    @Test
    void lineAtOffset_firstLine_returnsZero() {
        int[] offsets = GosuSymbolExtractor.buildLineOffsets("hello world");
        assertEquals(0, GosuSymbolExtractor.lineAtOffset(offsets, 3));
    }

    @Test
    void lineAtOffset_secondLine_returnsOne() {
        int[] offsets = GosuSymbolExtractor.buildLineOffsets("line1\nline2");
        assertEquals(1, GosuSymbolExtractor.lineAtOffset(offsets, 6));
    }

    @Test
    void lineAtOffset_exactLineStart_returnsCorrectLine() {
        int[] offsets = GosuSymbolExtractor.buildLineOffsets("a\nb\nc");
        assertEquals(1, GosuSymbolExtractor.lineAtOffset(offsets, 2));
        assertEquals(2, GosuSymbolExtractor.lineAtOffset(offsets, 4));
    }

    @Test
    void extractResults_lineNumbersCorrectAfterOptimization() {
        String src = "class Foo {\n  function bar() : String {}\n  var count : Integer = 0\n}";
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract(src);
        GosuSymbolExtractor.Symbol bar = symbols.stream()
                .filter(s -> s.name.equals("bar")).findFirst().orElse(null);
        assertNotNull(bar);
        assertEquals(1, bar.line);
        GosuSymbolExtractor.Symbol count = symbols.stream()
                .filter(s -> s.name.equals("count")).findFirst().orElse(null);
        assertNotNull(count);
        assertEquals(2, count.line);
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

    // ── findOccurrences ───────────────────────────────────────────────────

    @Test
    void findOccurrences_nullContent_returnsEmpty() {
        assertTrue(extractor.findOccurrences(null, "Foo").isEmpty());
    }

    @Test
    void findOccurrences_emptyContent_returnsEmpty() {
        assertTrue(extractor.findOccurrences("", "Foo").isEmpty());
    }

    @Test
    void findOccurrences_emptyWord_returnsEmpty() {
        assertTrue(extractor.findOccurrences("class Foo {}", "").isEmpty());
    }

    @Test
    void findOccurrences_singleOccurrence_returnsOneRange() {
        String src = "class Foo {}";
        List<org.eclipse.lsp4j.Range> ranges = extractor.findOccurrences(src, "Foo");
        assertEquals(1, ranges.size());
        assertEquals(0, ranges.get(0).getStart().getLine());
        assertEquals(6, ranges.get(0).getStart().getCharacter());
    }

    @Test
    void findOccurrences_multipleOccurrences_returnsAll() {
        String src = "class Foo {}\nvar x : Foo = null";
        List<org.eclipse.lsp4j.Range> ranges = extractor.findOccurrences(src, "Foo");
        assertEquals(2, ranges.size());
    }

    @Test
    void findOccurrences_wholeWordOnly_doesNotMatchSubstring() {
        String src = "class FooBar {}\nvar x : Foo = null";
        List<org.eclipse.lsp4j.Range> ranges = extractor.findOccurrences(src, "Foo");
        // "FooBar" must NOT be included; only the standalone "Foo"
        assertEquals(1, ranges.size());
        assertEquals(1, ranges.get(0).getStart().getLine());
    }

    @Test
    void findOccurrences_rangeCoversExactWord() {
        String src = "var count : Integer";
        List<org.eclipse.lsp4j.Range> ranges = extractor.findOccurrences(src, "count");
        assertEquals(1, ranges.size());
        org.eclipse.lsp4j.Range r = ranges.get(0);
        assertEquals(0, r.getStart().getLine());
        assertEquals(4, r.getStart().getCharacter());
        assertEquals(9, r.getEnd().getCharacter()); // "count" = 5 chars
    }

    @Test
    void findOccurrences_multiLine_correctLineNumbers() {
        String src = "class Alpha {}\nclass Beta {}\nvar x : Alpha = null";
        List<org.eclipse.lsp4j.Range> ranges = extractor.findOccurrences(src, "Alpha");
        assertEquals(2, ranges.size());
        assertEquals(0, ranges.get(0).getStart().getLine());
        assertEquals(2, ranges.get(1).getStart().getLine());
    }

    @Test
    void findOccurrences_wordInLineComment_notReturned() {
        String src = "var Foo : String\n// Foo is used here\nvar bar : Foo";
        List<org.eclipse.lsp4j.Range> ranges = extractor.findOccurrences(src, "Foo");
        assertEquals(2, ranges.size()); // line 0 and line 2 only
        assertTrue(ranges.stream().noneMatch(r -> r.getStart().getLine() == 1));
    }

    @Test
    void findOccurrences_wordInBlockComment_notReturned() {
        String src = "var Foo : String\n/* Foo in comment */\nvar bar : Foo";
        List<org.eclipse.lsp4j.Range> ranges = extractor.findOccurrences(src, "Foo");
        assertEquals(2, ranges.size()); // line 0 and line 2 only
        assertTrue(ranges.stream().noneMatch(r -> r.getStart().getLine() == 1));
    }

    // ── stripComments ─────────────────────────────────────────────────────

    @Test
    void extract_lineCommentedSymbol_notExtracted() {
        String src = "// function commentedOut() {}\nfunction realFunc() {}";
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract(src);
        assertTrue(symbols.stream().noneMatch(s -> s.name.equals("commentedOut")));
        assertTrue(symbols.stream().anyMatch(s -> s.name.equals("realFunc")));
    }

    @Test
    void extract_blockCommentedSymbol_notExtracted() {
        String src = "/* class Hidden {} */\nclass Visible {}";
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract(src);
        assertTrue(symbols.stream().noneMatch(s -> s.name.equals("Hidden")));
        assertTrue(symbols.stream().anyMatch(s -> s.name.equals("Visible")));
    }

    @Test
    void extract_multiLineBlockCommentedSymbol_notExtracted() {
        String src = "/*\n * var ignored : String\n */\nvar real : Integer = 0";
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract(src);
        assertTrue(symbols.stream().noneMatch(s -> s.name.equals("ignored")));
        assertTrue(symbols.stream().anyMatch(s -> s.name.equals("real")));
    }

    @Test
    void stripComments_lineComment_replacedWithSpaces() {
        String result = GosuSymbolExtractor.stripComments("var x = 1 // inline\nvar y = 2");
        assertFalse(result.contains("//"));
        assertTrue(result.contains("var y"));
    }

    @Test
    void stripComments_blockComment_preservesNewlines() {
        String src = "/* line1\nline2 */\nvar z = 3";
        String result = GosuSymbolExtractor.stripComments(src);
        assertEquals(src.length(), result.length());
        // newline inside block comment must be preserved
        long newlinesBefore = src.chars().filter(c -> c == '\n').count();
        long newlinesAfter  = result.chars().filter(c -> c == '\n').count();
        assertEquals(newlinesBefore, newlinesAfter);
    }

    @Test
    void extract_symbolAfterBlockComment_hasCorrectLine() {
        String src = "/*\n * block comment\n */\nclass After {}";
        List<GosuSymbolExtractor.Symbol> symbols = extractor.extract(src);
        GosuSymbolExtractor.Symbol sym = symbols.stream()
                .filter(s -> s.name.equals("After")).findFirst().orElse(null);
        assertNotNull(sym);
        assertEquals(3, sym.line);
    }
}

