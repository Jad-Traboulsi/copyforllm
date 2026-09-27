import * as vscode from 'vscode';
import { ExclusionMatcher } from './exclusionMatcher';
import { commentPrefixFor, hasBinaryExtension, isBinaryFile, looksBinary } from './fileTypes';
import { nameOf, relativePathOf } from './paths';
import { Settings } from './settings';

/** Holds the result of the content building operation. */
export interface BuilderResult {
    content: string;
    fileCount: number;
    skippedCount: number;
}

/** Reports progress as a fraction of this builder's work (0..1) plus a detail message. */
export type ProgressReporter = (fraction: number, message: string) => void;

interface Entry {
    uri: vscode.Uri;
    name: string;
    isDirectory: boolean;
    /** Symlinked folders are shown but never descended into, so a link cycle can't loop forever. */
    isSymbolicLink: boolean;
}

const EXCLUDED_REASON = 'excluded by CopyForLlm+ settings';

/**
 * Builds a formatted string containing a file tree representation and the content
 * of selected files/directories within one workspace folder, suitable for pasting
 * into LLMs.
 */
export class LlmContentBuilder {
    private readonly matcher: ExclusionMatcher;
    private readonly decoder = new TextDecoder('utf-8');
    private readonly binaryFiles = new Map<string, boolean>();
    private totalFilesToProcess = 0;
    private processedFilesCount = 0;
    private fileCount = 0;
    private skippedCount = 0;

    constructor(
        private readonly folder: vscode.WorkspaceFolder,
        private readonly settings: Settings,
        private readonly report: ProgressReporter,
        private readonly token: vscode.CancellationToken,
    ) {
        this.matcher = new ExclusionMatcher(settings.excludedPatterns);
    }

    async buildContent(selectedUris: readonly vscode.Uri[]): Promise<BuilderResult> {
        this.report(0, 'Determining project structure...');
        const selection = sortEntries(await Promise.all(selectedUris.map(uri => this.entryOf(uri))));

        this.report(0.1, 'Calculating total files...');
        this.totalFilesToProcess = await this.estimateTotalFiles(selection);

        this.report(0.1, 'Building tree structure...');
        const tree = await this.buildTreeStructureRepresentation(selection);

        this.report(0.2, 'Processing file contents...');
        let fileContents = '';
        for (const entry of selection) {
            fileContents += await this.processFileOrDirectoryContent(entry);
        }

        this.report(1, 'Finalizing...');
        const header = `Selected structure within project '${this.folder.name}':\n`;
        const content = `${header}${tree.trimEnd()}\n\n---\n\n${fileContents}`;
        return { content: content.trim(), fileCount: this.fileCount, skippedCount: this.skippedCount };
    }

    private async estimateTotalFiles(entries: readonly Entry[]): Promise<number> {
        let count = 0;
        for (const entry of entries) {
            this.checkCanceled();
            if (!entry.isDirectory) {
                count++;
            } else if (!entry.isSymbolicLink && !this.matcher.isExcluded(entry.name, this.relativePath(entry))) {
                count += await this.estimateTotalFiles(await this.childrenOf(entry));
            }
        }
        return count;
    }

    private updateProgress(details: string): void {
        this.processedFilesCount++;
        this.checkCanceled();
        const fraction = this.totalFilesToProcess > 0
            ? 0.2 + (this.processedFilesCount / this.totalFilesToProcess) * 0.8
            : 0.2;
        this.report(Math.min(fraction, 1), details);
    }

    /**
     * Builds the tree structure, displaying the hierarchy from the workspace folder
     * down to the selected items and their contents.
     */
    private async buildTreeStructureRepresentation(selection: readonly Entry[]): Promise<string> {
        const selectedRelativePaths = new Set(selection.map(entry => this.relativePath(entry)));
        const ancestorPaths = new Set<string>();
        for (const selectedPath of selectedRelativePaths) {
            let current = selectedPath;
            while (current.includes('/')) {
                current = current.substring(0, current.lastIndexOf('/'));
                if (current.length > 0) ancestorPaths.add(current);
            }
        }

        // Exclusion patterns never hide anything here - they only affect the content section
        // below. A node is visible if it's a descendant of any selected path (at any depth, not
        // just directly under it), or an ancestor of a selected item (to draw the path down to
        // it from the root).
        const isVisible = (candidate: Entry): boolean => {
            const candidatePath = this.relativePath(candidate);
            for (const selected of selectedRelativePaths) {
                if (selected === '' || candidatePath === selected || candidatePath.startsWith(`${selected}/`)) return true;
            }
            return ancestorPaths.has(candidatePath);
        };

        // With "hide binary files" on, a binary file never reaches the tree, and a folder left
        // with nothing to show goes with it. Memoized by path, so answering this for the whole
        // selection costs one extra pass over it rather than one per level of nesting.
        const foldersWithVisibleContent = new Map<string, boolean>();

        const hasVisibleContent = async (dir: Entry): Promise<boolean> => {
            const key = dir.uri.toString();
            const known = foldersWithVisibleContent.get(key);
            if (known !== undefined) return known;
            this.checkCanceled();
            let result = false;
            for (const child of await this.childrenOf(dir)) {
                if (isVisible(child) && await isShownInTree(child)) {
                    result = true;
                    break;
                }
            }
            foldersWithVisibleContent.set(key, result);
            return result;
        };

        const isShownInTree = async (candidate: Entry): Promise<boolean> => {
            if (!isVisible(candidate)) return false;
            if (!this.settings.hideBinaryFilesInTree) return true;
            if (candidate.isDirectory) return candidate.isSymbolicLink || hasVisibleContent(candidate);
            return !(await this.isBinary(candidate));
        };

        let output = '.\n'; // Represent the workspace folder
        const buildTreeRecursive = async (current: Entry, indent: string): Promise<void> => {
            this.checkCanceled();
            const visibleChildren: Entry[] = [];
            for (const child of await this.childrenOf(current)) {
                if (await isShownInTree(child)) visibleChildren.push(child);
            }

            for (const [index, child] of visibleChildren.entries()) {
                const isLastVisibleSibling = index === visibleChildren.length - 1;
                output += `${indent}${isLastVisibleSibling ? '└── ' : '├── '}${child.name}`;
                if (child.isDirectory) {
                    output += '/\n';
                    await buildTreeRecursive(child, indent + (isLastVisibleSibling ? '    ' : '│   '));
                } else {
                    output += '\n';
                }
            }
        };

        await buildTreeRecursive(
            { uri: this.folder.uri, name: this.folder.name, isDirectory: true, isSymbolicLink: false },
            '',
        );
        return output;
    }

