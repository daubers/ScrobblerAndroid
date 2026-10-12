package dev.daubney.scrobbled.data.recognition

import dev.daubney.scrobbled.data.search.TrackResult
import kotlinx.coroutines.delay

/**
 * Stand-in for a real recognition service (ACRCloud) until its keys are available. It ignores the
 * audio and cycles through a few known songs, reporting "no match" every fourth call so the
 * not-found path can be exercised too.
 */
class FakeRecognizer(private val latencyMs: Long = 1_500) : Recognizer {
    private var calls = 0

    override suspend fun recognize(clip: AudioClip): TrackResult? {
        delay(latencyMs)
        val n = calls++
        if (n % 4 == 3) return null
        return songs[n % 4 % songs.size]
    }

    private companion object {
        val songs = listOf(
            TrackResult("", "Radiohead", "Reckoner", "In Rainbows", 290),
            TrackResult("", "Boards of Canada", "Roygbiv", "Music Has the Right to Children", 151),
            TrackResult("", "Portishead", "Glory Box", "Dummy", 305),
        )
    }
}
