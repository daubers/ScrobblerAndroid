package dev.daubney.scrobbled

import android.app.Application
import androidx.room.Room
import dev.daubney.scrobbled.data.queue.AppDatabase
import dev.daubney.scrobbled.data.queue.FlushScheduler
import dev.daubney.scrobbled.data.queue.QueueFlusher
import dev.daubney.scrobbled.data.queue.QueueStore
import dev.daubney.scrobbled.data.queue.RoomQueueStore
import dev.daubney.scrobbled.data.queue.WorkManagerFlushScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.auth.DefaultAuthRepository
import dev.daubney.scrobbled.data.auth.KeystoreCredentialStore
import dev.daubney.scrobbled.data.history.DefaultHistoryRepository
import dev.daubney.scrobbled.data.history.HistoryRepository
import dev.daubney.scrobbled.data.scrobble.DefaultScrobbleRepository
import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.search.MusicBrainzSearch
import dev.daubney.scrobbled.data.search.TrackSearch
import dev.daubney.scrobbled.data.recognition.AudioRecordRecorder
import dev.daubney.scrobbled.data.recognition.AudioRecorder
import dev.daubney.scrobbled.data.recognition.FakeRecognizer
import dev.daubney.scrobbled.data.recognition.Recognizer
import dev.daubney.scrobbled.ui.AppServices

/** Hand-rolled service locator; small enough that a DI framework isn't worth it yet. */
class ScrobbledApplication : Application() {
    val auth: AuthRepository by lazy { DefaultAuthRepository(KeystoreCredentialStore(this)) }
    val history: HistoryRepository by lazy { DefaultHistoryRepository(auth) }
    private val database by lazy { Room.databaseBuilder(this, AppDatabase::class.java, "scrobbled.db").build() }
    val queue: QueueStore by lazy { RoomQueueStore(database.queueDao()) }
    private val scheduler: FlushScheduler by lazy { WorkManagerFlushScheduler(this) }
    val queueFlusher by lazy { QueueFlusher(queue, auth) }
    val scrobbles: ScrobbleRepository by lazy { DefaultScrobbleRepository(auth, queue, scheduler) }
    val trackSearch: TrackSearch by lazy { MusicBrainzSearch() }
    val recorder: AudioRecorder by lazy { AudioRecordRecorder() }

    // TODO: swap for the ACRCloud recognizer once its host/keys are available.
    val recognizer: Recognizer by lazy { FakeRecognizer() }

    val services by lazy { AppServices(history, queue.observeCount(), scrobbles, trackSearch, recorder, recognizer) }

    override fun onCreate() {
        super.onCreate()
        // Anything left over from a previous run (app killed while offline) gets another go.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (queue.count() > 0) scheduler.schedule()
        }
    }
}
