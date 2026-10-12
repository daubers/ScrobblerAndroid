package dev.daubney.scrobbled.data.recognition

import dev.daubney.scrobbled.data.search.TrackResult

/** Raw microphone audio: 16-bit signed little-endian mono PCM. */
class AudioClip(val pcm: ByteArray, val sampleRate: Int) {
    val seconds get() = pcm.size / 2.0 / sampleRate
}

/** A failure the user can act on (no microphone, no network, service refused). */
class ListenException(message: String) : Exception(message)

interface AudioRecorder {
    /** Records [seconds] of audio, reporting progress 0..1. Cancelling the coroutine stops the microphone. */
    suspend fun record(seconds: Int, onProgress: (Float) -> Unit): AudioClip
}

interface Recognizer {
    /**
     * Identifies the song in [clip], or returns null when nothing matched. The result's `mbid` is the
     * MusicBrainz recording id when the service provides one, otherwise empty.
     */
    suspend fun recognize(clip: AudioClip): TrackResult?
}
