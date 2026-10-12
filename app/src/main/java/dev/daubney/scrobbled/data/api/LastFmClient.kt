package dev.daubney.scrobbled.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Client for the server's Last.fm-compatible `/2.0/` API. Writes are signed with the app's secret. */
class LastFmClient(
    baseUrl: String,
    private val apiKey: String,
    private val secret: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val endpoint = baseUrl.trimEnd('/').plus("/2.0/").toHttpUrl()

    suspend fun getMobileSession(username: String, password: String): Session {
        val root = call(
            mapOf("method" to "auth.getMobileSession", "username" to username, "password" to password),
        )
        val session = root["session"]?.jsonObject ?: throw LastFmException(-1, "Malformed session response")
        return Session(
            username = session.string("name") ?: username,
            key = session.string("key") ?: throw LastFmException(-1, "Session response had no key"),
        )
    }

    /** Submits up to 50 scrobbles in one call. Duplicates are accepted by the server, so retries are safe. */
    suspend fun scrobble(sessionKey: String, tracks: List<ScrobbleTrack>): ScrobbleResult {
        require(tracks.size in 1..50) { "Between 1 and 50 tracks per call" }
        val params = mutableMapOf("method" to "track.scrobble", "sk" to sessionKey)
        tracks.forEachIndexed { i, t ->
            val ts = requireNotNull(t.timestamp) { "Scrobbles need a timestamp" }
            params["artist[$i]"] = t.artist
            params["track[$i]"] = t.track
            params["timestamp[$i]"] = ts.toString()
            t.optionalFields().forEach { (k, v) -> params["$k[$i]"] = v }
        }
        val attr = call(params)["scrobbles"]?.jsonObject?.get("@attr")?.jsonObject
            ?: throw LastFmException(-1, "Malformed scrobble response")
        return ScrobbleResult(attr.getValue("accepted").jsonPrimitive.int, attr.getValue("ignored").jsonPrimitive.int)
    }

    suspend fun updateNowPlaying(sessionKey: String, track: ScrobbleTrack) {
        val params = mutableMapOf(
            "method" to "track.updateNowPlaying", "sk" to sessionKey,
            "artist" to track.artist, "track" to track.track,
        )
        params += track.optionalFields()
        call(params)
    }

    private fun ScrobbleTrack.optionalFields(): Map<String, String> = buildMap {
        album?.let { put("album", it) }
        albumArtist?.let { put("albumArtist", it) }
        durationSeconds?.let { put("duration", it.toString()) }
        trackNumber?.let { put("trackNumber", it.toString()) }
        mbid?.let { put("mbid", it) }
    }

    private suspend fun call(params: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val signed = params + ("api_key" to apiKey)
        val body = FormBody.Builder(Charsets.UTF_8).apply {
            signed.forEach { (k, v) -> add(k, v) }
            add("api_sig", Signer.sign(signed, secret))
            add("format", "json")
        }.build()
        val text = try {
            http.newCall(Request.Builder().url(endpoint).post(body).build()).execute().use { it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw LastFmException(-1, e.message ?: "Network error")
        }
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw LastFmException(-1, "Unexpected response from server")
        }
        root["error"]?.let {
            throw LastFmException(it.jsonPrimitive.int, root["message"]?.jsonPrimitive?.content ?: "Request failed")
        }
        root
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content
}
