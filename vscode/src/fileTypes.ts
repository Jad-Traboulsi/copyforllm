import { promises as fs } from 'fs';
import * as vscode from 'vscode';

/**
 * VS Code has no API that maps a file to its language's comment syntax without
 * opening it as a document, so the header prefix comes from this table instead.
 * Anything not listed gets "#", the same fallback the JetBrains plugin uses for
 * languages without a line comment (Markdown, HTML, CSS, ...).
 */
const LINE_COMMENT_PREFIXES: Record<string, string> = {
    ...prefixFor('//', [
        'c', 'h', 'cc', 'cpp', 'cxx', 'hh', 'hpp', 'hxx', 'm', 'mm', 'cs', 'java', 'kt', 'kts', 'scala', 'sc',
        'groovy', 'gradle', 'js', 'jsx', 'mjs', 'cjs', 'ts', 'tsx', 'mts', 'cts', 'go', 'rs', 'swift', 'dart',
        'php', 'zig', 'v', 'sv', 'proto', 'jsonc', 'json5', 'scss', 'less', 'sol', 'fs', 'fsx', 'fsi', 'hx',
    ]),
    ...prefixFor('--', ['sql', 'lua', 'hs', 'elm', 'ada', 'adb', 'ads']),
    ...prefixFor(';', ['clj', 'cljs', 'cljc', 'edn', 'lisp', 'el', 'scm', 'rkt', 'asm', 's', 'ini']),
    ...prefixFor('%', ['tex', 'sty', 'erl', 'hrl']),
    ...prefixFor("'", ['vb', 'vbs', 'bas']),
    ...prefixFor('REM', ['bat', 'cmd']),
};

function prefixFor(prefix: string, extensions: string[]): Record<string, string> {
    return Object.fromEntries(extensions.map(extension => [extension, prefix]));
}

/** Determines the line comment prefix (e.g. "//", "#") for a file, from its extension. */
export function commentPrefixFor(fileName: string): string {
    return LINE_COMMENT_PREFIXES[extensionOf(fileName)] ?? '#';
}

const BINARY_EXTENSIONS = new Set([
    'png', 'jpg', 'jpeg', 'gif', 'bmp', 'ico', 'icns', 'webp', 'tif', 'tiff', 'psd', 'heic', 'avif',
    'mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'mp4', 'mov', 'avi', 'mkv', 'webm',
    'zip', 'gz', 'tgz', 'bz2', 'xz', '7z', 'rar', 'tar', 'jar', 'war', 'ear', 'aar', 'apk', 'ipa', 'dmg', 'iso',
    'exe', 'dll', 'so', 'dylib', 'a', 'lib', 'o', 'obj', 'class', 'pyc', 'pyo', 'wasm', 'bin',
    'db', 'sqlite', 'sqlite3', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt',
    'ttf', 'otf', 'woff', 'woff2', 'eot',
]);

/** How many leading bytes are inspected for a NUL byte, the telltale of a binary file. */
const SNIFF_LENGTH = 8000;

export function hasBinaryExtension(fileName: string): boolean {
    return BINARY_EXTENSIONS.has(extensionOf(fileName));
}

export function looksBinary(bytes: Uint8Array): boolean {
    return bytes.subarray(0, SNIFF_LENGTH).includes(0);
}

/**
 * Whether a file is binary: by extension first, then by sniffing its first bytes.
 * Local files are sniffed with a partial read so large files aren't loaded whole.
 */
export async function isBinaryFile(uri: vscode.Uri, fileName: string): Promise<boolean> {
    if (hasBinaryExtension(fileName)) return true;
    if (uri.scheme !== 'file') return looksBinary(await vscode.workspace.fs.readFile(uri));

    const handle = await fs.open(uri.fsPath, 'r');
    try {
        const buffer = Buffer.alloc(SNIFF_LENGTH);
        const { bytesRead } = await handle.read(buffer, 0, SNIFF_LENGTH, 0);
        return looksBinary(buffer.subarray(0, bytesRead));
    } finally {
        await handle.close();
    }
}

function extensionOf(fileName: string): string {
    const dot = fileName.lastIndexOf('.');
    return dot <= 0 ? '' : fileName.substring(dot + 1).toLowerCase();
}
