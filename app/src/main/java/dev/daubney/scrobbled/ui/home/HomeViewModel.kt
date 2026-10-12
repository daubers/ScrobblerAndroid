package dev.daubney.scrobbled.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daubney.scrobbled.data.history.HistoryRepository
import dev.daubney.scrobbled.data.history.NowPlaying
import dev.daubney.scrobbled.data.history.RecentScrobble
import dev.daubney.scrobbled.data.history.Summary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val nowPlaying: NowPlaying? = null,
    val summary: Summary? = null,
    val recent: List<RecentScrobble> = emptyList(),
    /** Scrobbles saved on this device that haven't reached the server yet. */
    val pending: Int = 0,
    val loaded: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val history: HistoryRepository,
    pending: Flow<Int> = emptyFlow(),
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch { pending.collect { n -> _state.update { it.copy(pending = n) } } }
    }

    /** Full refresh: summary, latest scrobbles and now playing. Each part fails independently. */
    fun refresh() {
        viewModelScope.launch {
            var error: String? = null
            suspend fun <T> part(block: suspend () -> T, apply: (T) -> Unit) {
                try { apply(block()) } catch (e: Exception) { error = e.message ?: "Something went wrong" }
            }
            part({ history.summary() }) { s -> _state.update { it.copy(summary = s) } }
            part({ history.recent(1, RECENT_COUNT).items }) { r -> _state.update { it.copy(recent = r) } }
            part({ history.nowPlaying() }) { n -> _state.update { it.copy(nowPlaying = n) } }
            _state.update { it.copy(loaded = true, error = error) }
        }
    }

    /** Cheap poll for the now-playing card; keeps the last known value on failure. */
    fun refreshNowPlaying() {
        viewModelScope.launch {
            try {
                val np = history.nowPlaying()
                _state.update { it.copy(nowPlaying = np) }
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val RECENT_COUNT = 5
    }
}
