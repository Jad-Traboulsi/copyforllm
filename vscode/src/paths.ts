import * as path from 'path';
import * as vscode from 'vscode';

export function nameOf(uri: vscode.Uri): string {
    return path.posix.basename(uri.path);
}

/** The path of a URI relative to its workspace folder, '/'-separated; "" for the folder itself. */
export function relativePathOf(uri: vscode.Uri, folder: vscode.WorkspaceFolder): string {
    return path.posix.relative(folder.uri.path, uri.path);
}
