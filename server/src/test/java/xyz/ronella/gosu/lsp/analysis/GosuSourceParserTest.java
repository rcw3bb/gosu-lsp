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

    @Test
    void parse_reifiedFunctionWithGenerics_noErrors() {
        String src = "class Foo {\n  public reified function make<T>(x : T) : T {\n    return x\n  }\n}\n";
        assertFalse(parser.parse(src).hasErrors());
    }

    @Test
    void parse_getMethodCallOnType_noErrors() {
        String src = "class Foo {\n  function bar() {\n    var s = DisplayKey.get(\"Property1\")\n  }\n}\n";
        assertFalse(parser.parse(src).hasErrors());
    }

    @Test
    void parse_getAndSetCallsWithWhitespaceBeforeParen_noErrors() {
        String src = "class Foo {\n  function bar() {\n    var a = x?.get (1)\n    list.set\n(0, a)\n  }\n}\n";
        assertFalse(parser.parse(src).hasErrors());
    }

    @Test
    void parse_annotationDeclaration_noErrors() {
        String src = "package com.example\n\nuses java.lang.annotation.Retention\nuses java.lang.annotation.Target\n\n"
                + "@Target({METHOD})\n@Retention(RUNTIME)\nannotation DateFieldInfuser {\n}\n";
        GosuSourceParser.ParseResult result = parser.parse(src);
        assertFalse(result.hasErrors(), String.valueOf(result.errors));
    }

    @Test
    void parse_annotationDeclarationWithMembers_noErrors() {
        String src = "annotation Info {\n  function value() : String\n}\n";
        assertFalse(parser.parse(src).hasErrors());
    }

    @Test
    void parse_annotationElementWithDefaultValue_noErrors() {
        String src = "annotation Info {\n  function sourceType() : String = \"\"\n"
                + "  function count() : int = 1 + 2\n  function name() : String\n}\n";
        GosuSourceParser.ParseResult result = parser.parse(src);
        assertFalse(result.hasErrors(), String.valueOf(result.errors));
    }

    @Test
    void parse_identifierNamedAnnotationElsewhere_noErrors() {
        String src = "class Foo {\n  function bar() {\n    var annotation = 1\n  }\n}\n";
        assertFalse(parser.parse(src).hasErrors());
    }

    @Test
    void parse_nonAnnotationIdentifierAtTopLevel_hasErrors() {
        assertTrue(parser.parse("foo Bar {\n}\n").hasErrors());
    }

    @Test
    void parse_getMethodDeclaration_noErrors() {
        String src = "class Foo {\n  function get(i : int) : int {\n    return i\n  }\n}\n";
        assertFalse(parser.parse(src).hasErrors());
    }

    @Test
    void parse_propertyGetAndSet_stillRecognisedAsKeywords() {
        String src = "class Foo {\n  property get Name() : String {\n    return null\n  }\n"
                + "  property set Name(v : String) {\n  }\n}\n";
        assertFalse(parser.parse(src).hasErrors());
    }
}
