package dev.daubney.scrobbled.data.history

import dev.daubney.scrobbled.data.api.ApiException
import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.auth.Credentials
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

class HistoryRepositoryTest {
    private lateinit var server: MockWebServer
    private var creds: Credentials? = null

    private val auth = object : AuthRepository {
        override fun current() = creds
        override suspend fun connect(serverUrl: String, username: String, password: String) = error("unused")
        override fun disconnect() {}
    }
    private lateinit var repo: DefaultHistoryRepository
    private var expiredCalls = 0

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        creds = Credentials(server.url("/").toString().trimEnd('/'), "alice", "tok", "k", "s", "sk")
        repo = DefaultHistoryRepository(auth, onSessionExpired = { expiredCalls++ })
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun nowPlayingParsesTrackAndSendsBearer() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"now_playing":{"artist":"Radiohead","track":"Reckoner","album":null,"duration":290,
               "started_at":"2026-10-12T10:00:00+00:00","expires_at":"2026-10-12T10:04:50+00:00"}}"""))
        val np = repo.nowPlaying()!!
        assertEquals("Radiohead", np.artist)
        assertNull(np.album) // JSON null must not become the string "null"
        assertEquals(290, np.durationSeconds)
        assertEquals(Instant.parse("2026-10-12T10:00:00Z"), np.startedAt)
        val req = server.takeRequest()
        assertEquals("/api/v1/me/now-playing", req.path)
        assertEquals("Bearer tok", req.getHeader("Authorization"))
    }

    @Test
    fun nowPlayingNullWhenNothingPlaying() = runTest {
        server.enqueue(MockResponse().setBody("""{"now_playing":null}"""))
        assertNull(repo.nowPlaying())
    }

    @Test
    fun recentParsesPage() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"items":[{"artist":"A","track":"T","album":"Al","album_artist":null,"duration":null,
               "played_at":"2026-10-12T10:15:02.494847+00:00"}],"page":2,"per_page":50,"total":120,"total_pages":3}"""))
        val page = repo.recent(2, 50)
        assertEquals("/api/v1/me/recent?page=2&limit=50", server.takeRequest().path)
        assertEquals(1, page.items.size)
        assertEquals("Al", page.items[0].album)
        assertEquals(Instant.parse("2026-10-12T10:15:02Z").epochSecond, page.items[0].playedAt.epochSecond)
        assertEquals(2, page.page)
        assertEquals(120, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun lastPageHasNoMore() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[],"page":3,"per_page":50,"total":120,"total_pages":3}"""))
        assertFalse(repo.recent(3).hasMore)
    }

    @Test
    fun summaryParses() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"scrobbles":10,"artists":4,"tracks":7,"first_scrobble_at":"2026-01-01T00:00:00+00:00"}"""))
        assertEquals(Summary(10, 4, 7, Instant.parse("2026-01-01T00:00:00Z")), repo.summary())
    }

    @Test
    fun summaryForNewUserHasNoFirstScrobble() = runTest {
        server.enqueue(MockResponse().setBody("""{"scrobbles":0,"artists":0,"tracks":0,"first_scrobble_at":null}"""))
        assertNull(repo.summary().firstScrobbleAt)
    }

    @Test
    fun unauthorizedBecomesSessionExpired() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"unauthorized","message":"no"}}"""))
        try { repo.summary(); fail() } catch (e: SessionExpiredException) { }
        assertEquals(1, expiredCalls)
    }

    @Test
    fun noCredentialsIsSessionExpired() = runTest {
        creds = null
        try { repo.recent(1); fail() } catch (e: SessionExpiredException) { }
    }

    @Test
    fun otherServerErrorsPropagate() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"code":"boom","message":"Kaput"}}"""))
        try { repo.summary(); fail() } catch (e: ApiException) {
            assertEquals(500, e.status)
            assertEquals("Kaput", e.message)
        }
    }

    @Test
    fun unreachableServerGivesPlainMessage() = runTest {
        server.shutdown()
        try { repo.summary(); fail() } catch (e: ApiException) {
            assertEquals("Can't reach the server. Check your connection.", e.message)
        }
    }
}
