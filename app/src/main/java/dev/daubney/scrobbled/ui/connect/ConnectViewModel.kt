package dev.daubney.scrobbled.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.auth.ConnectException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
) {
    val canSubmit get() = !busy && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
}

class ConnectViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        ConnectState(serverUrl = auth.current()?.serverUrl.orEmpty(), connected = auth.current() != null),
    )
    val state: StateFlow<ConnectState> = _state.asStateFlow()

    fun onServerUrl(v: String) = _state.update { it.copy(serverUrl = v, error = null) }
    fun onUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun connect() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                auth.connect(s.serverUrl, s.username.trim(), s.password)
                _state.update { it.copy(busy = false, password = "", connected = true) }
            } catch (e: ConnectException) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    /** The server rejected our token: sign out but keep the server and username so only the password is needed. */
    fun onSessionExpired() {
        val c = auth.current()
        auth.disconnect()
        _state.value = ConnectState(
            serverUrl = c?.serverUrl ?: _state.value.serverUrl,
            username = c?.username ?: _state.value.username,
            error = "Your session expired. Sign in again.",
        )
    }

    fun disconnect() {
        auth.disconnect()
        _state.update { it.copy(connected = false, password = "") }
    }
}
