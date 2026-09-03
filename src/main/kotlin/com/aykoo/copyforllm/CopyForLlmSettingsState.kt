package com.aykoo.copyforllm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persists the list of filename/path patterns matched against files and folders
 * (e.g. ".env", "*.pem", "node_modules", "secrets"). A match - whether a single
 * file or a whole folder - always stays visible in the tree; in the content
 * section it's noted as skipped, but its actual content is never included.
 */
@Service(Service.Level.APP)
@State(name = "CopyForLlmSettings", storages = [Storage("copyforllm.xml")])
class CopyForLlmSettingsState : PersistentStateComponent<CopyForLlmSettingsState.State> {

    class State {
        var excludedPatterns: MutableList<String> = mutableListOf(".env", "node_modules")
        var hideBinaryFilesInTree: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    var excludedPatterns: MutableList<String>
        get() = state.excludedPatterns
        set(value) {
            state.excludedPatterns = value
        }

    /**
     * Whether binary files are left out of the copied file tree. Their entry in the
     * content section is unaffected - that still names them and notes the skip.
     */
    var hideBinaryFilesInTree: Boolean
        get() = state.hideBinaryFilesInTree
        set(value) {
            state.hideBinaryFilesInTree = value
        }

    /** Appends the patterns that aren't configured yet, keeping the existing order. */
    fun addExcludedPatterns(patterns: Collection<String>) {
        val updated = state.excludedPatterns.toMutableList()
        patterns.map { it.trim() }
            .filter { it.isNotEmpty() && it !in updated }
            .forEach { updated.add(it) }
        state.excludedPatterns = updated
    }

    fun removeExcludedPatterns(patterns: Collection<String>) {
        val removed = patterns.map { it.trim() }.toSet()
        state.excludedPatterns = state.excludedPatterns.filterNot { it in removed }.toMutableList()
    }

    companion object {
        fun getInstance(): CopyForLlmSettingsState =
            ApplicationManager.getApplication().getService(CopyForLlmSettingsState::class.java)
    }
}
