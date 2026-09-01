package com.aykoo.copyforllm

/**
 * Matches a file or directory's name or project-relative path against the
 * user-configured exclusion patterns. Patterns support the '*' and '?' glob
 * wildcards and are matched case-insensitively against both the bare name and
 * the full relative path, so ".env" or "node_modules" excludes that name
 * anywhere in the tree without needing a wildcard directory prefix.
 *
 * Excluded files and directories are never dropped from the tree the plugin
 * builds - only from the content it copies, so a match stays visible as a
 * location without exposing what's in it.
 */
object ExclusionMatcher {

    fun isExcluded(fileName: String, relativePath: String, patterns: List<String>): Boolean {
        val compiled = patterns.mapNotNull { pattern ->
            val trimmed = pattern.trim()
            if (trimmed.isEmpty()) null else globToRegex(trimmed)
        }
        return compiled.any { it.matches(fileName) || it.matches(relativePath) }
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("(?i)")
        for (c in glob) {
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '\\', '{', '}', '[', ']' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        return Regex(sb.toString())
    }
}
