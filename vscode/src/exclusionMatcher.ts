/**
 * Matches a file or directory's name or workspace-relative path against the
 * user-configured exclusion patterns. Patterns support the '*' and '?' glob
 * wildcards and are matched case-insensitively against both the bare name and
 * the full relative path, so ".env" or "node_modules" excludes that name
 * anywhere in the tree without needing a wildcard directory prefix.
 *
 * Excluded files and directories are never dropped from the tree the extension
 * builds - only from the content it copies, so a match stays visible as a
 * location without exposing what's in it.
 */
export class ExclusionMatcher {
    private readonly regexes: RegExp[];

    constructor(patterns: readonly string[]) {
        this.regexes = patterns
            .map(pattern => pattern.trim())
            .filter(pattern => pattern.length > 0)
            .map(globToRegex);
    }

    isExcluded(fileName: string, relativePath: string): boolean {
        return this.regexes.some(regex => regex.test(fileName) || regex.test(relativePath));
    }

    /**
     * Whether a workspace-relative path is excluded either in its own right or
     * because one of its parent folders matches - mirroring the builder, which
     * notes a matched folder as skipped instead of descending into it.
     */
    isExcludedIncludingAncestors(relativePath: string): boolean {
        let current = relativePath;
        while (current.length > 0) {
            if (this.isExcluded(current.substring(current.lastIndexOf('/') + 1), current)) return true;
            if (!current.includes('/')) return false;
            current = current.substring(0, current.lastIndexOf('/'));
        }
        return false;
    }
}

function globToRegex(glob: string): RegExp {
    let source = '';
    for (const c of glob) {
        if (c === '*') source += '.*';
        else if (c === '?') source += '.';
        else source += c.replace(/[.+^$(){}[\]|\\]/g, '\\$&');
    }
    return new RegExp(`^${source}$`, 'i');
}
