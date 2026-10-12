package dev.daubney.scrobbled.data.search

data class TrackResult(
    val mbid: String,
    val artist: String,
    val track: String,
    val album: String?,
    val durationSeconds: Int?,
)

interface TrackSearch {
    /** Returns matches best-first; empty for a blank query. Throws [SearchException] with a user-facing message. */
    suspend fun search(query: String): List<TrackResult>
}

class SearchException(message: String) : Exception(message)
