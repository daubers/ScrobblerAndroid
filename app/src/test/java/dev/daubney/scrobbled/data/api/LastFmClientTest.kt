package dev.daubney.scrobbled.data.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class LastFmClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: LastFmClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = LastFmClient(server.url("/").toString(), apiKey = "key", secret = "secret")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun sentParams(): Map<String, String> {
        val req = server.takeRequest()
        assertEquals("/2.0/", req.path)
        assertEquals("POST", req.method)
        return req.body.readUtf8().split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
    }

    private fun assertSigned(p: Map<String, String>) =
        assertEquals(Signer.sign(p - "api_sig", "secret"), p["api_sig"])

    @Test
    fun getMobileSessionParsesSessionAndSignsRequest() = runTest {
        server.enqueue(MockResponse().setBody("""{"session":{"name":"alice","key":"abc123","subscriber":"0"}}"""))
        val session = client.getMobileSession("alice", "pw")
        assertEquals(Session("alice", "abc123"), session)
        val p = sentParams()
        assertEquals("auth.getMobileSession", p["method"])
        assertEquals("json", p["format"])
        assertEquals("key", p["api_key"])
        assertSigned(p)
    }

    @Test
    fun scrobbleSendsIndexedFieldsAndParsesCounts() = runTest {
        server.enqueue(MockResponse().setBody("""{"scrobbles":{"scrobble":{"track":{"#text":"x"}},"@attr":{"accepted":"2","ignored":"0"}}}"""))
        val result = client.scrobble(
            "sk1",
            listOf(
                ScrobbleTrack("Björk", "Army of Me", timestamp = 100, album = "Post", durationSeconds = 235, mbid = "m"),
                ScrobbleTrack("B", "T", timestamp = 200),
            ),
        )
        assertEquals(ScrobbleResult(2, 0), result)
        val p = sentParams()
        assertEquals("sk1", p["sk"])
        assertEquals("Björk", p["artist[0]"])
        assertEquals("100", p["timestamp[0]"])
        assertEquals("Post", p["album[0]"])
        assertEquals("235", p["duration[0]"])
        assertEquals("m", p["mbid[0]"])
        assertEquals("T", p["track[1]"])
        assertFalse(p.containsKey("album[1]"))
        assertSigned(p)
    }

    @Test
    fun updateNowPlayingSendsUnindexedFields() = runTest {
        server.enqueue(MockResponse().setBody("""{"nowplaying":{"track":{"corrected":"0","#text":"T"}}}"""))
        client.updateNowPlaying("sk1", ScrobbleTrack("A", "T", album = "Al"))
        val p = sentParams()
        assertEquals("track.updateNowPlaying", p["method"])
        assertEquals("A", p["artist"])
        assertEquals("Al", p["album"])
        assertSigned(p)
    }

    @Test
    fun serverErrorBecomesLastFmException() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":9,"message":"Invalid session key"}"""))
        try {
            client.scrobble("bad", listOf(ScrobbleTrack("A", "T", timestamp = 1)))
            fail("expected exception")
        } catch (e: LastFmException) {
            assertEquals(9, e.code)
            assertTrue(e.isInvalidSession)
        }
    }

    @Test
    fun nonJsonResponseIsReportedNotCrashed() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        try {
            client.updateNowPlaying("sk", ScrobbleTrack("A", "T"))
            fail("expected exception")
        } catch (e: LastFmException) {
            assertEquals(-1, e.code)
        }
    }
}
