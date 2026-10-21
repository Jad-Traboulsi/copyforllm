import * as vscode from 'vscode';
import { ExclusionMatcher } from './exclusionMatcher';
import { nameOf, relativePathOf } from './paths';

/**
 * Asks for the list of extensions and/or file names to copy. VS Code has no multi-line
 * input box, so this is a quick pick: typing an entry and pressing Enter adds it to the
 * list, checked; pressing Enter with nothing typed accepts the checked entries.
 * Resolves to undefined when dismissed.
 */
export function promptForEntries(lastEntries: readonly string[]): Promise<string[] | undefined> {
    return new Promise(resolve => {
        const quickPick = vscode.window.createQuickPick();
        quickPick.title = 'Copy Matching Files for LLM+';
        quickPick.placeholder = 'Type an extension (.xml or xml) or a file name (pom.xml) and press Enter to add it. '
            + 'Press Enter with nothing typed to copy.';
        quickPick.canSelectMany = true;
        quickPick.items = lastEntries.map(label => ({ label }));
        quickPick.selectedItems = quickPick.items;

        let result: string[] | undefined;
        quickPick.onDidAccept(() => {
            const typed = quickPick.value.trim();
            if (typed.length === 0) {
                result = quickPick.selectedItems.map(item => item.label);
                quickPick.hide();
                return;
            }
            const selected = quickPick.selectedItems;
            const existing = quickPick.items.find(item => item.label.toLowerCase() === typed.toLowerCase());
            const item = existing ?? { label: typed };
            if (!existing) quickPick.items = [...quickPick.items, item];
            quickPick.selectedItems = selected.includes(item) ? selected : [...selected, item];
            quickPick.value = '';
        });
        quickPick.onDidHide(() => {
            quickPick.dispose();
            resolve(result);
        });
        quickPick.show();
    });
}

/**
 * Files under the selection whose name matches one of [terms] (lowercase), in no
 * particular order (the builder sorts). A selected file counts if it matches itself.
 * Folders matching an exclusion pattern and symlinked folders are not searched.
 */
export async function collectMatches(
    selection: readonly vscode.Uri[],
    terms: readonly string[],
    matcher: ExclusionMatcher,
    token: vscode.CancellationToken,
): Promise<vscode.Uri[]> {
    const matches = new Map<string, vscode.Uri>();

    const visit = async (uri: vscode.Uri, type: vscode.FileType): Promise<void> => {
        if (token.isCancellationRequested) throw new vscode.CancellationError();
        const name = nameOf(uri);
        if ((type & vscode.FileType.Directory) === 0) {
            if (isMatch(name.toLowerCase(), terms)) matches.set(uri.toString(), uri);
            return;
        }
        const folder = vscode.workspace.getWorkspaceFolder(uri);
        const relativePath = folder ? relativePathOf(uri, folder) : name;
        if ((type & vscode.FileType.SymbolicLink) !== 0 || matcher.isExcluded(name, relativePath)) return;
        try {
            for (const [childName, childType] of await vscode.workspace.fs.readDirectory(uri)) {
                await visit(vscode.Uri.joinPath(uri, childName), childType);
            }
        } catch (error) {
            if (error instanceof vscode.CancellationError) throw error;
            console.warn(`CopyForLlm+: could not read children of ${uri.toString()}`, error);
        }
    };

    for (const uri of selection) {
        await visit(uri, (await vscode.workspace.fs.stat(uri)).type);
    }
    return [...matches.values()];
}

/**
 * ".xml" matches names ending in it; "xml" means the same and also the exact name
 * (so "Dockerfile" works); anything with a dot inside, like "pom.xml", is an exact name.
 * Both [name] and [terms] are lowercase.
 */
function isMatch(name: string, terms: readonly string[]): boolean {
    return terms.some(term => {
        if (term.startsWith('.')) return name.endsWith(term);
        if (!term.includes('.')) return name.endsWith(`.${term}`) || name === term;
        return name === term;
    });
}
