# Changelog

## 1.1.1

- Added screenshots and a demo GIF to the README.

## 1.1.0

- Added **Copy Matching Files for LLM+** to the Explorer context menu for folders. It asks for a list of extensions
  (`.xml` or `xml`) and/or exact file names (`pom.xml`) and copies only the matching files found at any depth under
  the selection; the tree shows just the paths down to them. Exclusion patterns and the hide-binary setting apply as
  usual, and excluded folders are not searched. Ported from the CopyForLlm+ 1.4.0 JetBrains plugin.

## 1.0.0

- Initial VS Code release, ported from the CopyForLlm+ 1.3.0 JetBrains plugin: **Copy for LLM+**, **Exclude from /
  Include in Copy for LLM+** in the Explorer context menu, the `copyForLlm.excludedPatterns` setting (`.env` and
  `node_modules` by default) and the `copyForLlm.hideBinaryFilesInTree` setting (on by default).
