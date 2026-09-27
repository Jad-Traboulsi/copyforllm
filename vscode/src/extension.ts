import * as vscode from 'vscode';
import { BuilderResult, LlmContentBuilder, messageOf } from './contentBuilder';
import { ExclusionMatcher } from './exclusionMatcher';
import { nameOf, relativePathOf } from './paths';
import { addExcludedPatterns, readSettings, removeExcludedPatterns } from './settings';

/**
 * Absolute paths of the configured patterns in every workspace folder. The Explorer
 * menu reads it to offer "Include" instead of "Exclude" on an item excluded by its
 * own path - menu titles can't change at runtime, so these are two commands.
 */
const EXCLUDED_PATHS_CONTEXT = 'copyForLlm.excludedPaths';

export function activate(context: vscode.ExtensionContext): void {
    context.subscriptions.push(
        vscode.commands.registerCommand('copyForLlm.copy', copyForLlm),
        vscode.commands.registerCommand('copyForLlm.exclude', excludeSelection),
        vscode.commands.registerCommand('copyForLlm.include', includeSelection),
        vscode.workspace.onDidChangeConfiguration(event => {
            if (event.affectsConfiguration('copyForLlm.excludedPatterns')) updateExcludedPathsContext();
        }),
        vscode.workspace.onDidChangeWorkspaceFolders(updateExcludedPathsContext),
    );
    updateExcludedPathsContext();
}

/** Explorer commands get the clicked item plus the whole multi-selection, when there is one. */
function selectionOf(uri?: vscode.Uri, uris?: vscode.Uri[]): vscode.Uri[] {
    if (uris && uris.length > 0) return uris;
    return uri ? [uri] : [];
}

async function copyForLlm(uri?: vscode.Uri, uris?: vscode.Uri[]): Promise<void> {
    const byFolder = new Map<number, { folder: vscode.WorkspaceFolder; selected: vscode.Uri[] }>();
    for (const selected of selectionOf(uri, uris)) {
        const folder = vscode.workspace.getWorkspaceFolder(selected);
        if (!folder) continue;
        const group = byFolder.get(folder.index) ?? { folder, selected: [] };
        group.selected.push(selected);
        byFolder.set(folder.index, group);
    }
    if (byFolder.size === 0) {
        vscode.window.showWarningMessage('Could not determine the selected files/folders to copy.');
        return;
    }

    const settings = readSettings();
    try {
        const results = await vscode.window.withProgress(
            { location: vscode.ProgressLocation.Notification, title: 'Copying for LLM+', cancellable: true },
            async (progress, token) => {
                const results: BuilderResult[] = [];
                let reported = 0;
                for (const [index, { folder, selected }] of [...byFolder.values()].entries()) {
                    // Each folder gets an equal share of the progress bar.
                    const report = (fraction: number, message: string) => {
                        const target = ((index + fraction) / byFolder.size) * 100;
                        progress.report({ increment: target - reported, message });
                        reported = target;
                    };
                    results.push(await new LlmContentBuilder(folder, settings, report, token).buildContent(selected));
                }
                return results;
            },
        );

        const content = results.map(result => result.content).join('\n\n');
        const fileCount = results.reduce((sum, result) => sum + result.fileCount, 0);
        const skippedCount = results.reduce((sum, result) => sum + result.skippedCount, 0);
        await vscode.env.clipboard.writeText(content);
        vscode.window.showInformationMessage(
            `Copied ${fileCount} files (${skippedCount} skipped, ${content.length} characters) to clipboard.`,
        );
    } catch (error) {
        if (error instanceof vscode.CancellationError) return;
        console.error('Copy for LLM+ failed.', error);
        vscode.window.showErrorMessage(`Error copying content: ${messageOf(error)}`);
    }
}

/**
 * Adds the selection's workspace-relative paths to the exclusion patterns - the same
 * list as the copyForLlm.excludedPatterns setting, so both entry points stay in sync.
 */
async function excludeSelection(uri?: vscode.Uri, uris?: vscode.Uri[]): Promise<void> {
    const patterns = patternsFor(selectionOf(uri, uris));
    if (patterns.length === 0) return;

    // Some other pattern (a parent folder, a name pattern) may already cover the whole
    // selection - there its own path would change nothing.
    const matcher = new ExclusionMatcher(readSettings().excludedPatterns);
    if (patterns.every(pattern => matcher.isExcludedIncludingAncestors(pattern))) {
        vscode.window.showInformationMessage(
            `${describe(patterns)} already excluded from Copy for LLM+ by an existing pattern.`,
        );
        return;
    }

    await addExcludedPatterns(patterns);
    vscode.window.showInformationMessage(`${describe(patterns)} excluded from Copy for LLM+ content.`);
}

async function includeSelection(uri?: vscode.Uri, uris?: vscode.Uri[]): Promise<void> {
    const configured = readSettings().excludedPatterns;
    const patterns = patternsFor(selectionOf(uri, uris)).filter(pattern => configured.includes(pattern));
    if (patterns.length === 0) return;

    await removeExcludedPatterns(patterns);
    vscode.window.showInformationMessage(`${describe(patterns)} included in Copy for LLM+ again.`);
}

function describe(patterns: readonly string[]): string {
    return patterns.length === 1 ? `'${patterns[0]}'` : `${patterns.length} items`;
}

/**
 * The patterns standing for the selected items: their workspace-relative paths, which
 * ExclusionMatcher matches exactly. An item outside every workspace folder falls back
 * to its bare name, matching that name anywhere.
 */
function patternsFor(uris: readonly vscode.Uri[]): string[] {
    const patterns = uris.map(uri => {
        const folder = vscode.workspace.getWorkspaceFolder(uri);
        return (folder ? relativePathOf(uri, folder) : nameOf(uri)).trim();
    });
    // A workspace folder itself has no path to exclude by
    return [...new Set(patterns.filter(pattern => pattern.length > 0))];
}

function updateExcludedPathsContext(): void {
    const patterns = readSettings().excludedPatterns;
    const paths = (vscode.workspace.workspaceFolders ?? []).flatMap(folder =>
        patterns.map(pattern => vscode.Uri.joinPath(folder.uri, pattern).fsPath),
    );
    vscode.commands.executeCommand('setContext', EXCLUDED_PATHS_CONTEXT, paths);
}
