## Purpose

This repo is a Language Server Protocol (LSP) implementation for the [Gosu programming language](https://gosu-lang.github.io/), providing IDE features (diagnostics, completion, hover, go-to-definition, outline) for `.gs` and `.gsx` files in VS Code. It is split into a **Java server** (Eclipse LSP4J + ANTLR4, base package `xyz.ronella.gosu.lsp`) and a **TypeScript VS Code extension client**. The server communicates over stdio JSON-RPC.

- **Build server JAR:** `.\gradlew.bat :server:shadowJar`
- **Copy JAR + run tests + coverage:** `.\gradlew.bat :server:copyServerJar :server:test`
- **Build client extension:** `cd client && npm install && npm run compile`
- **Run tests only:** `.\gradlew.bat :server:test` (JaCoCo report auto-generated at `server/build/reports/jacoco/test/html/`)
- Author: Ron Webb

## Tree

- `server/` — Java Gradle subproject (LSP server, Java 17)
- `server/build.gradle` — server build: ANTLR4, Shadow JAR, JUnit 5, Mockito, JaCoCo
- `server/src/main/antlr/xyz/ronella/gosu/lsp/GosuLanguage.g4` — ANTLR4 grammar (generated classes excluded from JaCoCo)
- `server/src/main/java/xyz/ronella/gosu/lsp/` — root LSP classes (server, launcher, text/workspace services)
- `server/src/main/java/xyz/ronella/gosu/lsp/analysis/` — parser, diagnostics, symbol extractor, type provider
- `server/src/main/java/xyz/ronella/gosu/lsp/document/` — in-memory document store
- `server/src/main/java/xyz/ronella/gosu/lsp/handlers/` — completion, hover, definition, references, rename handlers
- `server/src/test/java/xyz/ronella/gosu/lsp/` — JUnit 5 unit tests mirroring main structure
- `client/` — TypeScript VS Code extension (activates on `.gs`/`.gsx`, launches server JAR)
- `client/src/extension.ts` — extension entry point
- `client/syntaxes/gosu.tmLanguage.json` — TextMate grammar for syntax highlighting
- `client/language-configuration.json` — VS Code language configuration
- `client/server/` — server JAR copied here at build time (not committed)
- `build.gradle` — root build (shared Maven repos)
- `settings.gradle` — Gradle project settings
- `CHANGELOG.md` — version history

## Rules

- Before adding a new LSP feature, read the existing handler in `server/src/main/java/xyz/ronella/gosu/lsp/handlers/` and follow its pattern
- When changing the grammar, run `.\gradlew.bat :server:generateGrammarSource` and verify no compile errors before touching Java code; ANTLR-generated files live under `server/build/generated-src/antlr/main/` and must not be hand-edited
- All new Java code must have unit tests in `server/src/test/java/` mirroring the main package structure; target ≥ 90% instruction coverage
- Never drop the JaCoCo `afterEvaluate` exclusions in `server/build.gradle` — ANTLR grammar classes and `GosuLanguageServerLauncher` must remain excluded
- Keep both `server/build.gradle` `version` and `client/package.json` `version` in sync when bumping versions; server uses `-SNAPSHOT` suffix
- Never modify `GosuLanguageServerLauncher.main()` without considering that it blocks on stdin and is intentionally excluded from coverage
- Follow SOLID — each class has a single responsibility; `GosuTextDocumentService` delegates to handlers rather than implementing logic directly
- Follow DRY — do not duplicate symbol extraction logic; `GosuSymbolExtractor` is the single source of truth
- Never push generated files under `server/build/` or `client/out/` to source control
- When creating or discovering new source files, update the Tree above

## Note-taking

- After each task, log any correction, preference, or pattern learned.
- Write to the matching section of this file under Rules; one dated line, plain language.
  e.g. `Always rebuild the shadow JAR before testing the client extension end-to-end (learned 2026-07-29)`
- 3+ related notes on the same topic → create a `docs/` context file, move notes there, update the Tree. Keep this file under 100 lines.
- The `gosu-core` dependency brings in Java 25 class files that cause JaCoCo 0.8.11 to log instrumentation warnings; these are harmless and do not fail the build (learned 2026-08-02)
- When replacing large Java files with replace_string_in_file, the old tail content may remain; use PowerShell Set-Content to rewrite the file cleanly instead (learned 2026-08-02)
