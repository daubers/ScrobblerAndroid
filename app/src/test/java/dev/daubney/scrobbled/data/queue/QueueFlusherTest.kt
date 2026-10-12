package dev.daubney.scrobbled.data.queue

import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.auth.Credentials
import dev.daubney.scrobbled.data.scrobble.TrackDraft
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant

class QueueFlusherTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private lateinit var server: MockWebServer
    private val store = InMemoryQueue()
    private var creds: Credentials? = null
    private val auth = object : AuthRepository {
        override fun current() = creds
        override suspend fun connect(serverUrl: String, username: String, password: String) = error("unused")
        override fun disconnect() {}
    }
    private lateinit var flusher: QueueFlusher

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        creds = Credentials(server.url("/").toString().trimEnd('/'), "a", "t", "key", "secret", "sess")
        flusher = QueueFlusher(store, auth, now = { now })
    }
    @After fun tearDown() = server.shutdown()

    private suspend fun enqueue(n: Int, ageMinutes: Long = 10) = repeat(n) {
        store.add(TrackDraft(artist = "A", track = "T$it", playedAt = now.minus(Duration.ofMinutes(ageMinutes)).minusSeconds(it.toLong())))
    }
    private fun ok(accepted: Int, ignored: Int = 0) = MockResponse().setBody(
        """{"scrobbles":{"scrobble":[],"@attr":{"accepted":"$accepted","ignored":"$ignored"}}}""")

    @Test
    fun emptyQueueMakesNoRequest() = runTest {
        assertEquals(FlushResult.Done(0, 0), flusher.flush())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sendsOldestFirstAndEmptiesTheQueue() = runTest {
        enqueue(3)
        server.enqueue(ok(3))
        assertEquals(FlushResult.Done(3, 0), flusher.flush())
        assertEquals(0, store.count())
        val body = URLDecoder.decode(server.takeRequest().body.readUtf8(), "UTF-8")
        // T2 was played earliest, so it must be index 0
        assertTrue(body.contains("track[0]=T2"))
        assertTrue(body.contains("track[2]=T0"))
    }

    @Test
    fun largeQueueIsSentInBatchesOf50() = runTest {
        enqueue(120)
        server.enqueue(ok(50)); server.enqueue(ok(50)); server.enqueue(ok(20))
        assertEquals(FlushResult.Done(120, 0), flusher.flush())
        assertEquals(3, server.requestCount)
        assertEquals(0, store.count())
    }

    @Test
    fun ignoredScrobblesAreDroppedNotRetriedForever() = runTest {
        enqueue(2)
        server.enqueue(ok(1, 1))
        assertEquals(FlushResult.Done(1, 1), flusher.flush())
        assertEquals(0, store.count())
    }

    @Test
    fun offlineKeepsItemsAndAsksForRetry() = runTest {
        enqueue(2)
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))
        assertEquals(FlushResult.Retry, flusher.flush())
        assertEquals(2, store.count())
    }

    @Test
    fun serverBusyKeepsItemsAndAsksForRetry() = runTest {
        enqueue(1)
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":29,"message":"Rate limit exceeded"}"""))
        assertEquals(FlushResult.Retry, flusher.flush())
        assertEquals(1, store.count())
    }

    @Test
    fun rejectedSessionBlocksAndKeepsItems() = runTest {
        enqueue(2)
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":9,"message":"Invalid session key"}"""))
        assertEquals(FlushResult.Blocked("Session expired"), flusher.flush())
        assertEquals(2, store.count())
    }

    @Test
    fun unexpectedServerErrorBlocksAndKeepsItems() = runTest {
        enqueue(1)
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":6,"message":"Invalid parameters"}"""))
        assertEquals(FlushResult.Blocked("Invalid parameters"), flusher.flush())
        assertEquals(1, store.count())
    }

    @Test
    fun signedOutBlocksWithoutRequest() = runTest {
        enqueue(1)
        creds = null
        assertEquals(FlushResult.Blocked("Not signed in"), flusher.flush())
        assertEquals(0, server.requestCount)
        assertEquals(1, store.count())
    }

    @Test
    fun playsOlderThanFourteenDaysAreDroppedWithoutSending() = runTest {
        enqueue(1, ageMinutes = 15 * 24 * 60)
        assertEquals(FlushResult.Done(0, 1), flusher.flush())
        assertEquals(0, server.requestCount)
        assertEquals(0, store.count())
    }

    @Test
    fun mixedBatchDropsExpiredAndSendsTheRest() = runTest {
        enqueue(1, ageMinutes = 15 * 24 * 60)
        enqueue(2)
        server.enqueue(ok(2))
        assertEquals(FlushResult.Done(2, 1), flusher.flush())
        assertEquals(0, store.count())
    }
}
