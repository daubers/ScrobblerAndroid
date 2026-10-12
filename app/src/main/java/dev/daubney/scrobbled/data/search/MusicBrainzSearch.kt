package dev.daubney.scrobbled.data.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Recording search against the MusicBrainz web service. MusicBrainz asks for at most one request per
 * second and a User-Agent that says who is calling (an app URL rather than a personal address).
 */
class MusicBrainzSearch(
    private val baseUrl: String = "https://musicbrainz.org",
    private val http: OkHttpClient = OkHttpClient(),
    private val limiter: RateLimiter = RateLimiter(1_000),
) : TrackSearch {

    override suspend fun search(query: String): List<TrackResult> {
        val lucene = buildQuery(query) ?: return emptyList()
        val url = "$baseUrl/ws/2/recording".toHttpUrl().newBuilder()
            .addQueryParameter("query", lucene)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", LIMIT.toString())
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val (status, body) = limiter.run {
            withContext(Dispatchers.IO) {
                try {
                    http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
                } catch (e: IOException) {
                    throw SearchException("Couldn't reach MusicBrainz: ${e.message}")
                }
            }
        }
        when {
            status == 503 || status == 429 -> throw SearchException("MusicBrainz is busy. Try again in a moment.")
            status !in 200..299 -> throw SearchException("MusicBrainz search failed ($status)")
        }
        val parsed = try {
            json.decodeFromString<Response>(body)
        } catch (e: Exception) {
            throw SearchException("Unexpected response from MusicBrainz")
        }
        return parsed.recordings.mapNotNull { it.toResult() }
            // The same song is often listed once per release; collapse identical rows.
            .distinctBy { Triple(it.artist.lowercase(), it.track.lowercase(), it.album?.lowercase()) }
    }

    @Serializable
    private data class Response(val recordings: List<Recording> = emptyList())

    @Serializable
    private data class Recording(
        val id: String,
        val title: String = "",
        val length: Long? = null,
        @SerialName("artist-credit") val artistCredit: List<Credit> = emptyList(),
        val releases: List<Release> = emptyList(),
    )

    @Serializable
    private data class Credit(val name: String = "", val joinphrase: String = "")

    @Serializable
    private data class Release(val title: String = "")

    private fun Recording.toResult(): TrackResult? {
        val artist = artistCredit.joinToString("") { it.name + it.joinphrase }.trim()
        if (title.isBlank() || artist.isEmpty()) return null
        return TrackResult(
            mbid = id, artist = artist, track = title,
            album = releases.firstOrNull()?.title?.takeIf { it.isNotBlank() },
            durationSeconds = length?.let { (it / 1000).toInt() },
        )
    }

    companion object {
        const val USER_AGENT = "ScrobbledAndroid/1.0 (https://git.daubney.dev/matt/ScrobbledAndroid)"
        private const val LIMIT = 15
        private val json = Json { ignoreUnknownKeys = true }
        private val special = Regex("""([+\-&|!(){}\[\]^"~*?:\\/])""")

        /**
         * Free text to Lucene: every word must match the title, artist or release, so "radiohead reckoner"
         * works without field syntax. Special characters are escaped; null when nothing searchable is left.
         */
        fun buildQuery(input: String): String? {
            val words = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            return words.joinToString(" AND ") { w ->
                val e = special.replace(w, """\\$1""")
                "(recording:$e OR artist:$e OR release:$e)"
            }
        }
    }
}
