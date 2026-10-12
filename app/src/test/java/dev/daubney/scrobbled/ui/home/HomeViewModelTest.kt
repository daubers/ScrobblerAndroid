package dev.daubney.scrobbled.ui.home

import dev.daubney.scrobbled.data.history.NowPlaying
import dev.daubney.scrobbled.ui.FakeHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val playing = NowPlaying("A", "T", null, 200, Instant.EPOCH, Instant.EPOCH.plusSeconds(200))

    @Test
    fun refreshLoadsSummaryRecentAndNowPlaying() {
        val repo = FakeHistory(total = 120).also { it.nowPlaying = playing }
        val vm = HomeViewModel(repo)
        vm.refresh()
        val s = vm.state.value
        assertTrue(s.loaded)
        assertEquals(120, s.summary?.scrobbles)
        assertEquals(5, s.recent.size)
        assertEquals(playing, s.nowPlaying)
        assertNull(s.error)
    }

    @Test
    fun refreshReportsErrorButStillMarksLoaded() {
        val repo = FakeHistory().also { it.failure = RuntimeException("offline") }
        val vm = HomeViewModel(repo)
        vm.refresh()
        assertTrue(vm.state.value.loaded)
        assertEquals("offline", vm.state.value.error)
    }

    @Test
    fun nowPlayingPollClearsWhenTrackEnds() {
        val repo = FakeHistory().also { it.nowPlaying = playing }
        val vm = HomeViewModel(repo)
        vm.refreshNowPlaying()
        assertNotNull(vm.state.value.nowPlaying)
        repo.nowPlaying = null
        vm.refreshNowPlaying()
        assertNull(vm.state.value.nowPlaying)
    }

    @Test
    fun nowPlayingPollFailureKeepsLastValueSilently() {
        val repo = FakeHistory().also { it.nowPlaying = playing }
        val vm = HomeViewModel(repo)
        vm.refreshNowPlaying()
        repo.failure = RuntimeException("offline")
        vm.refreshNowPlaying()
        assertEquals(playing, vm.state.value.nowPlaying)
        assertNull(vm.state.value.error)
    }

    @Test
    fun pendingCountFollowsTheQueue() {
        val queue = MutableStateFlow(0)
        val vm = HomeViewModel(FakeHistory(), queue)
        assertEquals(0, vm.state.value.pending)
        queue.value = 3
        assertEquals(3, vm.state.value.pending)
        queue.value = 0
        assertEquals(0, vm.state.value.pending)
    }
}
