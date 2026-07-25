# Changelog

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
