# CopyForLlm+ JetBrains Plugin

Copies selected project file structure and content to the clipboard, formatted for pasting into Large Language Models (
LLMs).

This is a fork of [AykoSc/copyforllm](https://github.com/AykoSc/copyforllm), licensed under the
Apache License 2.0 (see `LICENSE`). Changes in this fork: added a settings page to exclude sensitive files and
folders (e.g. `.env`, `node_modules`) from the copied content.

# Usage

Right-click files/folders in the Project view, select "Copy for LLM", and paste into your LLM prompt.

The output includes a file tree representing the selection within the project and the content of the included files,
each preceded by a header comment indicating its path relative to the project root.

# Excluding sensitive files and folders

Go to **Settings/Preferences > Tools > CopyForLlm** to maintain a list of filename/path patterns (one per line,
`*`/`?` wildcards supported, case-insensitive).

- A pattern matching a **file** (e.g. `.env`, `.env.*`, `*.pem`) still shows the file's location in the tree, but
  leaves it out of the copied content entirely - no header, no content, nothing.
- A pattern matching a **folder** (e.g. `node_modules`, `secrets`) excludes that whole folder, and everything
  inside it, from both the tree and the copy - like a `.gitignore` rule.

`.env` and `node_modules` are excluded by default.

# Caveats

Only tested with IntelliJ. Other JetBrains IDEs should work, but are not guaranteed to be supported.