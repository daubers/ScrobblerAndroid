package dev.daubney.scrobbled.data.search

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MusicBrainzSearchTest {
    private lateinit var server: MockWebServer
    private lateinit var search: MusicBrainzSearch

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        search = MusicBrainzSearch(server.url("/").toString().trimEnd('/'))
    }
    @After fun tearDown() = server.shutdown()

    @Test
    fun mapsRecordingToResult() = runTest {
        server.enqueue(MockResponse().setBody("""{"recordings":[{"id":"mbid-1","title":"Reckoner","length":290000,
            "artist-credit":[{"name":"Radiohead","joinphrase":""}],
            "releases":[{"title":"In Rainbows"},{"title":"Other"}]}]}"""))
        val r = search.search("radiohead reckoner").single()
        assertEquals(TrackResult("mbid-1", "Radiohead", "Reckoner", "In Rainbows", 290), r)
    }

    @Test
    fun sendsUserAgentAndQuery() = runTest {
        server.enqueue(MockResponse().setBody("""{"recordings":[]}"""))
        search.search("radiohead reckoner")
        val req = server.takeRequest()
        assertEquals(MusicBrainzSearch.USER_AGENT, req.getHeader("User-Agent"))
        assertEquals("json", req.requestUrl!!.queryParameter("fmt"))
        assertEquals(
            "(recording:radiohead OR artist:radiohead OR release:radiohead) AND (recording:reckoner OR artist:reckoner OR release:reckoner)",
            req.requestUrl!!.queryParameter("query"),
        )
    }

    @Test
    fun joinsMultipleArtistCredits() = runTest {
        server.enqueue(MockResponse().setBody("""{"recordings":[{"id":"x","title":"Song",
            "artist-credit":[{"name":"A","joinphrase":" feat. "},{"name":"B","joinphrase":""}]}]}"""))
        val r = search.search("song").single()
        assertEquals("A feat. B", r.artist)
        assertNull(r.album)
        assertNull(r.durationSeconds)
    }

    @Test
    fun collapsesSameSongListedOnManyReleases() = runTest {
        val rec = { id: String -> """{"id":"$id","title":"Reckoner","artist-credit":[{"name":"Radiohead"}],"releases":[{"title":"In Rainbows"}]}""" }
        server.enqueue(MockResponse().setBody("""{"recordings":[${rec("a")},${rec("b")},
            {"id":"c","title":"Reckoner","artist-credit":[{"name":"Radiohead"}],"releases":[{"title":"Live"}]}]}"""))
        assertEquals(listOf("a", "c"), search.search("reckoner").map { it.mbid })
    }

    @Test
    fun skipsRecordingsWithoutArtistOrTitle() = runTest {
        server.enqueue(MockResponse().setBody("""{"recordings":[{"id":"x","title":"No artist"},
            {"id":"y","title":"","artist-credit":[{"name":"A"}]}]}"""))
        assertEquals(emptyList<TrackResult>(), search.search("x"))
    }

    @Test
    fun blankQueryMakesNoRequest() = runTest {
        assertEquals(emptyList<TrackResult>(), search.search("   "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun busyServerGivesFriendlyError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"slow down"}"""))
        try { search.search("x"); fail() } catch (e: SearchException) {
            assertEquals("MusicBrainz is busy. Try again in a moment.", e.message)
        }
    }

    @Test
    fun garbageResponseIsReportedNotCrashed() = runTest {
        server.enqueue(MockResponse().setBody("<html>"))
        try { search.search("x"); fail() } catch (e: SearchException) {
            assertEquals("Unexpected response from MusicBrainz", e.message)
        }
    }

    @Test
    fun networkFailureIsReported() = runTest {
        server.shutdown()
        try { search.search("x"); fail() } catch (e: SearchException) {
            assert(e.message!!.startsWith("Couldn't reach MusicBrainz"))
        }
    }

    @Test
    fun luceneSpecialCharactersAreEscaped() {
        assertEquals(
            """(recording:AC\/DC OR artist:AC\/DC OR release:AC\/DC)""",
            MusicBrainzSearch.buildQuery("AC/DC"),
        )
        assertEquals(
            """(recording:what\? OR artist:what\? OR release:what\?) AND (recording:\(live\) OR artist:\(live\) OR release:\(live\))""",
            MusicBrainzSearch.buildQuery("what? (live)"),
        )
        assertNull(MusicBrainzSearch.buildQuery("  "))
    }
}
