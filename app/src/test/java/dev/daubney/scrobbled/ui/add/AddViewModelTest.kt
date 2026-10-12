package dev.daubney.scrobbled.ui.add

import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.scrobble.SubmitResult
import dev.daubney.scrobbled.data.scrobble.TrackDraft
import dev.daubney.scrobbled.data.search.SearchException
import dev.daubney.scrobbled.data.search.TrackResult
import dev.daubney.scrobbled.data.search.TrackSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

private class FakeScrobbles : ScrobbleRepository {
    val submitted = mutableListOf<TrackDraft>()
    var failure: Exception? = null
    var result = SubmitResult.Sent
    override suspend fun submit(draft: TrackDraft): SubmitResult {
        failure?.let { throw it }
        submitted += draft
        return result
    }
    override suspend fun setNowPlaying(draft: TrackDraft) {}
}

private class FakeSearch : TrackSearch {
    var results = listOf(TrackResult("mb1", "Radiohead", "Reckoner", "In Rainbows", 290))
    var failure: SearchException? = null
    val queries = mutableListOf<String>()
    override suspend fun search(query: String): List<TrackResult> {
        queries += query
        failure?.let { throw it }
        return results
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val repo = FakeScrobbles()
    private val search = FakeSearch()
    private lateinit var vm: AddViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = AddViewModel(repo, search) { now }
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun fill() { vm.onArtist("Radiohead"); vm.onTrack("Reckoner") }

    @Test
    fun cannotSubmitWithoutArtistAndTrack() {
        assertFalse(vm.state.value.canSubmit)
        vm.onArtist("A")
        assertFalse(vm.state.value.canSubmit)
        vm.onTrack("T")
        assertTrue(vm.state.value.canSubmit)
        vm.submit()
        assertEquals(1, repo.submitted.size)
    }

    @Test
    fun submitSendsDraftWithTimeRelativeToNow() {
        fill(); vm.onAlbum("In Rainbows"); vm.onWhen(WhenOption.Hour1)
        vm.submit()
        val d = repo.submitted.single()
        assertEquals("Radiohead", d.artist)
        assertEquals("In Rainbows", d.album)
        assertEquals(now.minusSeconds(3600), d.playedAt)
    }

    @Test
    fun successClearsFormKeepsTimeChoiceAndShowsMessage() {
        fill(); vm.onWhen(WhenOption.Hours3)
        vm.submit()
        val s = vm.state.value
        assertEquals("", s.artist)
        assertEquals("", s.track)
        assertEquals(WhenOption.Hours3, s.whenOption)
        assertEquals("Scrobbled Reckoner", s.message)
        assertFalse(s.submitting)
    }

    @Test
    fun failureKeepsFormAndShowsError() {
        fill()
        repo.failure = RuntimeException("offline")
        vm.submit()
        val s = vm.state.value
        assertEquals("offline", s.error)
        assertEquals("Radiohead", s.artist)
        assertFalse(s.submitting)
        assertTrue(s.canSubmit)
    }

    @Test
    fun editingClearsMessageAndError() {
        fill(); vm.submit()
        assertTrue(vm.state.value.message != null)
        vm.onArtist("x")
        assertNull(vm.state.value.message)
    }

    @Test
    fun yesterdayIsWithinServerWindow() {
        fill(); vm.onWhen(WhenOption.Yesterday)
        vm.submit()
        assertEquals(1, repo.submitted.size)
        assertNull(vm.state.value.error)
    }

    @Test
    fun searchPopulatesResults() {
        vm.onQuery("radiohead reckoner")
        vm.search()
        assertEquals(listOf("radiohead reckoner"), search.queries)
        assertEquals(1, vm.state.value.results.size)
        assertTrue(vm.state.value.searched)
        assertFalse(vm.state.value.searching)
    }

    @Test
    fun blankQueryDoesNotSearch() {
        vm.onQuery("  ")
        vm.search()
        assertEquals(emptyList<String>(), search.queries)
    }

    @Test
    fun searchErrorIsShownAndResultsKept() {
        vm.onQuery("x"); vm.search()
        search.failure = SearchException("MusicBrainz is busy. Try again in a moment.")
        vm.search()
        assertEquals("MusicBrainz is busy. Try again in a moment.", vm.state.value.searchError)
        assertEquals(1, vm.state.value.results.size)
        assertFalse(vm.state.value.searching)
    }

    @Test
    fun pickingResultFillsFormAndSwitchesToManual() {
        vm.pick(search.results.single())
        val s = vm.state.value
        assertEquals(AddTab.Manual, s.tab)
        assertEquals("Radiohead", s.artist)
        assertEquals("In Rainbows", s.album)
        assertEquals("mb1", s.mbid)
        assertEquals(290, s.durationSeconds)
    }

    @Test
    fun pickedResultSubmitsWithMbidAndDuration() {
        vm.pick(search.results.single())
        vm.submit()
        val d = repo.submitted.single()
        assertEquals("mb1", d.mbid)
        assertEquals(290, d.durationSeconds)
    }

    @Test
    fun changingArtistOrTrackDropsTheMusicBrainzMatch() {
        vm.pick(search.results.single())
        vm.onTrack("Reckoner (live)")
        assertEquals("", vm.state.value.mbid)
        assertNull(vm.state.value.durationSeconds)
        vm.pick(search.results.single())
        vm.onAlbum("Other album") // album tweaks keep the match
        assertEquals("mb1", vm.state.value.mbid)
    }

    @Test
    fun successKeepsSearchResultsForTheNextPick() {
        vm.onQuery("radiohead"); vm.search()
        vm.pick(search.results.single()); vm.submit()
        assertEquals("radiohead", vm.state.value.query)
        assertEquals(1, vm.state.value.results.size)
        assertEquals("", vm.state.value.mbid)
    }

    @Test
    fun queuedSubmitSaysItWillBeSentLater() {
        repo.result = SubmitResult.Queued
        fill(); vm.submit()
        assertEquals("Saved Reckoner. It will be sent when you're back online.", vm.state.value.message)
        assertEquals("", vm.state.value.track) // form clears: the scrobble is safely stored
    }
}
