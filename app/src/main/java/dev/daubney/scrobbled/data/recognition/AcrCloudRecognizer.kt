package dev.daubney.scrobbled.data.recognition

import dev.daubney.scrobbled.data.search.TrackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Identifies music with ACRCloud's HTTP "identify" API (https://docs.acrcloud.com/reference/identification-api). */
class AcrCloudRecognizer(
    private val config: () -> RecognitionConfig?,
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrlFor: (host: String) -> String = { "https://$it" },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : Recognizer {

    override suspend fun recognize(clip: AudioClip): TrackResult? {
        val cfg = config()?.takeIf { it.isComplete } ?: throw ListenException("ACRCloud isn't set up. Add your keys in Settings.")
        val wav = clip.toWav()
        val timestamp = nowSeconds().toString()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("access_key", cfg.accessKey)
            .addFormDataPart("data_type", DATA_TYPE)
            .addFormDataPart("signature_version", SIGNATURE_VERSION)
            .addFormDataPart("signature", sign(cfg.accessKey, cfg.accessSecret, timestamp))
            .addFormDataPart("timestamp", timestamp)
            .addFormDataPart("sample_bytes", wav.size.toString())
            .addFormDataPart("sample", "sample.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val request = Request.Builder().url("${baseUrlFor(cfg.host)}$URI").post(body).build()

        val text = withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw ListenException("Couldn't reach ACRCloud: ${e.message}")
            }
        }
        return parse(text)
    }

    private fun parse(text: String): TrackResult? {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ListenException("Unexpected response from ACRCloud")
        }
        val status = root["status"]?.jsonObject
        val code = status?.get("code")?.jsonPrimitive?.longOrNull?.toInt() ?: throw ListenException("Unexpected response from ACRCloud")
        val message = status["msg"].str().orEmpty()
        when (code) {
            0 -> Unit
            1001, 2004 -> return null // no match / nothing fingerprintable (e.g. silence)
            3001, 3002, 3014 -> throw ListenException("ACRCloud rejected your keys. Check them in Settings.")
            3003 -> throw ListenException("ACRCloud request limit reached.")
            else -> throw ListenException("ACRCloud error $code: $message")
        }
        val music = root["metadata"]?.jsonObject?.get("music")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val title = music["title"].str()?.takeIf { it.isNotBlank() } ?: return null
        val artist = music["artists"]?.jsonArray?.mapNotNull { it.jsonObject["name"].str() }?.joinToString(", ").orEmpty()
        if (artist.isBlank()) return null
        return TrackResult(
            mbid = musicBrainzId(music).orEmpty(),
            artist = artist,
            track = title,
            album = (music["album"] as? JsonObject)?.get("name").str()?.takeIf { it.isNotBlank() },
            durationSeconds = (music["duration_ms"] as? JsonPrimitive)?.longOrNull?.let { (it / 1000).toInt() },
        )
    }

    /** ACRCloud includes MusicBrainz ids in `external_metadata.musicbrainz` for projects with that source enabled. */
    private fun musicBrainzId(music: JsonObject): String? {
        val mb = (music["external_metadata"] as? JsonObject)?.get("musicbrainz")
        val first = when (mb) {
            is JsonObject -> mb
            is kotlinx.serialization.json.JsonArray -> mb.firstOrNull() as? JsonObject
            else -> null
        }
        return ((first?.get("track") as? JsonObject)?.get("id")).str()
    }

    private fun kotlinx.serialization.json.JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull

    companion object {
        private const val URI = "/v1/identify"
        private const val DATA_TYPE = "audio"
        private const val SIGNATURE_VERSION = "1"

        /** `base64(hmac_sha1(secret, "POST\n/v1/identify\n{key}\naudio\n1\n{timestamp}"))`. */
        fun sign(accessKey: String, secret: String, timestamp: String): String {
            val toSign = listOf("POST", URI, accessKey, DATA_TYPE, SIGNATURE_VERSION, timestamp).joinToString("\n")
            val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1")) }
            return java.util.Base64.getEncoder().encodeToString(mac.doFinal(toSign.toByteArray(Charsets.UTF_8)))
        }
    }
}

/** Wraps the raw 16-bit mono PCM in a canonical 44-byte WAV header. */
fun AudioClip.toWav(): ByteArray {
    val dataSize = pcm.size
    val out = java.io.ByteArrayOutputStream(44 + dataSize)
    fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
    fun short(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
    out.write("RIFF".toByteArray()); int(36 + dataSize); out.write("WAVE".toByteArray())
    out.write("fmt ".toByteArray()); int(16); short(1) /* PCM */; short(1) /* mono */
    int(sampleRate); int(sampleRate * 2); short(2); short(16)
    out.write("data".toByteArray()); int(dataSize)
    out.write(pcm)
    return out.toByteArray()
}
