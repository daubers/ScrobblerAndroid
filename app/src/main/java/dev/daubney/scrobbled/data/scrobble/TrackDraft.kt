package dev.daubney.scrobbled.data.scrobble

import dev.daubney.scrobbled.data.api.ScrobbleTrack
import java.time.Duration
import java.time.Instant

/**
 * An editable track about to be scrobbled. Every way of adding one (manual entry, MusicBrainz
 * search, audio recognition) ends up as a draft the user can correct before submitting.
 */
data class TrackDraft(
    val artist: String = "",
    val track: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val durationSeconds: Int? = null,
    val mbid: String = "",
    val playedAt: Instant = Instant.now(),
) {
    /** A user-facing problem with this draft, or null if it can be submitted. */
    fun validate(now: Instant = Instant.now()): String? = when {
        artist.isBlank() -> "Enter an artist"
        track.isBlank() -> "Enter a track title"
        playedAt.isAfter(now.plus(MAX_FUTURE)) -> "The play time can't be in the future"
        playedAt.isBefore(now.minus(MAX_AGE)) -> "The server only accepts plays from the last 14 days"
        else -> null
    }

    fun toScrobbleTrack() = ScrobbleTrack(
        artist = artist.trim(),
        track = track.trim(),
        timestamp = playedAt.epochSecond,
        album = album.trim().ifEmpty { null },
        albumArtist = albumArtist.trim().ifEmpty { null },
        durationSeconds = durationSeconds,
        mbid = mbid.trim().ifEmpty { null },
    )

    companion object {
        // Server limits (scrobbler/config.py): at most 14 days old and 5 minutes in the future.
        val MAX_AGE: Duration = Duration.ofDays(14)
        val MAX_FUTURE: Duration = Duration.ofMinutes(5)
    }
}
