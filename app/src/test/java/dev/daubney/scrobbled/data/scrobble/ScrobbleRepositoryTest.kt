package dev.daubney.scrobbled.data.scrobble

import dev.daubney.scrobbled.data.api.LastFmClient
import dev.daubney.scrobbled.data.api.LastFmException
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.auth.Credentials
import dev.daubney.scrobbled.data.history.SessionExpiredException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.time.Instant

class ScrobbleRepositoryTest {
    private lateinit var server: MockWebServer
    private var creds: Credentials? = null
    private val auth = object : AuthRepository {
        override fun current() = creds
        override suspend fun connect(serverUrl: String, username: String, password: String) = error("unused")
        override fun disconnect() {}
    }
    private lateinit var repo: DefaultScrobbleRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        creds = Credentials(server.url("/").toString().trimEnd('/'), "alice", "tok", "key", "secret", "sess")
        repo = DefaultScrobbleRepository(auth) { url, key, secret -> LastFmClient(url, key, secret) }
    }

    @After fun tearDown() = server.shutdown()

    private val draft get() = TrackDraft(artist = "A", track = "T", album = "Al", playedAt = Instant.now())
    private fun ok(accepted: Int, ignored: Int) = MockResponse().setBody(
        """{"scrobbles":{"scrobble":[],"@attr":{"accepted":"$accepted","ignored":"$ignored"}}}""")

    @Test
    fun submitSendsSessionKeyAndFields() = runTest {
        server.enqueue(ok(1, 0))
        repo.submit(draft)
        val body = URLDecoder.decode(server.takeRequest().body.readUtf8(), "UTF-8")
        assertTrue(body.contains("sk=sess"))
        assertTrue(body.contains("artist[0]=A"))
        assertTrue(body.contains("album[0]=Al"))
        assertTrue(body.contains("api_key=key"))
    }

    @Test
    fun ignoredScrobbleIsAnError() = runTest {
        server.enqueue(ok(0, 1))
        try { repo.submit(draft); fail() } catch (e: IllegalStateException) {
            assertEquals("The server didn't accept this scrobble", e.message)
        }
    }

    @Test
    fun invalidSessionBecomesSessionExpired() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":9,"message":"Invalid session key"}"""))
        try { repo.submit(draft); fail() } catch (e: SessionExpiredException) { }
    }

    @Test
    fun otherServerErrorsPropagate() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":8,"message":"Operation failed"}"""))
        try { repo.submit(draft); fail() } catch (e: LastFmException) { assertEquals(8, e.code) }
    }

    @Test
    fun invalidDraftNeverHitsTheNetwork() = runTest {
        try { repo.submit(TrackDraft(artist = "", track = "T")); fail() } catch (e: IllegalArgumentException) { }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun noCredentialsIsSessionExpired() = runTest {
        creds = null
        try { repo.submit(draft); fail() } catch (e: SessionExpiredException) { }
    }
}
