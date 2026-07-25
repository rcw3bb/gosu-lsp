# Gosu LSP

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](CHANGELOG.md)

A Language Server Protocol (LSP) implementation for the [Gosu programming language](https://gosu-lang.github.io/), providing Java-like IDE features for `.gs` and `.gsx` files in VS Code.

## Features

- **Syntax highlighting** — TextMate grammar covering keywords, types, strings, comments, annotations, and operators
- **Diagnostics** — real-time syntax errors via an ANTLR4 parser
- **Code completion** — Gosu keywords, built-in types, and symbols declared in the current file
- **Hover** — shows the declaration signature of the symbol under the cursor
- **Go-to-definition** — jumps to the declaration of any symbol, including across open files
- **Document outline** — lists all classes, functions, properties, and variables in the file

## Requirements

| Tool | Version |
|---|---|
| Java | 17 or higher |
| Gradle (wrapper) | 8.8 |
| Node.js | 18 or higher |
| VS Code | 1.85 or higher |

## Project Structure

```
gosu-lsp/
├── server/                          # Java Gradle subproject (LSP server)
│   ├── build.gradle
│   └── src/main/
│       ├── antlr/com/gosu/lsp/
│       │   └── GosuLanguage.g4      # ANTLR4 grammar for Gosu
│       └── java/com/gosu/lsp/
│           ├── GosuLanguageServerLauncher.java
│           ├── GosuLanguageServer.java
│           ├── GosuTextDocumentService.java
│           ├── GosuWorkspaceService.java
│           ├── analysis/
│           │   ├── GosuSourceParser.java
│           │   ├── DiagnosticsPublisher.java
│           │   └── GosuSymbolExtractor.java
│           ├── document/
│           │   └── DocumentManager.java
│           └── handlers/
│               ├── CompletionHandler.java
│               ├── HoverHandler.java
│               └── DefinitionHandler.java
├── client/                          # TypeScript VS Code extension
│   ├── package.json
│   ├── tsconfig.json
│   ├── language-configuration.json
│   ├── syntaxes/
│   │   └── gosu.tmLanguage.json
│   └── src/
│       └── extension.ts
├── build.gradle                     # Root build — shared repo config
├── settings.gradle
└── gradlew.bat
```

## Building

### 1 — Build the server JAR

```bat
.\gradlew.bat :server:shadowJar
```

The fat JAR is produced at `server/build/libs/gosu-lsp-server.jar`.

To also copy it into `client/server/` for packaging:

```bat
.\gradlew.bat :server:copyServerJar
```

### 2 — Build the VS Code extension

```bat
cd client
npm install
npm run compile
```

Compiled JavaScript is written to `client/out/` (configured in `client/tsconfig.json`).

## Installing the Extension

### Option A — Install directly from the compiled output (quickest)

Point VS Code at the `client/` folder without packaging:

```bat
code --install-extension client
```

Or in VS Code: **Extensions** → `...` menu → **Install from VSIX…** → select `client/`.

### Option B — Package as a VSIX then install

1. Install the VS Code Extension Manager globally (once):
   ```bat
   npm install -g @vscode/vsce
   ```
2. Package:
   ```bat
   cd client
   vsce package
   ```
   This produces `client/gosu-lsp-<version>.vsix`.
3. Install:
   ```bat
   code --install-extension gosu-lsp-<version>.vsix
   ```
   Or in VS Code: **Extensions** → `...` → **Install from VSIX…** → select the `.vsix` file.

> **Note:** The server JAR must be built before packaging (`.\gradlew.bat :server:copyServerJar` copies it to `client/server/` automatically).

## Running in VS Code (development)

1. Build both the server JAR and the client (steps above).
2. Open `client/` as a VS Code workspace (or the repo root).
3. Press **F5** to launch the Extension Development Host.
4. Open any `.gs` or `.gsx` file — the Gosu Language Server starts automatically.

## Architecture

```
VS Code (TypeScript extension)
        │  stdin / stdout (JSON-RPC over stdio)
        ▼
GosuLanguageServerLauncher (java -jar gosu-lsp-server.jar)
        │
        ▼
GosuLanguageServer  ──── GosuTextDocumentService
        │                        │
        │                DocumentManager (in-memory store)
        │                DiagnosticsPublisher → ANTLR4 parse
        │                CompletionHandler
        │                HoverHandler
        │                DefinitionHandler
        └───── GosuWorkspaceService
```

The server communicates over **stdio** using the [LSP 3.18 specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/).  
The grammar is implemented with **ANTLR4 4.13.2**, based on the [Gosu EBNF grammar](https://gosu-lang.github.io/grammar.html).

## Roadmap

| Phase | Scope |
|---|---|
| **1 (current)** | Syntax highlighting, diagnostics, completion, hover, go-to-definition |
| **2** | Type-aware completions via Gosu runtime, rename symbol, find all references |
| **3** | Format document, code actions, inlay hints |

## License

MIT — see [LICENSE](LICENSE) for details.
