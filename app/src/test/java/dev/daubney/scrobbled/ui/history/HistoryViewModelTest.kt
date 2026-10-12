package dev.daubney.scrobbled.ui.history

import dev.daubney.scrobbled.ui.FakeHistory
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

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsPagesInOrderUntilExhausted() {
        val repo = FakeHistory(total = 120)
        val vm = HistoryViewModel(repo)
        repeat(5) { vm.loadNext() }
        assertEquals(listOf(1, 2, 3), repo.pageRequests) // extra calls after the last page are ignored
        assertEquals(120, vm.state.value.items.size)
        assertFalse(vm.state.value.hasMore)
    }

    @Test
    fun emptyHistoryIsReportedAsEmpty() {
        val vm = HistoryViewModel(FakeHistory(total = 0))
        vm.loadNext()
        assertTrue(vm.state.value.empty)
    }

    @Test
    fun errorKeepsExistingItemsAndAllowsRetry() {
        val repo = FakeHistory(total = 120)
        val vm = HistoryViewModel(repo)
        vm.loadNext()
        repo.failure = RuntimeException("offline")
        vm.loadNext()
        assertEquals("offline", vm.state.value.error)
        assertEquals(50, vm.state.value.items.size)
        assertFalse(vm.state.value.loading)
        repo.failure = null
        vm.loadNext()
        assertNull(vm.state.value.error)
        assertEquals(100, vm.state.value.items.size)
    }

    @Test
    fun duplicatesFromShiftedPagesAreDropped() {
        val repo = FakeHistory(total = 120)
        val vm = HistoryViewModel(repo)
        vm.loadNext()
        repo.shift = 3 // 3 new plays arrive: page 2 now repeats the last 3 rows of page 1
        vm.loadNext()
        val keys = vm.state.value.items.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun refreshResetsToFirstPage() {
        val repo = FakeHistory(total = 120)
        val vm = HistoryViewModel(repo)
        vm.loadNext(); vm.loadNext()
        vm.refresh()
        assertEquals(50, vm.state.value.items.size)
        assertEquals(2, vm.state.value.nextPage)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun failedRefreshKeepsOldItems() {
        val repo = FakeHistory(total = 120)
        val vm = HistoryViewModel(repo)
        vm.loadNext()
        repo.failure = RuntimeException("offline")
        vm.refresh()
        assertEquals(50, vm.state.value.items.size)
        assertEquals("offline", vm.state.value.error)
        assertFalse(vm.state.value.refreshing)
    }
}
