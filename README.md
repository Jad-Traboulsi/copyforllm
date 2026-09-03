# CopyForLlm+ JetBrains Plugin

Copies selected project file structure and content to the clipboard, formatted for pasting into Large Language Models (
LLMs).

This is a fork of [AykoSc/copyforllm](https://github.com/AykoSc/copyforllm), licensed under the
Apache License 2.0 (see `LICENSE`). Changes in this fork: sensitive files and folders (e.g. `.env`, `node_modules`)
can be excluded from the copied content, from a settings page or straight from the Project view context menu.

# Usage

Right-click files/folders in the Project view, select "Copy for LLM+", and paste into your LLM prompt.

The output includes a file tree representing the selection within the project and the content of the included files,
each preceded by a header comment indicating its path relative to the project root.

# Excluding sensitive files and folders

Right-click a file or folder in the Project view and choose **Exclude from Copy for LLM+**. Its project-relative
path is added to the exclusion patterns; right-clicking it again offers **Include in Copy for LLM+** to undo that.
The entry is disabled ("Already Excluded from Copy for LLM+") when some other pattern - a parent folder, or a name
pattern like `node_modules` - already covers the selection.

For patterns rather than single items, go to **Settings/Preferences > Tools > CopyForLlm+** to maintain the list
directly (one per line, `*`/`?` wildcards supported, case-insensitive) - e.g. `.env`, `.env.*`, `*.pem`,
`node_modules`, `secrets`. Both entry points write to the same list, which is shared by all projects.

A match - whether it's a single file or a whole folder - always stays visible in the file tree, so the LLM still
knows it exists. In the content section it gets a short note that it was skipped (e.g.
`# (excluded by CopyForLlm+ settings file, content skipped)`), but its actual content is never included. For a
matched folder this applies to everything inside it too, without descending into and noting every file inside it
individually.

`.env` and `node_modules` are excluded by default.

# Caveats

Only tested with IntelliJ. Other JetBrains IDEs should work, but are not guaranteed to be supported.