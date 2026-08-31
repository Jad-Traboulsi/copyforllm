package com.aykoo.copyforllm

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * Settings > Tools > CopyForLlm. Lets the user maintain a list of filename/path
 * patterns matched against files and folders. A pattern matching a **file**
 * (e.g. ".env", "*.pem") hides only that file's content - it still appears in
 * the tree. A pattern matching a **folder** (e.g. "node_modules", "secrets")
 * excludes that whole folder, and everything inside it, from both the tree
 * and the copy - the same way a .gitignore rule would.
 */
class CopyForLlmConfigurable : Configurable {

    private var textArea: JBTextArea? = null

    override fun getDisplayName(): String = "CopyForLlm"

    override fun createComponent(): JComponent {
        val area = JBTextArea(10, 40)
        area.lineWrap = false
        textArea = area

        val info = JBLabel(
            "<html>A pattern matching a <b>file</b> hides only that file's content - it still appears in the tree.<br>" +
                "A pattern matching a <b>folder</b> excludes that whole folder, and everything inside it, from<br>" +
                "both the tree and the copy.<br>" +
                "One pattern per line. '*' and '?' wildcards are supported, matching is case-insensitive.<br>" +
                "Example: <code>.env</code>, <code>*.pem</code> (files) &nbsp;&middot;&nbsp; " +
                "<code>node_modules</code>, <code>secrets</code> (folders)</html>"
        )
        info.border = EmptyBorder(0, 0, 8, 0)

        val panel = JPanel(BorderLayout())
        panel.border = EmptyBorder(8, 8, 8, 8)
        panel.add(info, BorderLayout.NORTH)
        panel.add(JBScrollPane(area), BorderLayout.CENTER)
        return panel
    }

    private fun currentPatterns(): List<String> =
        textArea?.text.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }

    override fun isModified(): Boolean =
        currentPatterns() != CopyForLlmSettingsState.getInstance().excludedPatterns

    override fun apply() {
        CopyForLlmSettingsState.getInstance().excludedPatterns = currentPatterns().toMutableList()
    }

    override fun reset() {
        textArea?.text = CopyForLlmSettingsState.getInstance().excludedPatterns.joinToString("\n")
    }

    override fun disposeUIResources() {
        textArea = null
    }
}
