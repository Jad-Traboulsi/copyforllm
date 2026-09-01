package com.aykoo.copyforllm

import com.intellij.lang.Commenter
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

/** Holds the result of the content building operation. */
data class BuilderResult(
    val clipboardContent: String,
    val fileCount: Int,
    val skippedCount: Int
)

/**
 * Builds a formatted string containing a file tree representation and the content
 * of selected files/directories, suitable for pasting into LLMs.
 */
class LlmContentBuilder(
    private val project: Project,
    private val indicator: ProgressIndicator
) {
    private val logger = Logger.getInstance(LlmContentBuilder::class.java)
    private var totalFilesToProcess = 0
    private var processedFilesCount = 0
    private val excludedContentPatterns = CopyForLlmSettingsState.getInstance().excludedPatterns

    fun buildContent(selectedFiles: Array<VirtualFile>): BuilderResult {
        if (selectedFiles.isEmpty()) return BuilderResult("No files selected.", 0, 0)

        indicator.text2 = "Determining project structure..."
        indicator.fraction = 0.0

        val projectDir = project.guessProjectDir()
        if (projectDir == null) {
            logger.warn("Could not determine project base directory.")
            return BuilderResult("Error: Could not determine project base directory.", 0, 0)
        }
        logger.info("Using project base directory for paths: ${projectDir.path}")

        val treeStructure = StringBuilder()
        val fileContents = StringBuilder()
        var fileCount = 0
        var skippedCount = 0

        val sortedSelection = selectedFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name }))

        indicator.text2 = "Calculating total files..."
        indicator.fraction = 0.1
        totalFilesToProcess = estimateTotalFiles(sortedSelection, projectDir)
        logger.info("Estimated total files to process: $totalFilesToProcess")
        indicator.isIndeterminate = totalFilesToProcess <= 0

        indicator.text2 = "Building tree structure..."
        buildTreeStructureRepresentation(sortedSelection, projectDir, treeStructure)
        indicator.fraction = 0.2

        indicator.text2 = "Processing file contents..."
        processedFilesCount = 0
        processSelectionContents(
            sortedSelection, projectDir, fileContents,
            onFileProcessed = { fileCount++ },
            onFileSkipped = { _, _ -> skippedCount++ }
        )

        indicator.fraction = 1.0
        indicator.text2 = "Finalizing..."
        val header = "Selected structure within project '${project.name}':\n"
        val finalContent = "$header${treeStructure.toString().trimEnd()}\n\n---\n\n$fileContents"

        return BuilderResult(finalContent.trim(), fileCount, skippedCount)
    }

    private fun estimateTotalFiles(files: List<VirtualFile>, projectDir: VirtualFile): Int {
        var count = 0
        for (file in files) {
            try {
                indicator.checkCanceled()
            } catch (e: ProcessCanceledException) {
                throw e
            }
            if (file.isDirectory) {
                val relativePath = VfsUtilCore.getRelativePath(file, projectDir, '/') ?: file.name
                if (ExclusionMatcher.isExcluded(file.name, relativePath, excludedContentPatterns)) continue
                try {
                    val children = file.children ?: continue
                    count += estimateTotalFiles(children.toList(), projectDir)
                } catch (e: Exception) {
                    logger.warn("Could not estimate children for ${file.path}", e)
                }
            } else {
                count++
            }
        }
        return count
    }

    private fun updateProgress(details: String) {
        processedFilesCount++
        try {
            indicator.checkCanceled()
        } catch (e: ProcessCanceledException) {
            throw e
        }
        if (!indicator.isIndeterminate && totalFilesToProcess > 0) {
            val calculatedFraction = 0.2 + (processedFilesCount.toDouble() / totalFilesToProcess.toDouble()) * 0.8
            indicator.fraction = calculatedFraction.coerceAtMost(1.0)
        }
        indicator.text2 = details
    }

    /**
     * Builds the tree structure, displaying the hierarchy from the project root
     * down to the selected items and their contents.
     */
    private fun buildTreeStructureRepresentation(
        selection: List<VirtualFile>,
        projectDir: VirtualFile,
        output: StringBuilder
    ) {
        val selectedRelativePaths = selection.mapNotNull { VfsUtilCore.getRelativePath(it, projectDir, '/') }.toSet()
        val ancestorPaths = mutableSetOf<String>()
        selectedRelativePaths.forEach { path ->
            var current = path
            while (current.contains('/')) {
                current = current.substringBeforeLast('/')
                if (current.isNotEmpty()) {
                    ancestorPaths.add(current)
                }
            }
        }

        fun buildTreeRecursive(currentFile: VirtualFile, indent: String) {
            try {
                indicator.checkCanceled()
            } catch (e: ProcessCanceledException) {
                throw e
            }

            val children = try {
                currentFile.children?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
            } catch (e: Exception) {
                logger.warn("Could not read children of ${currentFile.path} for tree view", e)
                emptyList()
            }

            val parentRelativePath = VfsUtilCore.getRelativePath(currentFile, projectDir, '/')

            // A node is visible if it's part of the selection, an ancestor of a selected item, or
            // within a selected directory - unless it's a directory matching an exclusion pattern,
            // which is dropped entirely (not listed, not recursed into), like a .gitignore rule.
            fun isVisible(candidate: VirtualFile): Boolean {
                val candidateRelativePath = VfsUtilCore.getRelativePath(candidate, projectDir, '/') ?: return false
                if (candidate.isDirectory &&
                    ExclusionMatcher.isExcluded(candidate.name, candidateRelativePath, excludedContentPatterns)
                ) {
                    return false
                }
                val isSelected = selectedRelativePaths.contains(candidateRelativePath)
                val isAncestor = ancestorPaths.contains(candidateRelativePath)
                val isChildOfSelectedDir =
                    parentRelativePath != null && selectedRelativePaths.contains(parentRelativePath)
                return isSelected || isAncestor || isChildOfSelectedDir
            }

            val visibleChildren = children.filter(::isVisible)

            visibleChildren.forEachIndexed { index, child ->
                val isLastVisibleSibling = index == visibleChildren.lastIndex
                val prefix = if (isLastVisibleSibling) "└── " else "├── "
                output.append("$indent$prefix${child.name}")

                if (child.isDirectory) {
                    output.append("/\n")
                    val childIndent = indent + if (isLastVisibleSibling) "    " else "│   "
                    buildTreeRecursive(child, childIndent)
                } else {
                    output.append("\n")
                }
            }
        }

        output.append(".\n") // Represent the project root
        buildTreeRecursive(projectDir, "")
    }


    private fun processSelectionContents(
        selection: List<VirtualFile>,
        projectDir: VirtualFile,
        output: StringBuilder,
        onFileProcessed: (VirtualFile) -> Unit,
        onFileSkipped: (VirtualFile, String) -> Unit
    ) {
        selection.forEach { file ->
            processFileOrDirectoryContent(file, projectDir, output, onFileProcessed, onFileSkipped)
        }
    }

    private fun processFileOrDirectoryContent(
        file: VirtualFile,
        projectDir: VirtualFile,
        output: StringBuilder,
        onFileProcessed: (VirtualFile) -> Unit,
        onFileSkipped: (VirtualFile, String) -> Unit
    ) {
        try {
            indicator.checkCanceled()
        } catch (e: ProcessCanceledException) {
            throw e
        }
        if (file.isDirectory) {
            val relativePath = VfsUtilCore.getRelativePath(file, projectDir, '/') ?: file.name
            if (ExclusionMatcher.isExcluded(file.name, relativePath, excludedContentPatterns)) return

            try {
                val children = file.children?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
                children.forEach { child ->
                    processFileOrDirectoryContent(child, projectDir, output, onFileProcessed, onFileSkipped)
                }
            } catch (e: Exception) {
                logger.warn("Could not process children of ${file.path} for content", e)
            }
        } else {
            handleFileContent(file, projectDir, output, onFileProcessed, onFileSkipped)
        }
    }

    /**
     * Handles content extraction and formatting for a single file.
     */
    private fun handleFileContent(
        file: VirtualFile,
        projectDir: VirtualFile,
        output: StringBuilder,
        onFileProcessed: (VirtualFile) -> Unit,
        onFileSkipped: (VirtualFile, String) -> Unit
    ) {
        updateProgress("Processing: ${file.name}")

        val pathFromRoot = VfsUtilCore.getRelativePath(file, projectDir, '/') ?: file.name

        if (ExclusionMatcher.isExcluded(file.name, pathFromRoot, excludedContentPatterns)) {
            // Excluded files are left out of the content section entirely - only their
            // location in the tree is shown, nothing is written here at all.
            onFileSkipped(file, "excluded by CopyForLlm settings")
            return
        }

        val lineCommentPrefix = getLanguageCommentPrefix(file)

        // Header: Start with one blank line, then the File: path line.
        output.append("\n$lineCommentPrefix File: $pathFromRoot\n")

        val skipReason = when {
            file.length == 0L -> "empty"
            file.fileType.isBinary -> "binary"
            // TODO: Add file size check here
            else -> null
        }

        if (skipReason != null) {
            output.append("$lineCommentPrefix ($skipReason file, content skipped)\n")
            onFileSkipped(file, skipReason)
            output.append("\n\n") // Add two extra newlines even after skipped files for consistent spacing
        } else {
            try {
                val content = VfsUtilCore.loadText(file)
                output.append("\n") // Newline between header and content
                output.append(content)
                // Add three newlines after the content for spacing
                output.append("\n\n\n")
                onFileProcessed(file)
            } catch (e: IOException) {
                logger.warn("Could not read file content: ${file.path}", e)
                output.append("\n$lineCommentPrefix Error reading file: ${e.message}\n\n\n") // Add spacing after error
                onFileSkipped(file, "read error")
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                logger.error("Error processing file content: ${file.path}", e)
                output.append("\n$lineCommentPrefix Error processing file: ${e.message}\n\n\n") // Add spacing after error
                onFileSkipped(file, "processing error")
            }
        }
    }

    /** Determines the appropriate line comment prefix (e.g., "//", "#") for a file. */
    private fun getLanguageCommentPrefix(file: VirtualFile): String {
        return try {
            val language = LanguageUtil.getFileLanguage(file)
            val commenter: Commenter? = language?.let { LanguageCommenters.INSTANCE.forLanguage(it) }
            commenter?.lineCommentPrefix?.takeIf { it.isNotBlank() } ?: "#"
        } catch (e: Exception) {
            logger.warn("Could not determine language commenter for ${file.name}", e)
            "#" // Default fallback
        }
    }
}