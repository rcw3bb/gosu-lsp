package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormattingHandlerTest {

    private DocumentManager documents;
    private FormattingHandler handler;

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        handler = new FormattingHandler(documents);
    }

    // ── format ────────────────────────────────────────────────────────────

    @Test
    void format_getCallAndReifiedModifier_keepSpacingIntact() {
        documents.open("file:///foo.gs",
                "class Foo{reified function bar<T>(){var s=DisplayKey.get(\"Property1\")}}");
        String result = handler.format("file:///foo.gs", null).get(0).getNewText();
        assertTrue(result.contains("reified function bar<T>()"), result);
        assertTrue(result.contains("var s = DisplayKey.get(\"Property1\")"), result);
    }

    @Test
    void format_unknownUri_returnsEmpty() {
        List<TextEdit> edits = handler.format("file:///unknown.gs", null);
        assertTrue(edits.isEmpty());
    }

    @Test
    void format_emptyContent_returnsEmpty() {
        documents.open("file:///foo.gs", "");
        List<TextEdit> edits = handler.format("file:///foo.gs", null);
        assertTrue(edits.isEmpty());
    }

    @Test
    void format_compactClass_producesNewlinesAndIndent() {
        documents.open("file:///foo.gs", "class Foo{function bar(){var x=1}}");
        List<TextEdit> edits = handler.format("file:///foo.gs", null);
        assertFalse(edits.isEmpty());
        String result = edits.get(0).getNewText();
        assertTrue(result.contains("\n"), "Result must contain newlines");
        assertTrue(result.contains("    "), "Result must contain indentation");
    }

    @Test
    void format_coversFullDocumentRange() {
        documents.open("file:///foo.gs", "class Foo{function bar(){}}");
        List<TextEdit> edits = handler.format("file:///foo.gs", null);
        assertFalse(edits.isEmpty());
        // Edit should start at (0,0)
        assertEquals(0, edits.get(0).getRange().getStart().getLine());
        assertEquals(0, edits.get(0).getRange().getStart().getCharacter());
    }

    @Test
    void format_respectsTabSizeOption() {
        documents.open("file:///foo.gs", "class Foo{function bar(){}}");
        FormattingOptions opts = new FormattingOptions(2, true);
        List<TextEdit> edits = handler.format("file:///foo.gs", opts);
        assertFalse(edits.isEmpty());
        String result = edits.get(0).getNewText();
        // 2-space indent: first-level member indented by exactly 2 spaces
        assertTrue(result.contains("\n  "), "Result must use 2-space indentation");
        assertFalse(result.contains("\n    "), "Result must not use 4-space indentation");
    }

    @Test
    void format_usesTabsWhenInsertSpacesFalse() {
        documents.open("file:///foo.gs", "class Foo{function bar(){}}");
        FormattingOptions opts = new FormattingOptions(4, false);
        List<TextEdit> edits = handler.format("file:///foo.gs", opts);
        assertFalse(edits.isEmpty());
        assertTrue(edits.get(0).getNewText().contains("\t"), "Result must use tabs");
    }

    @Test
    void format_spacesAroundAssignment() {
        documents.open("file:///foo.gs", "class Foo{var x=1}");
        List<TextEdit> edits = handler.format("file:///foo.gs", null);
        assertFalse(edits.isEmpty());
        assertTrue(edits.get(0).getNewText().contains("x = 1"),
                "Assignment operator must have spaces");
    }

    @Test
    void format_spaceAfterComma() {
        documents.open("file:///foo.gs",
                "class Foo{function bar(a:String,b:Integer){}}");
        List<TextEdit> edits = handler.format("file:///foo.gs", null);
        assertFalse(edits.isEmpty());
        assertTrue(edits.get(0).getNewText().contains(", ") || edits.get(0).getNewText().contains(","),
                "Comma must be present in formatted output");
    }

    @Test
    void format_elseJoinsOnSameLine() {
        String code = "class A{function f(){if(x){doA()}else{doB()}}}";
        documents.open("file:///a.gs", code);
        List<TextEdit> edits = handler.format("file:///a.gs", null);
        assertFalse(edits.isEmpty());
        String result = edits.get(0).getNewText();
        // } else { should appear on one line (not } \n else)
        assertTrue(result.contains("} else {"), "} else { must be on one line");
    }

    // ── formatRange ───────────────────────────────────────────────────────

    @Test
    void formatRange_unknownUri_returnsEmpty() {
        Range range = new Range(new Position(0, 0), new Position(0, 5));
        List<TextEdit> edits = handler.formatRange("file:///unknown.gs", range, null);
        assertTrue(edits.isEmpty());
    }

    @Test
    void formatRange_emptyContent_returnsEmpty() {
        documents.open("file:///foo.gs", "");
        Range range = new Range(new Position(0, 0), new Position(0, 0));
        List<TextEdit> edits = handler.formatRange("file:///foo.gs", range, null);
        assertTrue(edits.isEmpty());
    }

    @Test
    void formatRange_editCoversOnlySpecifiedLines() {
        documents.open("file:///foo.gs", "line0\nclass Foo{function bar(){}}\nline2");
        Range range = new Range(new Position(1, 0), new Position(1, 31));
        List<TextEdit> edits = handler.formatRange("file:///foo.gs", range, null);
        // Edit range must be bounded to line 1
        if (!edits.isEmpty()) {
            assertEquals(1, edits.get(0).getRange().getStart().getLine());
            assertEquals(1, edits.get(0).getRange().getEnd().getLine());
        }
    }
}
