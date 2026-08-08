package xyz.ronella.gosu.lsp.handlers;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.ronella.gosu.lsp.document.DocumentManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionHandlerTest {

    private DocumentManager documents;
    private DefinitionHandler handler;

    @BeforeEach
    void setUp() {
        documents = new DocumentManager();
        handler = new DefinitionHandler(documents);
    }

    @Test
    void definition_unknownUri_returnsEmpty() {
        List<Location> result = handler.definition("file:///unknown.gs", new Position(0, 0));
        assertTrue(result.isEmpty());
    }

    @Test
    void definition_emptyDocument_returnsEmpty() {
        documents.open("file:///foo.gs", "");
        List<Location> result = handler.definition("file:///foo.gs", new Position(0, 0));
        assertTrue(result.isEmpty());
    }

    @Test
    void definition_symbolInSameDoc_returnsLocation() {
        String src = "class MyService {}\nvar s : MyService = null";
        documents.open("file:///foo.gs", src);
        // Hover over "MyService" usage on line 1 (char 8 = inside 'MyService')
        List<Location> result = handler.definition("file:///foo.gs", new Position(1, 10));
        assertFalse(result.isEmpty(), "Should find definition of MyService in the same file");
        assertEquals("file:///foo.gs", result.get(0).getUri());
    }

    @Test
    void definition_symbolNotDeclared_returnsEmpty() {
        documents.open("file:///foo.gs", "var x = undeclaredSymbol");
        List<Location> result = handler.definition("file:///foo.gs", new Position(0, 16));
        assertTrue(result.isEmpty());
    }

    @Test
    void definition_crossFile_findsSymbolInOtherDoc() {
        documents.open("file:///a.gs", "class SharedClass {}");
        documents.open("file:///b.gs", "var obj : SharedClass = null");
        // hover over "SharedClass" usage in b.gs
        List<Location> result = handler.definition("file:///b.gs", new Position(0, 12));
        assertFalse(result.isEmpty(), "Should find SharedClass defined in a.gs");
        assertTrue(result.stream().anyMatch(l -> "file:///a.gs".equals(l.getUri())));
    }

    @Test
    void definition_locationHasCorrectRange() {
        documents.open("file:///foo.gs", "class FooBar {}\nvar f : FooBar");
        List<Location> result = handler.definition("file:///foo.gs", new Position(1, 9));
        assertFalse(result.isEmpty());
        assertNotNull(result.get(0).getRange());
        assertEquals(0, result.get(0).getRange().getStart().getLine());
    }

    @Test
    void definition_functionSymbol_foundInSameDoc() {
        String src = "function compute() : Integer {}\nvar x = compute()";
        documents.open("file:///foo.gs", src);
        List<Location> result = handler.definition("file:///foo.gs", new Position(1, 10));
        assertFalse(result.isEmpty());
        assertEquals("file:///foo.gs", result.get(0).getUri());
    }

    @Test
    void definition_annotationInWorkspaceFile_foundOnDisk(@TempDir Path tmp) throws IOException {
        Path annotFile = tmp.resolve("StringFieldInfuser.gs");
        Files.writeString(annotFile, "annotation StringFieldInfuser {\n}", StandardCharsets.UTF_8);

        documents.open("file:///current.gs", "@StringFieldInfuser\nclass Foo {}");
        documents.setWorkspaceRoot(tmp.toUri().toString());

        // cursor on "StringFieldInfuser" (line 0, char 5 — inside the name after '@')
        List<Location> result = handler.definition("file:///current.gs", new Position(0, 5));
        assertFalse(result.isEmpty(), "Should find annotation declared in workspace file");
        assertTrue(result.stream().anyMatch(l -> l.getUri().contains("StringFieldInfuser.gs")));
    }

    @Test
    void definition_annotationInWorkspaceFile_foundWithoutExplicitRoot(@TempDir Path tmp) throws IOException {
        // Simulate a Gradle project: tmp acts as the project root with build.gradle
        Files.writeString(tmp.resolve("build.gradle"), "// gradle", StandardCharsets.UTF_8);
        Path annotFile = tmp.resolve("CalculatedFieldInfuser.gs");
        Files.writeString(annotFile, "annotation CalculatedFieldInfuser {\n}", StandardCharsets.UTF_8);

        // Current file lives inside the same project tree; no workspaceRoot set
        Path currentFile = tmp.resolve("MyEnhancement.gs");
        Files.writeString(currentFile, "typeis Optional<CalculatedFieldInfuser>", StandardCharsets.UTF_8);
        String currentUri = currentFile.toUri().toString();
        documents.open(currentUri, "typeis Optional<CalculatedFieldInfuser>");

        // cursor on "CalculatedFieldInfuser" — 'C' starts at char 16 (after "typeis Optional<")
        List<Location> result = handler.definition(currentUri, new Position(0, 20));
        assertFalse(result.isEmpty(), "Should find annotation via heuristic project-root detection");
        assertTrue(result.stream().anyMatch(l -> l.getUri().contains("CalculatedFieldInfuser.gs")));
    }
}
