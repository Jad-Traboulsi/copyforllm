package com.aykoo.copyforllm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persists the list of filename/path patterns matched against files and folders.
 * A pattern matching a file (e.g. ".env", "*.pem") keeps that file in the tree
 * (so its location is still visible) but leaves it out of the copied content
 * entirely. A pattern matching a folder (e.g. "node_modules", "secrets") excludes
 * that whole folder, and everything inside it, from both the tree and the copy.
 */
@Service(Service.Level.APP)
@State(name = "CopyForLlmSettings", storages = [Storage("copyforllm.xml")])
class CopyForLlmSettingsState : PersistentStateComponent<CopyForLlmSettingsState.State> {

    class State {
        var excludedPatterns: MutableList<String> = mutableListOf(".env", "node_modules")
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
