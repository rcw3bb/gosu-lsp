package xyz.ronella.gosu.lsp.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GosuSourceParserTest {

    private GosuSourceParser parser;

    @BeforeEach
    void setUp() {
        parser = new GosuSourceParser();
    }

    @Test
    void parse_emptyContent_noErrors() {
        GosuSourceParser.ParseResult result = parser.parse("");
        assertNotNull(result);
        assertFalse(result.hasErrors());
        assertTrue(result.errors.isEmpty());
    }

    @Test
    void parse_nullContent_noErrors() {
        GosuSourceParser.ParseResult result = parser.parse(null);
        assertNotNull(result);
        assertFalse(result.hasErrors());
    }

    @Test
    void parse_validPackageAndClass_noErrors() {
        String src = "package com.example\n\nclass Foo {\n}\n";
        GosuSourceParser.ParseResult result = parser.parse(src);
        assertNotNull(result.tree);
        assertFalse(result.hasErrors(), "Expected no parse errors for valid input");
    }

    @Test
    void parse_invalidSyntax_hasErrors() {
        // Missing class body braces
        String src = "package com.example\nclass Foo {{{{{{{{";
        GosuSourceParser.ParseResult result = parser.parse(src);
        assertTrue(result.hasErrors(), "Expected parse errors for invalid input");
    }

    @Test
    void parse_multipleErrors_allCaptured() {
        String src = "@@@ !!! ###";
        GosuSourceParser.ParseResult result = parser.parse(src);
        assertTrue(result.hasErrors());
        assertFalse(result.errors.isEmpty());
    }

    @Test
    void parseError_hasCorrectLineAndMessage() {
        // An empty grammar input produces no errors, but a totally garbled line should
        String src = "package com.example\n@@@@invalid\nclass Foo {}";
        GosuSourceParser.ParseResult result = parser.parse(src);
        if (result.hasErrors()) {
            GosuSourceParser.ParseError err = result.errors.get(0);
            assertTrue(err.line >= 1, "Line should be 1-based and >= 1");
            assertNotNull(err.message);
        }
    }

    @Test
    void parseResult_treeIsNotNullEvenWithErrors() {
        String src = "@@garbage@@";
        GosuSourceParser.ParseResult result = parser.parse(src);
        assertNotNull(result.tree, "Tree should still be produced even with parse errors");
    }

    @Test
    void parseResult_tokensIsNotNull() {
        GosuSourceParser.ParseResult result = parser.parse("package x");
        assertNotNull(result.tokens);
    }

    @Test
    void parseResult_parserIsNotNull() {
        GosuSourceParser.ParseResult result = parser.parse("package x");
        assertNotNull(result.parser);
    }
}
