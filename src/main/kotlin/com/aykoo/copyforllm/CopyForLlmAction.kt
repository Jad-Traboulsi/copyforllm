package com.aykoo.copyforllm

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection


class CopyForLlmAction : AnAction() {

    private val logger = Logger.getInstance(CopyForLlmAction::class.java)

    /**
     * Specify that the update method should run on the EDT (Event Dispatch Thread)
     * as it accesses data context which requires the UI thread. Required for newer platform versions.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    /**
     * Determines if the action should be enabled and visible.
     * Enabled only if a project is open and at least one item is selected in the context.
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY)
        val isEnabled = project != null && !navigatables.isNullOrEmpty()
        // Logger call kept for temporary debugging if needed, can be removed later
        // logger.info("CopyForLlmAction update called. Project: ${project?.name}, Navigatables selected: ${navigatables?.size ?: 0}, Setting enabled: $isEnabled")
        e.presentation.isEnabledAndVisible = isEnabled
    }

    /**
     * Executes the action when the user clicks the menu item.
     * Resolves selected items to VirtualFiles and triggers the LlmContentBuilder in a background task.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY) ?: return

        // Optional: Log the types received for deeper debugging if resolution fails
        // navigatables.forEachIndexed { index, nav ->
        //    logger.info("Navigatable[$index]: Type=${nav?.javaClass?.name ?: "null"}, Value=${nav}")
        // }

        val selectedFiles = SelectionResolver.resolve(navigatables)

        logger.info("Resolved ${selectedFiles.size} distinct VirtualFiles from ${navigatables.size} Navigatables.")
        // Optional: Log resolved files
        // selectedFiles.forEachIndexed { index, vf -> logger.debug("  Final VF[$index]: ${vf.path}") }

        copyForLlm(project, "Could not determine the selected files/folders to copy.") { selectedFiles }
    }
}

private val copyLogger = Logger.getInstance("com.aykoo.copyforllm.CopyForLlm")

/**
 * Builds the LLM content for the files [collectFiles] returns and puts it on the clipboard,
 * all in a cancellable background task, shared by every CopyForLlm+ copy action. Collecting
 * runs in that task too, so walking a large folder doesn't block the UI. When nothing is
 * collected, [emptyMessage] is shown as a warning and the clipboard is left alone.
 */
internal fun copyForLlm(
    project: Project,
    emptyMessage: String,
    collectFiles: (ProgressIndicator) -> List<VirtualFile>
) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Copying for LLM+", true) {
        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false
            indicator.fraction = 0.0
            indicator.text = "Preparing copy operation..."

            try {
                val selectedFiles = collectFiles(indicator)
                if (selectedFiles.isEmpty()) {
                    copyLogger.warn("Copy for LLM+ found no files to copy.")
                    ApplicationManager.getApplication().invokeLater {
                        CopyForLlmNotifier.notify(project, NotificationType.WARNING, emptyMessage)
                    }
                    return
                }

                val contentBuilder = LlmContentBuilder(project, indicator)
                val result = contentBuilder.buildContent(selectedFiles.toTypedArray())

                indicator.fraction = 1.0
                indicator.text = "Copying to clipboard..."

                // Copying to clipboard must happen on the EDT
                ApplicationManager.getApplication().invokeLater {
                    val contentToCopy = result.clipboardContent
                    val charCount = contentToCopy.length

                    CopyPasteManager.getInstance().setContents(StringSelection(contentToCopy))
                    CopyForLlmNotifier.notify(
                        project,
                        NotificationType.INFORMATION,
                        "Copied ${result.fileCount} files (${result.skippedCount} skipped, ${charCount} characters) to clipboard."
                    )
                }
            } catch (ex: ProcessCanceledException) {
                throw ex
            } catch (ex: Exception) { // Catch generic exceptions during the process
                copyLogger.error("Copy for LLM+ action failed.", ex)
                ApplicationManager.getApplication().invokeLater {
                    CopyForLlmNotifier.notify(
                        project,
                        NotificationType.ERROR,
                        "Error copying content: ${ex.message ?: "Unknown error"}"
                    )
                }
            }
        }
    })
}