package dev.daubney.scrobbled.data.scrobble

import dev.daubney.scrobbled.data.api.LastFmClient
import dev.daubney.scrobbled.data.api.LastFmException
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.history.SessionExpiredException

interface ScrobbleRepository {
    /** Submits one scrobble; throws if it can't be accepted. */
    suspend fun submit(draft: TrackDraft)
}

class DefaultScrobbleRepository(
    private val auth: AuthRepository,
    private val lastFmFor: (url: String, key: String, secret: String) -> LastFmClient =
        { url, key, secret -> LastFmClient(url, key, secret) },
) : ScrobbleRepository {

    override suspend fun submit(draft: TrackDraft) {
        draft.validate()?.let { throw IllegalArgumentException(it) }
        val creds = auth.current() ?: throw SessionExpiredException()
        val result = try {
            lastFmFor(creds.serverUrl, creds.apiKey, creds.apiSecret)
                .scrobble(creds.sessionKey, listOf(draft.toScrobbleTrack()))
        } catch (e: LastFmException) {
            throw if (e.isInvalidSession) SessionExpiredException() else e
        }
        // The server reports bad or stale tracks as "ignored" rather than as an error.
        if (result.accepted < 1) throw IllegalStateException("The server didn't accept this scrobble")
    }
}
