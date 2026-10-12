package dev.daubney.scrobbled

import android.app.Application
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.auth.DefaultAuthRepository
import dev.daubney.scrobbled.data.auth.KeystoreCredentialStore
import dev.daubney.scrobbled.data.history.DefaultHistoryRepository
import dev.daubney.scrobbled.data.history.HistoryRepository
import dev.daubney.scrobbled.data.scrobble.DefaultScrobbleRepository
import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.search.MusicBrainzSearch
import dev.daubney.scrobbled.data.search.TrackSearch

/** Hand-rolled service locator; small enough that a DI framework isn't worth it yet. */
class ScrobbledApplication : Application() {
    val auth: AuthRepository by lazy { DefaultAuthRepository(KeystoreCredentialStore(this)) }
    val history: HistoryRepository by lazy { DefaultHistoryRepository(auth) }
    val scrobbles: ScrobbleRepository by lazy { DefaultScrobbleRepository(auth) }
    val trackSearch: TrackSearch by lazy { MusicBrainzSearch() }
}
