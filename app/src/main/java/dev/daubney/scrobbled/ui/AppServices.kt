package dev.daubney.scrobbled.ui

import dev.daubney.scrobbled.data.history.HistoryRepository
import dev.daubney.scrobbled.data.recognition.AudioRecorder
import dev.daubney.scrobbled.data.recognition.Recognizer
import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.search.TrackSearch
import kotlinx.coroutines.flow.Flow

/** Everything the screens need from the data layer, bundled so navigation doesn't take a parameter per service. */
class AppServices(
    val history: HistoryRepository,
    val pendingScrobbles: Flow<Int>,
    val scrobbles: ScrobbleRepository,
    val trackSearch: TrackSearch,
    val recorder: AudioRecorder,
    val recognizer: Recognizer,
)
