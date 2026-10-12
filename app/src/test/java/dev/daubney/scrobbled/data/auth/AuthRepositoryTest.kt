package dev.daubney.scrobbled.data.auth

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FakeStore : CredentialStore {
    var saved: Credentials? = null
    override fun load() = saved
    override fun save(credentials: Credentials) { saved = credentials }
    override fun clear() { saved = null }
}

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private val store = FakeStore()
    private val requests = mutableListOf<String>()
    private var existingApps = """[]"""
    private var loginResponse = MockResponse().setBody("""{"token":"bearer1","user":{"id":1,"username":"alice"}}""")
    private var sessionResponse = MockResponse().setBody("""{"session":{"name":"alice","key":"sess1"}}""")

    private lateinit var repo: DefaultAuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += "${request.method} ${request.path}"
                    return when {
                        request.path == "/api/v1/auth/login" -> loginResponse
                        request.path == "/api/v1/apps" && request.method == "GET" -> {
                            assertEquals("Bearer bearer1", request.getHeader("Authorization"))
                            MockResponse().setBody(existingApps)
                        }
                        request.path == "/api/v1/apps" ->
                            MockResponse().setResponseCode(201)
                                .setBody("""{"name":"Scrobbled Android","api_key":"newkey","shared_secret":"newsecret","created_at":"x"}""")
                        request.path == "/2.0/" -> sessionResponse
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        repo = DefaultAuthRepository(store)
    }

    @After
    fun tearDown() = server.shutdown()

    private val url get() = server.url("/").toString()

    @Test
    fun connectCreatesAppAndStoresCredentials() = runTest {
        val creds = repo.connect(url, "alice", "pw")
        assertEquals(
            Credentials(url.trimEnd('/'), "alice", "bearer1", "newkey", "newsecret", "sess1"),
            creds,
        )
        assertEquals(creds, store.saved)
        assertEquals(
            listOf("POST /api/v1/auth/login", "GET /api/v1/apps", "POST /api/v1/apps", "POST /2.0/"),
            requests,
        )
    }

    @Test
    fun connectReusesExistingApp() = runTest {
        existingApps = """[{"name":"Other","api_key":"o","shared_secret":"os","created_at":"x"},
            {"name":"Scrobbled Android","api_key":"oldkey","shared_secret":"oldsecret","created_at":"x"}]"""
        val creds = repo.connect(url, "alice", "pw")
        assertEquals("oldkey", creds.apiKey)
        assertEquals("oldsecret", creds.apiSecret)
        assertEquals(false, requests.contains("POST /api/v1/apps"))
    }

    @Test
    fun wrongPasswordGivesFriendlyErrorAndStoresNothing() = runTest {
        loginResponse = MockResponse().setResponseCode(401)
            .setBody("""{"error":{"code":"invalid_credentials","message":"Bad login"}}""")
        try {
            repo.connect(url, "alice", "nope")
            fail("expected exception")
        } catch (e: ConnectException) {
            assertEquals("Wrong username or password", e.message)
        }
        assertNull(store.saved)
    }

    @Test
    fun sessionFailureIsReportedAndStoresNothing() = runTest {
        sessionResponse = MockResponse().setResponseCode(403).setBody("""{"error":4,"message":"Authentication Failed"}""")
        try {
            repo.connect(url, "alice", "pw")
            fail("expected exception")
        } catch (e: ConnectException) {
            assertEquals("Authentication Failed", e.message)
        }
        assertNull(store.saved)
    }

    @Test
    fun unreachableServerIsReported() = runTest {
        val dead = url
        server.shutdown()
        try {
            repo.connect(dead, "alice", "pw")
            fail("expected exception")
        } catch (e: ConnectException) {
            assertNotNull(e.message)
            assert(e.message!!.startsWith("Couldn't reach the server"))
        }
    }

    @Test
    fun invalidUrlIsRejectedBeforeAnyRequest() = runTest {
        try {
            repo.connect("   ", "alice", "pw")
            fail("expected exception")
        } catch (e: ConnectException) {
            assertEquals("Enter a valid server address", e.message)
        }
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun disconnectClearsStore() = runTest {
        repo.connect(url, "alice", "pw")
        repo.disconnect()
        assertNull(repo.current())
    }

    @Test
    fun normalizeServerUrl() {
        assertEquals("https://music.example.com", normalizeServerUrl(" music.example.com/ "))
        assertEquals("http://10.0.2.2:5050", normalizeServerUrl("http://10.0.2.2:5050/"))
        assertEquals("https://h/sub", normalizeServerUrl("https://h/sub/"))
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("ftp://host"))
        assertNull(normalizeServerUrl("http://"))
    }
}
