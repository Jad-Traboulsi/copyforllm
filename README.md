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
`*`/`?` wildcards supported, case-insensitive) - e.g. `.env`, `.env.*`, `*.pem`, `node_modules`, `secrets`.

A match - whether it's a single file or a whole folder - always stays visible in the file tree, so the LLM still
knows it exists. In the content section it gets a short note that it was skipped (e.g.
`# (excluded by CopyForLlm settings file, content skipped)`), but its actual content is never included. For a
matched folder this applies to everything inside it too, without descending into and noting every file inside it
individually.

`.env` and `node_modules` are excluded by default.

# Caveats

Only tested with IntelliJ. Other JetBrains IDEs should work, but are not guaranteed to be supported.