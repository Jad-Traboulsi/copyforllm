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
 * patterns matched against files and folders (e.g. ".env", "*.pem", "node_modules",
 * "secrets"). A match - whether a single file or a whole folder - always stays
 * visible in the tree; in the content section it's noted as skipped, but its
 * actual content is never included.
 */
class CopyForLlmConfigurable : Configurable {

    private var textArea: JBTextArea? = null

    override fun getDisplayName(): String = "CopyForLlm"

    override fun createComponent(): JComponent {
        val area = JBTextArea(10, 40)
        area.lineWrap = false
        textArea = area

        val info = JBLabel(
            "<html>A match - whether a single file or a whole folder - always stays visible in the tree;<br>" +
                "in the content section it's noted as skipped, but its actual content is never included.<br>" +
                "One pattern per line. '*' and '?' wildcards are supported, matching is case-insensitive.<br>" +
                "Example: <code>.env</code>, <code>*.pem</code>, <code>node_modules</code>, <code>secrets</code></html>"
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
