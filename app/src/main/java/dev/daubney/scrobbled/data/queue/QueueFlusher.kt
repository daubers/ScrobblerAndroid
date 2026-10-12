package dev.daubney.scrobbled.data.queue

import dev.daubney.scrobbled.data.api.LastFmClient
import dev.daubney.scrobbled.data.api.LastFmException
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.scrobble.TrackDraft
import java.time.Instant

sealed interface FlushResult {
    /** Queue is empty. [sent] were accepted; [dropped] were refused by the server or too old to ever be accepted. */
    data class Done(val sent: Int, val dropped: Int) : FlushResult

    /** A temporary failure (offline, server busy): try again later. */
    data object Retry : FlushResult

    /** Needs the user: signed out, session rejected, or an unexpected server error. Items are kept. */
    data class Blocked(val reason: String) : FlushResult
}

/** Sends queued scrobbles in batches of up to 50, oldest first. Safe to re-run: the server dedupes retries. */
class QueueFlusher(
    private val store: QueueStore,
    private val auth: AuthRepository,
    private val lastFmFor: (url: String, key: String, secret: String) -> LastFmClient =
        { url, key, secret -> LastFmClient(url, key, secret) },
    private val now: () -> Instant = Instant::now,
) {
    suspend fun flush(): FlushResult {
        var sent = 0
        var dropped = 0
        while (true) {
            val batch = store.pending(BATCH)
            if (batch.isEmpty()) return FlushResult.Done(sent, dropped)

            // The server refuses plays older than 14 days, so those can never succeed.
            val cutoff = now().minus(TrackDraft.MAX_AGE)
            val (expired, sendable) = batch.partition { it.draft.playedAt.isBefore(cutoff) }
            if (expired.isNotEmpty()) {
                store.remove(expired.map { it.id })
                dropped += expired.size
            }
            if (sendable.isEmpty()) continue

            val creds = auth.current() ?: return FlushResult.Blocked("Not signed in")
            val result = try {
                lastFmFor(creds.serverUrl, creds.apiKey, creds.apiSecret)
                    .scrobble(creds.sessionKey, sendable.map { it.draft.toScrobbleTrack() })
            } catch (e: LastFmException) {
                return when {
                    e.isInvalidSession -> FlushResult.Blocked("Session expired")
                    e.isTransient -> FlushResult.Retry
                    else -> FlushResult.Blocked(e.message.orEmpty())
                }
            }
            store.remove(sendable.map { it.id })
            sent += result.accepted
            dropped += result.ignored
        }
    }

    private companion object {
        const val BATCH = 50
    }
}
