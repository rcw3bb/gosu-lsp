import * as path from 'path';
import * as fs from 'fs';
import {
    ExtensionContext,
    window,
    workspace,
} from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    Executable,
    TransportKind,
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;

export async function activate(context: ExtensionContext): Promise<void> {
    const jarPath = resolveServerJar(context);
    if (!jarPath) {
        void window.showErrorMessage(
            'Gosu LSP: Could not locate gosu-lsp-server.jar. ' +
            'Run `./gradlew :server:shadowJar` to build it.'
        );
        return;
    }

    const serverExecutable: Executable = {
        command: 'java',
        args: ['-jar', jarPath],
        transport: TransportKind.stdio,
    };

    const serverOptions: ServerOptions = {
        run:   serverExecutable,
        debug: serverExecutable,   // same command for debug; add -agentlib: args here if needed
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [
            { scheme: 'file', language: 'gosu' },
        ],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/*.{gs,gsx}'),
        },
        outputChannelName: 'Gosu Language Server',
    };

    client = new LanguageClient(
        'gosu-lsp',
        'Gosu Language Server',
        serverOptions,
        clientOptions
    );

    await client.start();
    context.subscriptions.push(client);
}

export async function deactivate(): Promise<void> {
    if (client) {
        await client.stop();
        client = undefined;
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Resolves the server JAR location using two candidate paths:
 *
 *   1. `<extensionPath>/server/gosu-lsp-server.jar`
 *      (produced by `./gradlew :server:copyServerJar` / used when installed as a VSIX)
 *
 *   2. `<extensionPath>/../server/build/libs/gosu-lsp-server.jar`
 *      (produced by `./gradlew :server:shadowJar` during development with the
 *       extension launched directly from the workspace)
 *
 * Returns the first path that exists, or `undefined` if neither is found.
 */
function resolveServerJar(context: ExtensionContext): string | undefined {
    const candidates = [
        path.join(context.extensionPath, 'server', 'gosu-lsp-server.jar'),
        path.resolve(context.extensionPath, '..', 'server', 'build', 'libs', 'gosu-lsp-server.jar'),
    ];
    for (const candidate of candidates) {
        if (fs.existsSync(candidate)) {
            return candidate;
        }
    }
    return undefined;
}
