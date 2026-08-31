package com.aykoo.copyforllm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persists the list of filename/path patterns whose content should never be copied
 * (e.g. ".env", "*.pem"). Matching files still appear in the tree, but their body is
 * replaced with a placeholder, the same way binary/empty files are already handled.
 */
@Service(Service.Level.APP)
@State(name = "CopyForLlmSettings", storages = [Storage("copyforllm.xml")])
class CopyForLlmSettingsState : PersistentStateComponent<CopyForLlmSettingsState.State> {

    class State {
        var excludedPatterns: MutableList<String> = mutableListOf(".env")
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

    companion object {
        fun getInstance(): CopyForLlmSettingsState =
            ApplicationManager.getApplication().getService(CopyForLlmSettingsState::class.java)
    }
}
