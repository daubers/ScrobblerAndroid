package dev.daubney.scrobbled.data.recognition

import dev.daubney.scrobbled.data.search.TrackResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryConfigStore(var saved: RecognitionConfig? = null) : RecognitionConfigStore {
    override fun load() = saved
    override fun save(config: RecognitionConfig) { saved = config }
    override fun clear() { saved = null }
}

class SwitchingRecognizerTest {
    private val clip = AudioClip(ByteArray(2), 8000)
    private val real = object : Recognizer {
        override suspend fun recognize(clip: AudioClip) = TrackResult("", "Real", "Real song", null, null)
    }
    private val demo = object : Recognizer {
        override suspend fun recognize(clip: AudioClip) = TrackResult("", "Demo", "Demo song", null, null)
    }

    @Test
    fun usesDemoUntilKeysAreSaved() = runTest {
        val settings = RecognitionSettings(MemoryConfigStore())
        val r = SwitchingRecognizer(settings, real, demo)
        assertTrue(settings.isDemo)
        assertEquals("Demo song", r.recognize(clip)?.track)
        settings.save(RecognitionConfig("h", "k", "s"))
        assertFalse(settings.isDemo)
        assertEquals("Real song", r.recognize(clip)?.track)
    }

    @Test
    fun clearingKeysReturnsToDemo() = runTest {
        val settings = RecognitionSettings(MemoryConfigStore(RecognitionConfig("h", "k", "s")))
        val r = SwitchingRecognizer(settings, real, demo)
        assertEquals("Real song", r.recognize(clip)?.track)
        settings.clear()
        assertEquals("Demo song", r.recognize(clip)?.track)
        assertNull(settings.config.value)
    }

    @Test
    fun savedConfigIsNormalisedAndPersisted() {
        val store = MemoryConfigStore()
        val settings = RecognitionSettings(store)
        settings.save(RecognitionConfig(" https://host.example/ ", " k ", " s "))
        assertEquals(RecognitionConfig("host.example", "k", "s"), store.saved)
        assertEquals(RecognitionConfig("host.example", "k", "s"), settings.config.value)
    }

    @Test
    fun incompleteStoredConfigCountsAsDemo() {
        assertTrue(RecognitionSettings(MemoryConfigStore(RecognitionConfig("h", "", "s"))).isDemo)
    }
}
