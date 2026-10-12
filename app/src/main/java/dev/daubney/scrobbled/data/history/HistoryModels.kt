package dev.daubney.scrobbled.data.history

import java.time.Instant

data class NowPlaying(
    val artist: String,
    val track: String,
    val album: String?,
    val durationSeconds: Int?,
    val startedAt: Instant,
    val expiresAt: Instant,
)

data class RecentScrobble(
    val artist: String,
    val track: String,
    val album: String?,
    val playedAt: Instant,
) {
    /** Scrobbles are unique per (artist, track, playedAt) on the server. */
    val key get() = "$artist\u0000$track\u0000${playedAt.epochSecond}"
}

data class ScrobblePage(val items: List<RecentScrobble>, val page: Int, val totalPages: Int, val total: Int) {
    val hasMore get() = page < totalPages
}

data class Summary(val scrobbles: Int, val artists: Int, val tracks: Int, val firstScrobbleAt: Instant?)

class SessionExpiredException : Exception("Your session has expired. Please sign in again.")
