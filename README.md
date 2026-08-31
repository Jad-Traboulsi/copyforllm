# Copy for LLM JetBrains Plugin

Copies selected project file structure and content to the clipboard, formatted for pasting into Large Language Models (
LLMs).

This is a modified copy of [AykoSc/copyforllm](https://github.com/AykoSc/copyforllm), licensed under the
Apache License 2.0 (see `LICENSE`). Changes in this copy: added a settings page to exclude sensitive files
(e.g. `.env`) from the copied content.

# Usage

Right-click files/folders in the Project view, select "Copy for LLM", and paste into your LLM prompt.

The output includes a file tree representing the selection within the project and the content of the included files,
each preceded by a header comment indicating its path relative to the project root.

# Excluding sensitive files

Go to **Settings/Preferences > Tools > CopyForLlm** to maintain a list of filename/path patterns (one per line,
`*`/`?` wildcards supported, e.g. `.env`, `.env.*`, `*.pem`) whose content should never be copied. Matching files
still show up in the file tree, but their content is replaced with a placeholder, the same way binary/empty files
are handled. `.env` is excluded by default.

# Caveats

Only tested with IntelliJ. Other JetBrains IDEs should work, but are not guaranteed to be supported.