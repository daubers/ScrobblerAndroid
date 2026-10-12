package dev.daubney.scrobbled.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daubney.scrobbled.data.queue.QueueStore
import dev.daubney.scrobbled.data.recognition.RecognitionConfig
import dev.daubney.scrobbled.data.recognition.RecognitionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val serverUrl: String = "",
    val username: String = "",
    val host: String = "",
    val accessKey: String = "",
    val accessSecret: String = "",
    /** True when saved keys are in use; false means Listen runs on the demo recognizer. */
    val recognitionActive: Boolean = false,
    val keysMessage: String? = null,
    val keysError: String? = null,
    val pendingScrobbles: Int = 0,
    /** Shows the "discard unsent scrobbles?" confirmation. */
    val confirmingSignOut: Boolean = false,
)

class SettingsViewModel(
    serverUrl: String,
    username: String,
    private val recognition: RecognitionSettings,
    private val queue: QueueStore,
    private val signOut: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(
        recognition.config.value.let { c ->
            SettingsState(
                serverUrl = serverUrl, username = username,
                host = c?.host.orEmpty(), accessKey = c?.accessKey.orEmpty(), accessSecret = c?.accessSecret.orEmpty(),
                recognitionActive = !recognition.isDemo,
            )
        },
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch { queue.observeCount().collect { n -> _state.update { it.copy(pendingScrobbles = n) } } }
    }

    private fun edit(f: SettingsState.() -> SettingsState) = _state.update { it.f().copy(keysMessage = null, keysError = null) }
    fun onHost(v: String) = edit { copy(host = v) }
    fun onAccessKey(v: String) = edit { copy(accessKey = v) }
    fun onAccessSecret(v: String) = edit { copy(accessSecret = v) }

    /** All three blank clears the keys (back to demo mode); all three filled saves them; anything between is an error. */
    fun saveKeys() {
        val s = _state.value
        val config = RecognitionConfig(s.host, s.accessKey, s.accessSecret)
        when {
            s.host.isBlank() && s.accessKey.isBlank() && s.accessSecret.isBlank() -> {
                recognition.clear()
                _state.update { it.copy(recognitionActive = false, keysMessage = "Keys removed. Listen is in demo mode.", keysError = null) }
            }
            !config.isComplete ->
                _state.update { it.copy(keysError = "Fill in the host, access key and access secret.", keysMessage = null) }
            else -> {
                recognition.save(config)
                val saved = recognition.config.value!!
                _state.update {
                    it.copy(
                        host = saved.host, accessKey = saved.accessKey, accessSecret = saved.accessSecret,
                        recognitionActive = true, keysMessage = "Saved. Listen now uses ACRCloud.", keysError = null,
                    )
                }
            }
        }
    }

    fun requestSignOut() {
        if (_state.value.pendingScrobbles > 0) _state.update { it.copy(confirmingSignOut = true) } else signOut()
    }

    fun cancelSignOut() = _state.update { it.copy(confirmingSignOut = false) }

    /** Unsent scrobbles belong to this account, so they're discarded rather than risk sending them to the next one. */
    fun confirmSignOut() {
        viewModelScope.launch {
            queue.clear()
            signOut()
        }
    }
}
