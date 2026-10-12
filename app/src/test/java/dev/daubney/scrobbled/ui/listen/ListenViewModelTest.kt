package dev.daubney.scrobbled.ui.listen

import dev.daubney.scrobbled.data.recognition.AudioClip
import dev.daubney.scrobbled.data.recognition.AudioRecorder
import dev.daubney.scrobbled.data.recognition.ListenException
import dev.daubney.scrobbled.data.recognition.Recognizer
import dev.daubney.scrobbled.data.scrobble.ScrobbleRepository
import dev.daubney.scrobbled.data.scrobble.SubmitResult
import dev.daubney.scrobbled.data.scrobble.TrackDraft
import dev.daubney.scrobbled.data.search.TrackResult
import kotlinx.coroutines.CompletableDeferred
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

private class FakeRecorder : AudioRecorder {
    var gate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null
    val requested = mutableListOf<Int>()
    override suspend fun record(seconds: Int, onProgress: (Float) -> Unit): AudioClip {
        requested += seconds
        onProgress(0.5f)
        gate?.await()
        failure?.let { throw it }
        return AudioClip(ByteArray(16), 8000)
    }
}

private class FakeRecognizer : Recognizer {
    var result: TrackResult? = TrackResult("mb1", "Radiohead", "Reckoner", "In Rainbows", 290)
    var gate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null
    override suspend fun recognize(clip: AudioClip): TrackResult? {
        gate?.await()
        failure?.let { throw it }
        return result
    }
}

private class FakeScrobbles : ScrobbleRepository {
    val submitted = mutableListOf<TrackDraft>()
    val nowPlaying = mutableListOf<TrackDraft>()
    var failure: Exception? = null
    var result = SubmitResult.Sent
    override suspend fun submit(draft: TrackDraft): SubmitResult {
        failure?.let { throw it }; submitted += draft; return result
    }
    override suspend fun setNowPlaying(draft: TrackDraft) {
        failure?.let { throw it }; nowPlaying += draft
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListenViewModelTest {
    private val t0 = Instant.parse("2026-10-12T12:00:00Z")
    private var now = t0
    private val recorder = FakeRecorder()
    private val recognizer = FakeRecognizer()
    private val scrobbles = FakeScrobbles()
    private lateinit var vm: ListenViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = ListenViewModel(recorder, recognizer, scrobbles) { now }
    }
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun successfulListenLandsOnConfirmWithRecognisedFields() {
        vm.start()
        val s = vm.state.value
        assertEquals(ListenPhase.Confirm, s.phase)
        assertEquals("Radiohead", s.artist)
        assertEquals("Reckoner", s.track)
        assertEquals("In Rainbows", s.album)
        assertEquals("mb1", s.mbid)
        assertEquals(listOf(ListenViewModel.LISTEN_SECONDS), recorder.requested)
    }

    @Test
    fun showsRecordingThenIdentifyingPhases() {
        recorder.gate = CompletableDeferred()
        recognizer.gate = CompletableDeferred()
        vm.start()
        assertEquals(ListenPhase.Recording, vm.state.value.phase)
        assertEquals(0.5f, vm.state.value.progress, 0.001f)
        recorder.gate!!.complete(Unit)
        assertEquals(ListenPhase.Identifying, vm.state.value.phase)
        recognizer.gate!!.complete(Unit)
        assertEquals(ListenPhase.Confirm, vm.state.value.phase)
    }

    @Test
    fun noMatchIsReported() {
        recognizer.result = null
        vm.start()
        assertEquals(ListenPhase.NoMatch, vm.state.value.phase)
    }

    @Test
    fun microphoneFailureReturnsToIdleWithMessage() {
        recorder.failure = ListenException("Couldn't open the microphone")
        vm.start()
        assertEquals(ListenPhase.Idle, vm.state.value.phase)
        assertEquals("Couldn't open the microphone", vm.state.value.error)
    }

    @Test
    fun recognitionFailureReturnsToIdleWithMessage() {
        recognizer.failure = ListenException("Recognition service unavailable")
        vm.start()
        assertEquals("Recognition service unavailable", vm.state.value.error)
        assertEquals(ListenPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun cancelWhileRecordingStopsAndResets() {
        recorder.gate = CompletableDeferred()
        vm.start()
        vm.cancel()
        assertEquals(ListenPhase.Idle, vm.state.value.phase)
        recorder.gate!!.complete(Unit) // a late finish must not resurrect the flow
        assertEquals(ListenPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun cannotStartTwiceWhileRecording() {
        recorder.gate = CompletableDeferred()
        vm.start(); vm.start()
        assertEquals(1, recorder.requested.size)
    }

    @Test
    fun permissionDeniedShowsMessage() {
        vm.onPermissionDenied()
        assertEquals(ListenPhase.Idle, vm.state.value.phase)
        assertTrue(vm.state.value.error!!.contains("permission"))
    }

    @Test
    fun scrobbleSubmitsEditedDraftWithTimeListeningStarted() {
        vm.start()
        now = t0.plusSeconds(60) // user took a minute to confirm
        vm.onAlbum("Edited album")
        vm.scrobble()
        val d = scrobbles.submitted.single()
        assertEquals("Edited album", d.album)
        assertEquals("mb1", d.mbid)
        assertEquals(290, d.durationSeconds)
        assertEquals(t0, d.playedAt)
        assertEquals("Scrobbled Reckoner", vm.state.value.message)
        assertEquals(ListenPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun editingArtistOrTrackDropsTheRecognisedIds() {
        vm.start()
        vm.onTrack("Reckoner (live)")
        vm.scrobble()
        val d = scrobbles.submitted.single()
        assertEquals("", d.mbid)
        assertNull(d.durationSeconds)
    }

    @Test
    fun queuedScrobbleSaysItWillBeSentLater() {
        scrobbles.result = SubmitResult.Queued
        vm.start(); vm.scrobble()
        assertEquals("Saved Reckoner. It will be sent when you're back online.", vm.state.value.message)
    }

    @Test
    fun setNowPlayingAnnouncesInsteadOfScrobbling() {
        vm.start(); vm.setNowPlaying()
        assertEquals(1, scrobbles.nowPlaying.size)
        assertEquals(0, scrobbles.submitted.size)
        assertEquals("Now playing: Reckoner", vm.state.value.message)
    }

    @Test
    fun failedSubmitKeepsTheConfirmFormAndShowsError() {
        vm.start()
        scrobbles.failure = RuntimeException("The server didn't accept this scrobble")
        vm.scrobble()
        val s = vm.state.value
        assertEquals(ListenPhase.Confirm, s.phase)
        assertEquals("The server didn't accept this scrobble", s.error)
        assertEquals("Radiohead", s.artist)
        assertFalse(s.busy)
    }

    @Test
    fun cannotSubmitWithBlankFields() {
        vm.start()
        vm.onArtist(" ")
        assertFalse(vm.state.value.canSubmit)
        vm.scrobble()
        assertEquals(0, scrobbles.submitted.size)
    }
}
