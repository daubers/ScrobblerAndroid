package dev.daubney.scrobbled.data.api

/** A track to scrobble or announce as now playing. [timestamp] is Unix seconds (scrobbles only). */
data class ScrobbleTrack(
    val artist: String,
    val track: String,
    val timestamp: Long? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationSeconds: Int? = null,
    val trackNumber: Int? = null,
    val mbid: String? = null,
)

data class Session(val username: String, val key: String)

data class ScrobbleResult(val accepted: Int, val ignored: Int)

/** An error reported by the server (Last.fm error code) or a transport/parse failure (code -1). */
class LastFmException(val code: Int, message: String) : Exception(message) {
    /** The session key was rejected; the user must re-authenticate. */
    val isInvalidSession get() = code == 9
}
