package dev.daubney.scrobbled.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daubney.scrobbled.data.history.HistoryRepository
import dev.daubney.scrobbled.data.history.RecentScrobble
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryState(
    val items: List<RecentScrobble> = emptyList(),
    val nextPage: Int = 1,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val empty get() = items.isEmpty() && !hasMore && error == null
}

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun loadNext() {
        val s = _state.value
        if (s.loading || s.refreshing || !s.hasMore) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = history.recent(s.nextPage)
                _state.update {
                    // New scrobbles shift later pages, so the same row can appear twice: dedupe by key.
                    val seen = it.items.mapTo(HashSet()) { r -> r.key }
                    it.copy(
                        items = it.items + page.items.filter { r -> seen.add(r.key) },
                        nextPage = page.page + 1, hasMore = page.hasMore, loading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                val page = history.recent(1)
                _state.update {
                    HistoryState(items = page.items, nextPage = page.page + 1, hasMore = page.hasMore)
                }
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, error = e.message ?: "Something went wrong") }
            }
        }
    }
}
