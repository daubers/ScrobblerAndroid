package dev.daubney.scrobbled.ui.settings

import dev.daubney.scrobbled.data.queue.InMemoryQueue
import dev.daubney.scrobbled.data.recognition.MemoryConfigStore
import dev.daubney.scrobbled.data.recognition.RecognitionConfig
import dev.daubney.scrobbled.data.recognition.RecognitionSettings
import dev.daubney.scrobbled.data.scrobble.TrackDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val store = MemoryConfigStore()
    private val settings = RecognitionSettings(store)
    private val queue = InMemoryQueue()
    private var signedOut = 0

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SettingsViewModel("https://h", "alice", settings, queue) { signedOut++ }

    @Test
    fun startsInDemoModeWithAccountShown() {
        val s = vm().state.value
        assertEquals("alice", s.username)
        assertEquals("https://h", s.serverUrl)
        assertFalse(s.recognitionActive)
    }

    @Test
    fun loadsSavedKeys() {
        store.saved = RecognitionConfig("host", "key", "secret")
        val s = SettingsViewModel("u", "n", RecognitionSettings(store), queue) {}.state.value
        assertTrue(s.recognitionActive)
        assertEquals("key", s.accessKey)
    }

    @Test
    fun savingCompleteKeysActivatesAcrCloud() {
        val vm = vm()
        vm.onHost(" https://identify-eu-west-1.acrcloud.com/ "); vm.onAccessKey("k"); vm.onAccessSecret("s")
        vm.saveKeys()
        val s = vm.state.value
        assertTrue(s.recognitionActive)
        assertEquals("identify-eu-west-1.acrcloud.com", s.host)
        assertEquals("Saved. Listen now uses ACRCloud.", s.keysMessage)
        assertNotNull(store.saved)
        assertFalse(settings.isDemo)
    }

    @Test
    fun partialKeysAreRejectedAndNotSaved() {
        val vm = vm()
        vm.onHost("host"); vm.onAccessKey("k")
        vm.saveKeys()
        assertNotNull(vm.state.value.keysError)
        assertNull(store.saved)
    }

    @Test
    fun savingAllBlankRemovesKeys() {
        store.saved = RecognitionConfig("h", "k", "s")
        val vm = SettingsViewModel("u", "n", RecognitionSettings(store), queue) {}
        vm.onHost(""); vm.onAccessKey(""); vm.onAccessSecret("")
        vm.saveKeys()
        assertFalse(vm.state.value.recognitionActive)
        assertNull(store.saved)
    }

    @Test
    fun editingClearsStatusMessages() {
        val vm = vm()
        vm.saveKeys(); vm.onHost("x")
        vm.saveKeys()
        vm.onHost("y")
        assertNull(vm.state.value.keysError)
        assertNull(vm.state.value.keysMessage)
    }

    @Test
    fun signOutIsImmediateWhenNothingIsQueued() {
        vm().requestSignOut()
        assertEquals(1, signedOut)
    }

    @Test
    fun signOutAsksFirstWhenScrobblesAreWaiting() = kotlinx.coroutines.test.runTest {
        queue.add(TrackDraft(artist = "A", track = "T"))
        val vm = vm()
        assertEquals(1, vm.state.value.pendingScrobbles)
        vm.requestSignOut()
        assertTrue(vm.state.value.confirmingSignOut)
        assertEquals(0, signedOut)
        assertEquals(1, queue.count())
    }

    @Test
    fun cancellingKeepsTheQueueAndSession() = kotlinx.coroutines.test.runTest {
        queue.add(TrackDraft(artist = "A", track = "T"))
        val vm = vm()
        vm.requestSignOut(); vm.cancelSignOut()
        assertFalse(vm.state.value.confirmingSignOut)
        assertEquals(0, signedOut)
        assertEquals(1, queue.count())
    }

    @Test
    fun confirmingDiscardsTheQueueAndSignsOut() = kotlinx.coroutines.test.runTest {
        queue.add(TrackDraft(artist = "A", track = "T"))
        val vm = vm()
        vm.requestSignOut(); vm.confirmSignOut()
        assertEquals(0, queue.count())
        assertEquals(1, signedOut)
    }
}
