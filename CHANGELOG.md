# Changelog

## 0.5.0 - 2026-10-30

### Added
- **`reified` modifier** — `reified function foo<T>()` now parses without diagnostics; the keyword is also offered in completions, spaced by the formatter, and highlighted by the TextMate grammar
- **Virtual threads** — all `textDocument/*` request handlers and the LSP4J launcher now run on virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`)

### Changed
- Server now requires **Java 21 or higher** (was 17)
- Build upgraded to **Gradle 9.8.2** (builds on JDK 21 and 25); Shadow plugin moved to `com.gradleup.shadow` 9.6.1, PMD to 7.29.0 (new `OnDemandImport`, `UnnecessaryWarningSuppression` and `AvoidCatchingGenericException` rules excluded for now), and `junit-platform-launcher` added to the test runtime

### Fixed
- **`annotation` declarations** — `annotation Foo { }` (with `@Target({METHOD})` / `@Retention(RUNTIME)` annotations above it) is no longer reported as a syntax error; `annotation` is recognised contextually so `java.lang.annotation.*` imports and identifiers named `annotation` keep working
- **`get`/`set` method calls** — `DisplayKey.get("Property1")`, `x?.get(0)`, `list.set(0, v)` and `function get()` no longer produce syntax errors; `get`/`set` are lexed as property accessor keywords only when not followed by `(`

## 0.4.1 - 2026-08-09

### Fixed
- **Comment content excluded from symbols** — `GosuSymbolExtractor.extract()` now strips `//` line comments and `/* */` block comments (replacing their text with spaces to preserve character offsets) before running regex patterns, so declarations inside comments are no longer reported as symbols
- **Comment content excluded from references** — `GosuSymbolExtractor.findOccurrences()` applies the same comment stripping before searching for word occurrences, so references inside comments are no longer returned

## 0.4.0 - 2026-08-09

### Changed
- **Outline performance** — `GosuSymbolExtractor.extract()` now pre-builds a line-offset table (single O(n) scan + binary search) instead of calling the O(n) `lineOf()`/`lineStart()` helpers per regex match; `findOccurrences()` receives the same treatment
- **Outline caching** — `GosuTextDocumentService` caches `documentSymbol` results per URI; repeated requests on an unchanged document return the cached list immediately without re-running any regex; cache is invalidated on `didOpen`, `didChange`, and `didClose`

## 0.3.5 - 2026-08-08

### Added
- **Annotation go-to-definition** — CTRL+Click on an annotation usage (e.g. `@CalculatedFieldInfuser`) now navigates to its `annotation Foo { }` declaration; the server scans the workspace file tree if the declaration file is not already open
- `ANNOTATION` added to `GosuSymbolExtractor.SymbolKind`; annotation declarations extracted via a line-anchored regex that avoids false matches inside package/uses paths (e.g. `java.lang.annotation.Target`)
- `GosuSymbolExtractor.annotationSymbol()` computes the correct `startChar` pointing to the annotation name rather than the `annotation` keyword
- `annotation` case added to `HoverHandler.buildHoverMarkdown()` for correct hover display
- `DefinitionHandler` falls back to a heuristic project-root search (walks up from the current file looking for `.git`, `build.gradle`, `settings.gradle`, `pom.xml`) when no workspace root is configured by the client
- `DocumentManager.setWorkspaceRoot()` / `getWorkspaceRoot()` for workspace-root propagation
- `GosuLanguageServer.initialize()` captures workspace root from `InitializeParams.getWorkspaceFolders()`
- `DefinitionHandler` logger (FINE level) for diagnosing workspace scan path and resolved word

### Fixed
- Annotation symbols were absent from the document outline because the `annotation` keyword inside `uses`/`package` paths (e.g. `java.lang.annotation.Retention\nuses`) was being matched as a false symbol, polluting the extractor output

## 0.3.4 - 2026-08-08

### Fixed
- Extension failed to activate when installed from the marketplace because `vscode-languageclient` was not bundled and `node_modules` is excluded from the VSIX; switched `vscode:prepublish` to use `esbuild` so all runtime dependencies are inlined into `out/extension.js`

## 0.3.3 - 2026-08-08

### Fixed
- Server JAR was missing from the marketplace extension because `client/server/` is listed in `.gitignore`; added `!server` to `client/.vscodeignore` so `vsce` includes it regardless

## 0.3.2 - 2026-08-08

### Added
- Visible logging: `logging.properties` bundled in the server JAR configures JUL with a compact single-line format directed to stderr; `GosuLanguageServerLauncher` loads it at startup
- `RevealOutputChannelOn.Info` added to the VS Code client so the "Gosu Language Server" output panel opens automatically on the first log message
- JAR manifest now includes `Implementation-Title` and `Implementation-Version` attributes

## 0.3.1 - 2026-08-03

### Fixed
- Corrected `publisher` field in `client/package.json` from `gosu-lsp` to `ronella` to match VS Marketplace publisher ID
- Updated extension `displayName` to `Gosu Programming Language Support` to satisfy VS Marketplace uniqueness requirements
- `build-vsix.ps1` now copies the root `README.md` into `client/` before packaging so the published extension shows its README on the Marketplace

## 0.3.0 - 2026-08-02

### Added
- **Format Document** (`textDocument/formatting`) — reformats the entire file with consistent indentation (default 4 spaces; tab size and insert-spaces flags honoured from client `FormattingOptions`) and spacing rules via ANTLR4 token-stream pass
- **Range Formatting** (`textDocument/rangeFormatting`) — formats only the selected line range
- **Code Actions** (`textDocument/codeAction`) — produces `QuickFix` actions for ANTLR4 diagnostic messages: inserts missing `;` and removes extraneous/mismatched tokens
- **Inlay Hints** (`textDocument/inlayHint`) — type hints after `var` declarations whose type is known (`var x: String`) and parameter-name hints before call-site arguments for functions declared in the same file
- New handlers: `FormattingHandler`, `CodeActionHandler`, `InlayHintHandler`
- 30 new unit tests covering all Phase 3 handlers

## 0.2.0 - 2026-08-02

### Added
- **Find All References** (`textDocument/references`) — locates every occurrence of the symbol under the cursor across all open documents, with optional declaration inclusion/exclusion
- **Rename Symbol** (`textDocument/rename` + `textDocument/prepareRename`) — renames a symbol in all open files; returns the identifier range on `prepareRename`
- **Type-aware dot completions** — when typing `receiver.`, the server resolves the declared type of the receiver variable and returns its members via Java reflection (JDK types) or the Gosu runtime type system (`gosu-core`)
- `GosuSymbolExtractor.findOccurrences()` — word-boundary regex occurrence search used by References and Rename
- `GosuTypeProvider` — two-tier type member resolver (Java reflection first, Gosu runtime fallback); degrades gracefully if Gosu runtime cannot initialize
- New handlers: `ReferencesHandler`, `RenameHandler`
- `gosu-core:1.18.5` runtime dependency for Gosu-specific type introspection
- 28 new unit tests covering all new features; total test count: 162

## 0.1.0 - 2026-07-26

### Added
- ANTLR4 grammar (`GosuLanguage.g4`) covering the full Gosu language syntax including classes, interfaces, enums, enhancements, expressions, statements, and generics
- LSP server implemented in Java using Eclipse LSP4J over stdio transport
- Real-time syntax diagnostics — parse errors reported as you type via `textDocument/publishDiagnostics`
- Code completion for Gosu keywords, built-in types, and symbols declared in the current file
- Hover support showing the declaration signature of the symbol under the cursor
- Go-to-definition across all open documents
- Document outline (symbols) listing classes, functions, properties, and variables
- In-memory document store with incremental text sync
- VS Code extension (TypeScript) that automatically launches the server on `.gs` / `.gsx` files
- TextMate syntax grammar for `.gs` and `.gsx` files (keywords, types, strings, comments, annotations, operators)
- Gradle shadow JAR build producing `gosu-lsp-server-<version>.jar`
- `copyServerJar` Gradle task that copies the server JAR and LICENSE into `client/` for VSIX packaging
- `.vscode/launch.json` and `.vscode/tasks.json` for Extension Development Host debugging
- MIT License
