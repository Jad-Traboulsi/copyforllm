package com.aykoo.copyforllm

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * Project view action that asks for a list of extensions and/or file names and copies
 * only the matching files found under the selected folders, at any depth. The matches
 * are handed to LlmContentBuilder as the selection, so the tree shows just the paths
 * down to them and the exclusion and "hide binary" settings apply as usual. Folders
 * matching an exclusion pattern are not searched at all.
 */
class CopyMatchingFilesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    /** Shown only when the selection contains at least one folder to search. */
    override fun update(e: AnActionEvent) {
        val navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY)
        e.presentation.isEnabledAndVisible = e.project != null && !navigatables.isNullOrEmpty() &&
            SelectionResolver.resolve(navigatables).any { it.isDirectory }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY) ?: return
        val selection = SelectionResolver.resolve(navigatables)

        val properties = PropertiesComponent.getInstance()
        val dialog = EntriesDialog(project, properties.getList(LAST_ENTRIES_KEY).orEmpty())
        if (!dialog.showAndGet()) return
        val entries = dialog.entries()
        if (entries.isEmpty()) return
        properties.setList(LAST_ENTRIES_KEY, entries)

        val folders = selection.filter { it.isDirectory }
        val where = if (folders.size == 1) "'${folders.single().name}'" else "the selected folders"
        val emptyMessage = "No files matching ${entries.joinToString(", ")} found under $where."

        val terms = entries.map { it.lowercase() }
        copyForLlm(project, emptyMessage) { indicator -> collectMatches(project, selection, terms, indicator) }
    }

    /**
     * Files under the selection whose name matches one of [terms], in no particular
     * order (the builder sorts). A selected file counts if it matches itself.
     */
    private fun collectMatches(
        project: Project,
        selection: List<VirtualFile>,
        terms: List<String>,
        indicator: ProgressIndicator
    ): List<VirtualFile> {
        indicator.text2 = "Finding matching files..."
        val projectDir = project.guessProjectDir()
        val excludedPatterns = CopyForLlmSettingsState.getInstance().excludedPatterns
        val matches = linkedSetOf<VirtualFile>()

        selection.forEach { root ->
            VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
                override fun visitFileEx(file: VirtualFile): Result {
                    indicator.checkCanceled()
                    if (file.isDirectory) {
                        val relativePath = projectDir?.let { VfsUtilCore.getRelativePath(file, it, '/') } ?: file.name
                        return if (ExclusionMatcher.isExcluded(file.name, relativePath, excludedPatterns)) SKIP_CHILDREN
                        else CONTINUE
                    }
                    if (isMatch(file.name.lowercase(), terms)) matches.add(file)
                    return CONTINUE
                }
            })
        }
        return matches.toList()
    }

    /**
     * ".xml" matches names ending in it; "xml" means the same and also the exact name
     * (so "Dockerfile" works); anything with a dot inside, like "pom.xml", is an exact name.
     * Both [name] and [terms] are lowercase.
     */
    private fun isMatch(name: String, terms: List<String>): Boolean = terms.any { term ->
        when {
            term.startsWith('.') -> name.endsWith(term)
            '.' !in term -> name.endsWith(".$term") || name == term
            else -> name == term
        }
    }

    /** One entry per line, laid out like the exclusion list in Settings > Tools > CopyForLlm+. */
    private class EntriesDialog(project: Project, lastEntries: List<String>) : DialogWrapper(project) {

        private val textArea = JBTextArea(lastEntries.joinToString("\n"), 8, 30)

        init {
            title = "Copy Matching Files for LLM+"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val info = JBLabel(
                "<html>One per line: extensions (<code>.xml</code> or <code>xml</code>)<br>" +
                    "or exact file names (<code>pom.xml</code>). Matching is case-insensitive.</html>"
            )
            info.border = EmptyBorder(0, 0, 8, 0)

            val panel = JPanel(BorderLayout())
            panel.add(info, BorderLayout.NORTH)
            panel.add(JBScrollPane(textArea), BorderLayout.CENTER)
            return panel
        }

        override fun getPreferredFocusedComponent(): JComponent = textArea

        fun entries(): List<String> = textArea.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private companion object {
        const val LAST_ENTRIES_KEY = "com.aykoo.copyforllm.CopyMatchingFilesAction.lastEntries"
    }
}
