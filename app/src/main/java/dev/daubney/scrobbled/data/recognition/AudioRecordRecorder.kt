package dev.daubney.scrobbled.data.recognition

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

/** Records from the microphone as 8 kHz mono 16-bit PCM, the format recognition services expect. */
class AudioRecordRecorder : AudioRecorder {
    override suspend fun record(seconds: Int, onProgress: (Float) -> Unit): AudioClip = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw ListenException("No microphone available")
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, RATE * 2),
            )
        } catch (e: SecurityException) {
            throw ListenException("Microphone permission is needed to listen")
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw ListenException("Couldn't open the microphone")
        }
        val target = RATE * seconds * 2
        val out = ByteArrayOutputStream(target)
        val buffer = ByteArray(minBuffer)
        try {
            recorder.startRecording()
            while (out.size() < target) {
                coroutineContext.ensureActive()
                val n = recorder.read(buffer, 0, buffer.size)
                if (n < 0) throw ListenException("Lost the microphone")
                out.write(buffer, 0, n)
                onProgress((out.size().toFloat() / target).coerceAtMost(1f))
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        AudioClip(out.toByteArray(), RATE)
    }

    private companion object {
        const val RATE = 8000
    }
}
