package dev.daubney.scrobbled.ui.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daubney.scrobbled.data.recognition.AudioRecorder
import dev.daubney.scrobbled.data.recognition.ListenException
import dev.daubney.scrobbled.data.recognition.Recognizer
import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.scrobble.SubmitResult
import dev.daubney.scrobbled.data.scrobble.TrackDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

enum class ListenPhase { Idle, Recording, Identifying, Confirm, NoMatch }

data class ListenState(
    val phase: ListenPhase = ListenPhase.Idle,
    /** Recording progress, 0..1. */
    val progress: Float = 0f,
    // What was heard, editable before it is submitted
    val artist: String = "",
    val track: String = "",
    val album: String = "",
    val mbid: String = "",
    val durationSeconds: Int? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val canSubmit get() = !busy && artist.isNotBlank() && track.isNotBlank()
}

class ListenViewModel(
    private val recorder: AudioRecorder,
    private val recognizer: Recognizer,
    private val scrobbles: ScrobbleRepository,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var job: Job? = null
    /** When the clip started, which is when the song was playing (not when the user confirms it). */
    private var heardAt: Instant = clock()

    /** Call once the microphone permission is granted. */
    fun start() {
        if (_state.value.phase == ListenPhase.Recording || _state.value.phase == ListenPhase.Identifying) return
        _state.value = ListenState(phase = ListenPhase.Recording)
        heardAt = clock()
        job = viewModelScope.launch {
            try {
                val clip = recorder.record(LISTEN_SECONDS) { p -> _state.update { it.copy(progress = p) } }
                _state.update { it.copy(phase = ListenPhase.Identifying, progress = 1f) }
                val found = recognizer.recognize(clip)
                _state.update {
                    if (found == null) it.copy(phase = ListenPhase.NoMatch)
                    else it.copy(
                        phase = ListenPhase.Confirm, artist = found.artist, track = found.track,
                        album = found.album.orEmpty(), mbid = found.mbid, durationSeconds = found.durationSeconds,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ListenException) {
                fail(e.message)
            } catch (e: Exception) {
                fail(e.message ?: "Something went wrong while listening")
            }
        }
    }

    fun onPermissionDenied() = _state.update {
        ListenState(error = "Microphone permission is needed to listen for music.")
    }

    /** Stops recording or identifying and returns to the start. */
    fun cancel() {
        job?.cancel()
        _state.value = ListenState()
    }

    // The MusicBrainz id and length describe the recognised recording, so drop them if the song is changed.
    fun onArtist(v: String) = edit { copy(artist = v, mbid = "", durationSeconds = null) }
    fun onTrack(v: String) = edit { copy(track = v, mbid = "", durationSeconds = null) }
    fun onAlbum(v: String) = edit { copy(album = v) }

    fun scrobble() = submit { draft ->
        when (scrobbles.submit(draft)) {
            SubmitResult.Sent -> "Scrobbled ${draft.track.trim()}"
            SubmitResult.Queued -> "Saved ${draft.track.trim()}. It will be sent when you're back online."
        }
    }

    fun setNowPlaying() = submit { draft ->
        scrobbles.setNowPlaying(draft)
        "Now playing: ${draft.track.trim()}"
    }

    private fun draft(s: ListenState) = TrackDraft(
        artist = s.artist, track = s.track, album = s.album,
        durationSeconds = s.durationSeconds, mbid = s.mbid, playedAt = heardAt,
    )

    private fun submit(action: suspend (TrackDraft) -> String) {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val message = action(draft(s))
                _state.value = ListenState(message = message)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Something went wrong") }
            }
        }
    }

    private fun edit(f: ListenState.() -> ListenState) = _state.update { it.f().copy(error = null) }

    private fun fail(message: String?) {
        _state.value = ListenState(error = message ?: "Something went wrong while listening")
    }

    companion object {
        const val LISTEN_SECONDS = 10
    }
}
