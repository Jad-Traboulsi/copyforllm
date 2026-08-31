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
 * patterns (e.g. ".env", "*.pem") whose content is never copied, even when the
 * file itself is part of the selection.
 */
class CopyForLlmConfigurable : Configurable {

    private var textArea: JBTextArea? = null

    override fun getDisplayName(): String = "CopyForLlm"

    override fun createComponent(): JComponent {
        val area = JBTextArea(10, 40)
        area.lineWrap = false
        textArea = area

        val info = JBLabel(
            "<html>Files whose name or project-relative path matches one of these patterns will still<br>" +
                "show up in the copied file tree, but their content will never be included.<br>" +
                "One pattern per line. '*' and '?' wildcards are supported, matching is case-insensitive.<br>" +
                "Example: <code>.env</code>, <code>.env.*</code>, <code>*.pem</code>, <code>secrets/*</code></html>"
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