    private async processFileOrDirectoryContent(entry: Entry): Promise<string> {
        this.checkCanceled();
        if (!entry.isDirectory) return this.handleFileContent(entry);

        const relativePath = this.relativePath(entry);
        if (this.matcher.isExcluded(entry.name, relativePath)) {
            this.skippedCount++;
            return `\n# Folder: ${relativePath}/\n# (${EXCLUDED_REASON}, folder skipped)\n\n`;
        }
        if (entry.isSymbolicLink) {
            this.skippedCount++;
            return `\n# Folder: ${relativePath}/\n# (symbolic link, folder skipped)\n\n`;
        }

        let output = '';
        for (const child of await this.childrenOf(entry)) {
            output += await this.processFileOrDirectoryContent(child);
        }
        return output;
    }

    /** Handles content extraction and formatting for a single file. */
    private async handleFileContent(entry: Entry): Promise<string> {
        this.updateProgress(`Processing: ${entry.name}`);

        const pathFromRoot = this.relativePath(entry) || entry.name;
        const lineCommentPrefix = commentPrefixFor(entry.name);

        // Header: Start with one blank line, then the File: path line.
        const header = `\n${lineCommentPrefix} File: ${pathFromRoot}\n`;

        let skipReason: string | undefined;
        let content = '';
        try {
            if (this.matcher.isExcluded(entry.name, pathFromRoot)) {
                skipReason = EXCLUDED_REASON;
            } else if ((await vscode.workspace.fs.stat(entry.uri)).size === 0) {
                skipReason = 'empty';
            } else if (hasBinaryExtension(entry.name)) {
                skipReason = 'binary';
            } else {
                const bytes = await vscode.workspace.fs.readFile(entry.uri);
                if (looksBinary(bytes)) skipReason = 'binary';
                else content = this.decoder.decode(bytes);
            }
        } catch (error) {
            this.skippedCount++;
            return `${header}\n${lineCommentPrefix} Error reading file: ${messageOf(error)}\n\n\n`;
        }

        if (skipReason !== undefined) {
            this.skippedCount++;
            // Two extra newlines even after skipped files for consistent spacing
            return `${header}${lineCommentPrefix} (${skipReason} file, content skipped)\n\n\n`;
        }
        this.fileCount++;
        // Newline between header and content, three newlines after the content for spacing
        return `${header}\n${content}\n\n\n`;
    }

    private async isBinary(entry: Entry): Promise<boolean> {
        const key = entry.uri.toString();
        let binary = this.binaryFiles.get(key);
        if (binary === undefined) {
            try {
                binary = await isBinaryFile(entry.uri, entry.name);
            } catch {
                binary = false; // Unreadable files stay in the tree; the content section reports the error
            }
            this.binaryFiles.set(key, binary);
        }
        return binary;
    }

    private async entryOf(uri: vscode.Uri): Promise<Entry> {
        const { type } = await vscode.workspace.fs.stat(uri);
        return toEntry(uri, nameOf(uri), type);
    }

    private async childrenOf(dir: Entry): Promise<Entry[]> {
        if (dir.isSymbolicLink) return [];
        try {
            const children = await vscode.workspace.fs.readDirectory(dir.uri);
            return sortEntries(children.map(([name, type]) => toEntry(vscode.Uri.joinPath(dir.uri, name), name, type)));
        } catch (error) {
            console.warn(`CopyForLlm+: could not read children of ${dir.uri.toString()}`, error);
            return [];
        }
    }

    private relativePath(entry: Entry): string {
        return relativePathOf(entry.uri, this.folder);
    }

    private checkCanceled(): void {
        if (this.token.isCancellationRequested) throw new vscode.CancellationError();
    }
}

function toEntry(uri: vscode.Uri, name: string, type: vscode.FileType): Entry {
    return {
        uri,
        name,
        isDirectory: (type & vscode.FileType.Directory) !== 0,
        isSymbolicLink: (type & vscode.FileType.SymbolicLink) !== 0,
    };
}

/** Folders first, then by name - the order the JetBrains plugin uses. */
function sortEntries(entries: Entry[]): Entry[] {
    return entries.sort((a, b) =>
        a.isDirectory !== b.isDirectory ? (a.isDirectory ? -1 : 1) : a.name < b.name ? -1 : a.name > b.name ? 1 : 0,
    );
}

export function messageOf(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}
