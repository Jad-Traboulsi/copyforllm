package com.aykoo.copyforllm

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Project view action that excludes the selected files/folders from the content
 * CopyForLlm+ copies - or includes them again if they're already excluded. It
 * writes the selection's project-relative paths into the same pattern list as
 * Settings > Tools > CopyForLlm+, so both entry points stay in sync.
 */
class ToggleExclusionAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    /**
     * Presents the action as "exclude" or "include" - text and icon both - depending
     * on what the selection already is, and disables it for a selection that some other pattern (a parent
     * folder, a name pattern) already excludes - there its own path would change nothing.
     */
    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        val patterns = selectedPatterns(e)
        if (patterns.isEmpty()) {
            presentation.isEnabledAndVisible = false
            return
        }

        presentation.isVisible = true
        val configured = CopyForLlmSettingsState.getInstance().excludedPatterns
        when {
            patterns.all { it in configured } -> {
                presentation.text = "Include in Copy for LLM+"
                presentation.description = "Copy the content of the selection again"
                presentation.icon = AllIcons.Actions.Show
                presentation.isEnabled = true
            }

            patterns.all { ExclusionMatcher.isExcludedIncludingAncestors(it, configured) } -> {
                presentation.text = "Already Excluded from Copy for LLM+"
                presentation.description = "The selection already matches an exclusion pattern"
                presentation.icon = AllIcons.Vcs.Ignore_file
                presentation.isEnabled = false
            }

            else -> {
                presentation.text = "Exclude from Copy for LLM+"
                presentation.description = "Leave the content of the selection out of what is copied"
                presentation.icon = AllIcons.Vcs.Ignore_file
                presentation.isEnabled = true
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val patterns = selectedPatterns(e)
        if (patterns.isEmpty()) return

        val settings = CopyForLlmSettingsState.getInstance()
        val message = if (patterns.all { it in settings.excludedPatterns }) {
            settings.removeExcludedPatterns(patterns)
            "${describe(patterns)} included in Copy for LLM+ again."
        } else {
            settings.addExcludedPatterns(patterns)
            "${describe(patterns)} excluded from Copy for LLM+ content."
        }
        CopyForLlmNotifier.notify(project, NotificationType.INFORMATION, message)
    }

    private fun describe(patterns: List<String>): String =
        if (patterns.size == 1) "'${patterns.single()}'" else "${patterns.size} items"

    /** Project-relative paths of the selection, used verbatim as exclusion patterns. */
    private fun selectedPatterns(e: AnActionEvent): List<String> {
        val project = e.project ?: return emptyList()
        val navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY) ?: return emptyList()
        val projectDir = project.guessProjectDir()
        return SelectionResolver.resolve(navigatables)
            .map { patternFor(it, projectDir) }
            .filter { it.isNotEmpty() } // The project root itself has no path to exclude by
            .distinct()
    }

    /**
     * The pattern standing for a single selected item: its project-relative path,
     * which ExclusionMatcher matches exactly. A file outside the project directory
     * falls back to its bare name, matching that name anywhere.
     */
    private fun patternFor(file: VirtualFile, projectDir: VirtualFile?): String {
        val relativePath = projectDir?.let { VfsUtilCore.getRelativePath(file, it, '/') }
        return (relativePath ?: file.name).trim()
    }
}
