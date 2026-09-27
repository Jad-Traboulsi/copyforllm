import * as vscode from 'vscode';

const SECTION = 'copyForLlm';
const EXCLUDED_PATTERNS = 'excludedPatterns';

export interface Settings {
    /**
     * Filename/path patterns matched against files and folders (e.g. ".env", "*.pem",
     * "node_modules", "secrets"). A match - whether a single file or a whole folder -
     * always stays visible in the tree; in the content section it's noted as skipped,
     * but its actual content is never included.
     */
    excludedPatterns: string[];
    /**
     * Whether binary files are left out of the copied file tree. Their entry in the
     * content section is unaffected - that still names them and notes the skip.
     */
    hideBinaryFilesInTree: boolean;
}

export function readSettings(): Settings {
    const config = vscode.workspace.getConfiguration(SECTION);
    return {
        excludedPatterns: config.get<string[]>(EXCLUDED_PATTERNS, [])
            .map(pattern => pattern.trim())
            .filter(pattern => pattern.length > 0),
        hideBinaryFilesInTree: config.get<boolean>('hideBinaryFilesInTree', true),
    };
}

/** Appends the patterns that aren't configured yet, keeping the existing order. */
export async function addExcludedPatterns(patterns: readonly string[]): Promise<void> {
    const updated = readSettings().excludedPatterns;
    patterns.map(pattern => pattern.trim())
        .filter(pattern => pattern.length > 0 && !updated.includes(pattern))
        .forEach(pattern => updated.push(pattern));
    await writeExcludedPatterns(updated);
}

export async function removeExcludedPatterns(patterns: readonly string[]): Promise<void> {
    const removed = new Set(patterns.map(pattern => pattern.trim()));
    await writeExcludedPatterns(readSettings().excludedPatterns.filter(pattern => !removed.has(pattern)));
}

/**
 * Writes to the workspace settings when the list is already overridden there, so the
 * change takes effect; otherwise to the user settings, shared by every workspace.
 */
async function writeExcludedPatterns(patterns: string[]): Promise<void> {
    const config = vscode.workspace.getConfiguration(SECTION);
    const target = config.inspect(EXCLUDED_PATTERNS)?.workspaceValue !== undefined
        ? vscode.ConfigurationTarget.Workspace
        : vscode.ConfigurationTarget.Global;
    await config.update(EXCLUDED_PATTERNS, patterns, target);
}
