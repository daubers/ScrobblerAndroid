package dev.daubney.scrobbled.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.scrobble.TrackDraft
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

data class AddState(
    val artist: String = "",
    val track: String = "",
    val album: String = "",
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
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(AddState())
    val state: StateFlow<AddState> = _state.asStateFlow()

    private fun edit(f: AddState.() -> AddState) = _state.update { it.f().copy(error = null, message = null) }

    fun onArtist(v: String) = edit { copy(artist = v) }
    fun onTrack(v: String) = edit { copy(track = v) }
    fun onAlbum(v: String) = edit { copy(album = v) }
    fun onWhen(v: WhenOption) = edit { copy(whenOption = v) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val draft = TrackDraft(
            artist = s.artist, track = s.track, album = s.album,
            playedAt = clock().minus(s.whenOption.ago),
        )
        draft.validate(clock())?.let { err -> _state.update { it.copy(error = err) }; return }
        _state.update { it.copy(submitting = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                scrobbles.submit(draft)
                // Keep the time choice (adding several older tracks in a row is common), clear the rest.
                _state.value = AddState(whenOption = s.whenOption, message = "Scrobbled ${draft.track.trim()}")
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, error = e.message ?: "Something went wrong") }
            }
        }
    }
}
