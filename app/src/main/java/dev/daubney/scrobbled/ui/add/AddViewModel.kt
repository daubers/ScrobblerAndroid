package dev.daubney.scrobbled.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.scrobble.TrackDraft
import dev.daubney.scrobbled.data.search.SearchException
import dev.daubney.scrobbled.data.search.TrackResult
import dev.daubney.scrobbled.data.search.TrackSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** When the track was played, relative to the moment of submitting. */
enum class WhenOption(val label: String, val ago: Duration) {
    Now("Now", Duration.ZERO),
    Minutes30("30 min ago", Duration.ofMinutes(30)),
    Hour1("1 h ago", Duration.ofHours(1)),
    Hours3("3 h ago", Duration.ofHours(3)),
    Yesterday("Yesterday", Duration.ofHours(24)),
}

enum class AddTab { Search, Manual }

data class AddState(
    val tab: AddTab = AddTab.Search,
    // Search
    val query: String = "",
    val results: List<TrackResult> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    /** True once a search has returned, so "no results" isn't shown before the first search. */
    val searched: Boolean = false,
    // The editable track (filled by hand or from a search result)
    val artist: String = "",
    val track: String = "",
    val album: String = "",
    val mbid: String = "",
    val durationSeconds: Int? = null,
    val whenOption: WhenOption = WhenOption.Now,
    val submitting: Boolean = false,
    val error: String? = null,
    /** Set after a successful submit, e.g. "Scrobbled Reckoner". */
    val message: String? = null,
) {
    val canSubmit get() = !submitting && artist.isNotBlank() && track.isNotBlank()
}

class AddViewModel(
    private val scrobbles: ScrobbleRepository,
    private val search: TrackSearch,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(AddState())
    val state: StateFlow<AddState> = _state.asStateFlow()
    private var searchJob: Job? = null

    private fun edit(f: AddState.() -> AddState) = _state.update { it.f().copy(error = null, message = null) }

    fun onTab(t: AddTab) = _state.update { it.copy(tab = t) }
    fun onQuery(v: String) = _state.update { it.copy(query = v) }

    // The MusicBrainz id and length describe a specific recording, so they no longer apply once the
    // user changes which song this is.
    fun onArtist(v: String) = edit { copy(artist = v, mbid = "", durationSeconds = null) }
    fun onTrack(v: String) = edit { copy(track = v, mbid = "", durationSeconds = null) }
    fun onAlbum(v: String) = edit { copy(album = v) }
    fun onWhen(v: WhenOption) = edit { copy(whenOption = v) }

    fun search() {
        val q = _state.value.query
        if (q.isBlank() || _state.value.searching) return
        _state.update { it.copy(searching = true, searchError = null) }
        searchJob = viewModelScope.launch {
            try {
                val results = search.search(q)
                _state.update { it.copy(results = results, searching = false, searched = true) }
            } catch (e: SearchException) {
                _state.update { it.copy(searching = false, searchError = e.message) }
            }
        }
    }

    /** Copies a search result into the form so it can be corrected before submitting. */
    fun pick(r: TrackResult) = edit {
        copy(
            tab = AddTab.Manual, artist = r.artist, track = r.track, album = r.album.orEmpty(),
            mbid = r.mbid, durationSeconds = r.durationSeconds,
        )
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val draft = TrackDraft(
            artist = s.artist, track = s.track, album = s.album,
            durationSeconds = s.durationSeconds, mbid = s.mbid,
            playedAt = clock().minus(s.whenOption.ago),
        )
        draft.validate(clock())?.let { err -> _state.update { it.copy(error = err) }; return }
        _state.update { it.copy(submitting = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                scrobbles.submit(draft)
                // Keep the time choice and search (adding several tracks in a row is common), clear the form.
                _state.update {
                    it.copy(
                        artist = "", track = "", album = "", mbid = "", durationSeconds = null,
                        submitting = false, message = "Scrobbled ${draft.track.trim()}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, error = e.message ?: "Something went wrong") }
            }
        }
    }
}
