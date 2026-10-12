package dev.daubney.scrobbled.data.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionConfigTest {
    @Test fun completeNeedsAllThreeFields() {
        assertTrue(RecognitionConfig("h", "k", "s").isComplete)
        assertFalse(RecognitionConfig("h", "", "s").isComplete)
        assertFalse(RecognitionConfig(" ", "k", "s").isComplete)
        assertFalse(RecognitionConfig("h", "k", "").isComplete)
    }

    @Test fun normalizedTidiesPastedValues() {
        val c = RecognitionConfig(" https://identify-eu-west-1.acrcloud.com/ ", " key ", " secret\n").normalized()
        assertEquals(RecognitionConfig("identify-eu-west-1.acrcloud.com", "key", "secret"), c)
    }
}
