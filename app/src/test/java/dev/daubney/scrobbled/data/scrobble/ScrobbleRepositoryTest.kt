package dev.daubney.scrobbled.data.scrobble

import dev.daubney.scrobbled.data.api.LastFmClient
import dev.daubney.scrobbled.data.api.LastFmException
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.auth.Credentials
import dev.daubney.scrobbled.data.history.SessionExpiredException
import dev.daubney.scrobbled.data.queue.InMemoryQueue
import dev.daubney.scrobbled.data.queue.RecordingScheduler
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
    private val queue = InMemoryQueue()
    private val scheduler = RecordingScheduler()
    private lateinit var repo: DefaultScrobbleRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        creds = Credentials(server.url("/").toString().trimEnd('/'), "alice", "tok", "key", "secret", "sess")
        repo = DefaultScrobbleRepository(auth, queue, scheduler) { url, key, secret -> LastFmClient(url, key, secret) }
    }

    @After fun tearDown() = server.shutdown()

    private val draft get() = TrackDraft(artist = "A", track = "T", album = "Al", playedAt = Instant.now())
    private fun ok(accepted: Int, ignored: Int) = MockResponse().setBody(
        """{"scrobbles":{"scrobble":[],"@attr":{"accepted":"$accepted","ignored":"$ignored"}}}""")

    @Test
    fun submitSendsSessionKeyAndFields() = runTest {
        server.enqueue(ok(1, 0))
        assertEquals(SubmitResult.Sent, repo.submit(draft))
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
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":6,"message":"Invalid parameters"}"""))
        try { repo.submit(draft); fail() } catch (e: LastFmException) { assertEquals(6, e.code) }
        assertEquals(0, queue.all.size)
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

    @Test
    fun networkFailureQueuesAndSchedulesAFlush() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))
        assertEquals(SubmitResult.Queued, repo.submit(draft))
        assertEquals(1, queue.all.size)
        assertEquals("T", queue.all.single().draft.track)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun serverErrorAndRateLimitAlsoQueue() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":8,"message":"Operation failed"}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":29,"message":"Rate limit exceeded"}"""))
        assertEquals(SubmitResult.Queued, repo.submit(draft))
        assertEquals(SubmitResult.Queued, repo.submit(draft))
        assertEquals(2, queue.all.size)
    }

    @Test
    fun rejectedSessionIsNotQueued() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":9,"message":"Invalid session key"}"""))
        try { repo.submit(draft); fail() } catch (e: SessionExpiredException) { }
        assertEquals(0, queue.all.size)
    }

    @Test
    fun successWithBacklogSchedulesAFlush() = runTest {
        queue.add(draft)
        server.enqueue(ok(1, 0))
        repo.submit(draft)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun successWithEmptyQueueDoesNotSchedule() = runTest {
        server.enqueue(ok(1, 0))
        repo.submit(draft)
        assertEquals(0, scheduler.calls)
    }

    @Test
    fun setNowPlayingSendsUpdateNowPlayingWithoutQueueing() = runTest {
        server.enqueue(MockResponse().setBody("""{"nowplaying":{"track":{"#text":"T"}}}"""))
        repo.setNowPlaying(TrackDraft(artist = "A", track = "T", album = "Al", durationSeconds = 200))
        val body = URLDecoder.decode(server.takeRequest().body.readUtf8(), "UTF-8")
        assertTrue(body.contains("method=track.updateNowPlaying"))
        assertTrue(body.contains("artist=A"))
        assertTrue(body.contains("duration=200"))
        assertTrue(body.contains("sk=sess"))
        assertEquals(0, queue.all.size)
    }

    @Test
    fun setNowPlayingDoesNotQueueOnFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad</html>"))
        try { repo.setNowPlaying(TrackDraft(artist = "A", track = "T")); fail() } catch (e: LastFmException) { }
        assertEquals(0, queue.all.size)
    }

    @Test
    fun setNowPlayingMapsInvalidSession() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":9,"message":"Invalid session key"}"""))
        try { repo.setNowPlaying(TrackDraft(artist = "A", track = "T")); fail() } catch (e: SessionExpiredException) { }
    }

    @Test
    fun setNowPlayingNeedsArtistAndTrack() = runTest {
        try { repo.setNowPlaying(TrackDraft(artist = "A", track = " ")); fail() } catch (e: IllegalArgumentException) { }
        assertEquals(0, server.requestCount)
    }
}
