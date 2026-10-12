package dev.daubney.scrobbled.data.recognition

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeRecognizerTest {
    private val clip = AudioClip(ByteArray(16_000), 8000)

    @Test
    fun clipReportsItsLength() = assertEquals(1.0, clip.seconds, 0.001)

    @Test
    fun cyclesSongsAndMissesEveryFourthCall() = runTest {
        val r = FakeRecognizer(latencyMs = 0)
        val results = List(8) { r.recognize(clip) }
        assertEquals("Reckoner", results[0]?.track)
        assertEquals("Roygbiv", results[1]?.track)
        assertEquals("Glory Box", results[2]?.track)
        assertNull(results[3])
        assertEquals("Reckoner", results[4]?.track)
        assertNull(results[7])
    }
}
