package dev.daubney.scrobbled.ui.add

import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.scrobble.TrackDraft
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
    override suspend fun submit(draft: TrackDraft) {
        failure?.let { throw it }
        submitted += draft
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val repo = FakeScrobbles()
    private lateinit var vm: AddViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = AddViewModel(repo) { now }
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
}
