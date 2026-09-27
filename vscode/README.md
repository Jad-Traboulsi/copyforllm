# CopyForLlm+ for VS Code

Copies selected files and folders - their structure and content - to the clipboard, formatted for pasting into Large
Language Models (LLMs).

This is the VS Code port of the [CopyForLlm+ JetBrains plugin](https://github.com/Jad-Traboulsi/copyforllm), itself a
fork of [AykoSc/copyforllm](https://github.com/AykoSc/copyforllm), licensed under the Apache License 2.0 (see
`LICENSE`).

# Usage

Right-click files/folders in the Explorer, select **Copy for LLM+**, and paste into your LLM prompt.

The output includes a file tree representing the selection within the workspace folder and the content of the included
files, each preceded by a header comment indicating its path relative to the workspace folder. Selecting items from
several folders of a multi-root workspace copies one such block per folder.

# Excluding sensitive files and folders

Right-click a file or folder in the Explorer and choose **Exclude from Copy for LLM+**. Its workspace-relative path is
added to the exclusion patterns; right-clicking it again offers **Include in Copy for LLM+** to undo that. If some
other pattern - a parent folder, or a name pattern like `node_modules` - already covers the selection, excluding it
changes nothing and says so.

For patterns rather than single items, edit the **CopyForLlm+: Excluded Patterns** setting (`copyForLlm.excludedPatterns`)
directly (`*`/`?` wildcards supported, case-insensitive) - e.g. `.env`, `.env.*`, `*.pem`, `node_modules`, `secrets`.
Both entry points write to the same list: your user settings, shared by all workspaces, unless the workspace overrides
the setting, in which case they write there.

A match - whether it's a single file or a whole folder - always stays visible in the file tree, so the LLM still
knows it exists. In the content section it gets a short note that it was skipped (e.g.
`# (excluded by CopyForLlm+ settings file, content skipped)`), but its actual content is never included. For a
matched folder this applies to everything inside it too, without descending into and noting every file inside it
individually.

`.env` and `node_modules` are excluded by default.

# Hiding binary files from the tree

**CopyForLlm+: Hide Binary Files In Tree** (`copyForLlm.hideBinaryFilesInTree`), on by default, drops binary files from
the tree, and any folder left with nothing else to show - the tree stays about the code the LLM can actually read.

This only affects the tree. The content section still names every binary file and notes that its content was skipped.

# Differences from the JetBrains plugin

- VS Code has no API for a language's comment syntax, so the header prefix (`//`, `#`, `--`, ...) comes from a table
  of file extensions; unknown types get `#`.
- Binary files are recognised by extension, or by a NUL byte in their first 8000 bytes.
- Symlinked folders are listed but not descended into.

# Development

```
npm install
npm run compile
```

Open this `vscode/` folder in VS Code and press F5 to launch an Extension Development Host with the extension loaded.
Package it with `npx @vscode/vsce package`, which produces a `.vsix` you can install via **Extensions > ... > Install
from VSIX...**.
