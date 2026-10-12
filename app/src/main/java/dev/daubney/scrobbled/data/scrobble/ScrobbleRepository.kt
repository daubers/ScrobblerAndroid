package dev.daubney.scrobbled.data.scrobble

import dev.daubney.scrobbled.data.api.LastFmClient
import dev.daubney.scrobbled.data.api.LastFmException
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.history.SessionExpiredException
import dev.daubney.scrobbled.data.queue.FlushScheduler
import dev.daubney.scrobbled.data.queue.QueueStore

enum class SubmitResult {
    /** The server accepted it. */
    Sent,

    /** Couldn't reach the server; saved and will be sent automatically when back online. */
    Queued,
}

interface ScrobbleRepository {
    /** Submits one scrobble. Throws if it can't be accepted and isn't worth retrying. */
    suspend fun submit(draft: TrackDraft): SubmitResult
}

class DefaultScrobbleRepository(
    private val auth: AuthRepository,
    private val queue: QueueStore,
    private val scheduler: FlushScheduler,
    private val lastFmFor: (url: String, key: String, secret: String) -> LastFmClient =
        { url, key, secret -> LastFmClient(url, key, secret) },
) : ScrobbleRepository {

    override suspend fun submit(draft: TrackDraft): SubmitResult {
        draft.validate()?.let { throw IllegalArgumentException(it) }
        val creds = auth.current() ?: throw SessionExpiredException()
        val result = try {
            lastFmFor(creds.serverUrl, creds.apiKey, creds.apiSecret)
                .scrobble(creds.sessionKey, listOf(draft.toScrobbleTrack()))
        } catch (e: LastFmException) {
            when {
                e.isInvalidSession -> throw SessionExpiredException()
                e.isTransient -> {
                    queue.add(draft)
                    scheduler.schedule()
                    return SubmitResult.Queued
                }
                else -> throw e
            }
        }
        // The server reports bad or stale tracks as "ignored" rather than as an error.
        if (result.accepted < 1) throw IllegalStateException("The server didn't accept this scrobble")
        // We're online, so this is a good moment to send anything saved earlier.
        if (queue.count() > 0) scheduler.schedule()
        return SubmitResult.Sent
    }
}
