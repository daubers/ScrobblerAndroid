package dev.daubney.scrobbled.data.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TrackDraftTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private fun draft(playedAt: Instant = now, artist: String = "A", track: String = "T") =
        TrackDraft(artist = artist, track = track, playedAt = playedAt)

    @Test fun validDraftHasNoError() = assertNull(draft().validate(now))
    @Test fun blankArtistRejected() = assertEquals("Enter an artist", draft(artist = "  ").validate(now))
    @Test fun blankTrackRejected() = assertEquals("Enter a track title", draft(track = "").validate(now))

    @Test
    fun fiveMinutesAheadAllowedButNotMore() {
        assertNull(draft(now.plus(Duration.ofMinutes(5))).validate(now))
        assertNotNull(draft(now.plus(Duration.ofMinutes(6))).validate(now))
    }

    @Test
    fun fourteenDaysOldAllowedButNotMore() {
        assertNull(draft(now.minus(Duration.ofDays(14))).validate(now))
        assertNotNull(draft(now.minus(Duration.ofDays(14)).minusSeconds(1)).validate(now))
    }

    @Test
    fun toScrobbleTrackTrimsAndOmitsBlanks() {
        val t = TrackDraft(
            artist = " Björk ", track = " Army of Me ", album = "  ", albumArtist = "", mbid = " ",
            durationSeconds = 235, playedAt = now,
        ).toScrobbleTrack()
        assertEquals("Björk", t.artist)
        assertEquals("Army of Me", t.track)
        assertNull(t.album)
        assertNull(t.albumArtist)
        assertNull(t.mbid)
        assertEquals(235, t.durationSeconds)
        assertEquals(now.epochSecond, t.timestamp)
    }
}
