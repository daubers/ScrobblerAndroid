package dev.daubney.scrobbled.data.recognition

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AcrCloudRecognizerTest {
    private lateinit var server: MockWebServer
    private var config: RecognitionConfig? = RecognitionConfig("unused.example", "accesskey123", "s3cr3t-äö")
    private lateinit var recognizer: AcrCloudRecognizer
    private val clip = AudioClip(ByteArray(160) { it.toByte() }, 8000)

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        recognizer = AcrCloudRecognizer({ config }, baseUrlFor = { server.url("/").toString().trimEnd('/') }, nowSeconds = { 1_700_000_000 })
    }
    @After fun tearDown() = server.shutdown()

    private fun respond(body: String) = server.enqueue(MockResponse().setBody(body))

    @Test
    fun signatureMatchesReferenceValue() {
        // Expected value computed independently with Python's hmac/hashlib/base64.
        assertEquals("jy7SsBkRtn2iahO5ebTbRIVPXhI=", AcrCloudRecognizer.sign("accesskey123", "s3cr3t-äö", "1700000000"))
    }

    @Test
    fun wavHasCanonicalHeader() {
        val wav = clip.toWav()
        assertEquals(44 + 160, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("data", String(wav, 36, 4))
        fun le32(o: Int) = (wav[o].toInt() and 0xFF) or ((wav[o + 1].toInt() and 0xFF) shl 8) or
            ((wav[o + 2].toInt() and 0xFF) shl 16) or ((wav[o + 3].toInt() and 0xFF) shl 24)
        assertEquals(36 + 160, le32(4))
        assertEquals(8000, le32(24))
        assertEquals(16_000, le32(28))
        assertEquals(160, le32(40))
    }

    @Test
    fun successMapsTrack() = runTest {
        respond("""{"status":{"msg":"Success","code":0,"version":"1.0"},"metadata":{"music":[
            {"title":"Reckoner","duration_ms":290000,"artists":[{"name":"Radiohead"}],"album":{"name":"In Rainbows"},
             "external_metadata":{"musicbrainz":{"track":{"id":"mb-123"}}}}]}}""")
        val r = recognizer.recognize(clip)!!
        assertEquals("Radiohead", r.artist)
        assertEquals("Reckoner", r.track)
        assertEquals("In Rainbows", r.album)
        assertEquals(290, r.durationSeconds)
        assertEquals("mb-123", r.mbid)
    }

    @Test
    fun missingOptionalFieldsAreFine() = runTest {
        respond("""{"status":{"code":0},"metadata":{"music":[{"title":"Song","artists":[{"name":"A"},{"name":"B"}]}]}}""")
        val r = recognizer.recognize(clip)!!
        assertEquals("A, B", r.artist)
        assertNull(r.album)
        assertNull(r.durationSeconds)
        assertEquals("", r.mbid)
    }

    @Test
    fun musicBrainzIdAsArrayIsAccepted() = runTest {
        respond("""{"status":{"code":0},"metadata":{"music":[{"title":"S","artists":[{"name":"A"}],
            "external_metadata":{"musicbrainz":[{"track":{"id":"mb-9"}}]}}]}}""")
        assertEquals("mb-9", recognizer.recognize(clip)!!.mbid)
    }

    @Test
    fun noMatchReturnsNull() = runTest {
        respond("""{"status":{"msg":"No result","code":1001,"version":"1.0"}}""")
        assertNull(recognizer.recognize(clip))
    }

    @Test
    fun silenceCannotBeFingerprintedAndCountsAsNoMatch() = runTest {
        respond("""{"status":{"msg":"Can't generate fingerprint","code":2004}}""")
        assertNull(recognizer.recognize(clip))
    }

    @Test
    fun badKeysGiveActionableMessage() = runTest {
        respond("""{"status":{"msg":"invalid access key","code":3001}}""")
        try { recognizer.recognize(clip); fail() } catch (e: ListenException) {
            assertEquals("ACRCloud rejected your keys. Check them in Settings.", e.message)
        }
    }

    @Test
    fun limitReachedIsReported() = runTest {
        respond("""{"status":{"msg":"limit exceeded","code":3003}}""")
        try { recognizer.recognize(clip); fail() } catch (e: ListenException) {
            assertEquals("ACRCloud request limit reached.", e.message)
        }
    }

    @Test
    fun unknownErrorCodeIncludesServiceMessage() = runTest {
        respond("""{"status":{"msg":"Something odd","code":4000}}""")
        try { recognizer.recognize(clip); fail() } catch (e: ListenException) {
            assertEquals("ACRCloud error 4000: Something odd", e.message)
        }
    }

    @Test
    fun garbageResponseIsReported() = runTest {
        respond("<html>")
        try { recognizer.recognize(clip); fail() } catch (e: ListenException) {
            assertEquals("Unexpected response from ACRCloud", e.message)
        }
    }

    @Test
    fun unreachableServiceIsReported() = runTest {
        server.shutdown()
        try { recognizer.recognize(clip); fail() } catch (e: ListenException) {
            assertTrue(e.message!!.startsWith("Couldn't reach ACRCloud"))
        }
    }

    @Test
    fun missingConfigAsksUserToSetItUp() = runTest {
        config = null
        try { recognizer.recognize(clip); fail() } catch (e: ListenException) {
            assertTrue(e.message!!.contains("Settings"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun requestIsSignedMultipartToIdentifyEndpoint() = runTest {
        respond("""{"status":{"code":1001}}""")
        recognizer.recognize(clip)
        val req = server.takeRequest()
        assertEquals("/v1/identify", req.path)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = req.body.readUtf8()
        for (part in listOf("access_key", "data_type", "signature_version", "signature", "timestamp", "sample_bytes", "sample")) {
            assertTrue("missing part $part", body.contains("name=\"$part\""))
        }
        assertTrue(body.contains("jy7SsBkRtn2iahO5ebTbRIVPXhI="))
        assertTrue(body.contains("1700000000"))
        assertTrue(body.contains("RIFF"))
    }
}
