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
import dev.daubney.scrobbled.data.recognition.AcrCloudRecognizer
import dev.daubney.scrobbled.data.recognition.FakeRecognizer
import dev.daubney.scrobbled.data.recognition.KeystoreRecognitionConfigStore
import dev.daubney.scrobbled.data.recognition.RecognitionSettings
import dev.daubney.scrobbled.data.recognition.SwitchingRecognizer
import kotlinx.coroutines.flow.MutableSharedFlow
import dev.daubney.scrobbled.data.recognition.Recognizer
import dev.daubney.scrobbled.ui.AppServices

/** Hand-rolled service locator; small enough that a DI framework isn't worth it yet. */
class ScrobbledApplication : Application() {
    val auth: AuthRepository by lazy { DefaultAuthRepository(KeystoreCredentialStore(this)) }
    /** Emits when the server rejects our stored token, so the UI can send the user back to sign-in. */
    val sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val history: HistoryRepository by lazy { DefaultHistoryRepository(auth, onSessionExpired = { sessionExpired.tryEmit(Unit) }) }
    private val database by lazy { Room.databaseBuilder(this, AppDatabase::class.java, "scrobbled.db").build() }
    val queue: QueueStore by lazy { RoomQueueStore(database.queueDao()) }
    /** Asks WorkManager to send anything queued; used after signing back in. */
    fun scheduleFlush() = scheduler.schedule()

    private val scheduler: FlushScheduler by lazy { WorkManagerFlushScheduler(this) }
    val queueFlusher by lazy { QueueFlusher(queue, auth) }
    val scrobbles: ScrobbleRepository by lazy { DefaultScrobbleRepository(auth, queue, scheduler) }
    val trackSearch: TrackSearch by lazy { MusicBrainzSearch() }
    val recorder: AudioRecorder by lazy { AudioRecordRecorder() }

    val recognitionSettings by lazy { RecognitionSettings(KeystoreRecognitionConfigStore(this)) }

    // ACRCloud once keys are saved in Settings; until then a fake so the Listen flow can still be tried.
    val recognizer: Recognizer by lazy {
        SwitchingRecognizer(recognitionSettings, AcrCloudRecognizer({ recognitionSettings.config.value }), FakeRecognizer())
    }

    val services by lazy { AppServices(history, queue.observeCount(), scrobbles, trackSearch, recorder, recognizer, recognitionSettings, queue) }

    override fun onCreate() {
        super.onCreate()
        // Anything left over from a previous run (app killed while offline) gets another go.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (queue.count() > 0) scheduler.schedule()
        }
    }
}
