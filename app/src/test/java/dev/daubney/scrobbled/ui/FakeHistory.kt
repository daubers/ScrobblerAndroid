package dev.daubney.scrobbled.ui

import dev.daubney.scrobbled.data.history.HistoryRepository
import dev.daubney.scrobbled.data.history.NowPlaying
import dev.daubney.scrobbled.data.history.RecentScrobble
import dev.daubney.scrobbled.data.history.ScrobblePage
import dev.daubney.scrobbled.data.history.Summary
import java.time.Instant

fun scrobble(n: Int, at: Long = 1_700_000_000L - n) = RecentScrobble("Artist", "Track $n", null, Instant.ofEpochSecond(at))

class FakeHistory(var total: Int = 120, private val pageSize: Int = 50) : HistoryRepository {
    var nowPlaying: NowPlaying? = null
    var failure: Exception? = null
    var summary = Summary(total, 5, 9, null)
    val pageRequests = mutableListOf<Int>()
    /** Extra scrobbles at the front, simulating new plays arriving between page loads. */
    var shift = 0

    override suspend fun nowPlaying(): NowPlaying? { failure?.let { throw it }; return nowPlaying }
    override suspend fun summary(): Summary { failure?.let { throw it }; return summary }

    override suspend fun recent(page: Int, limit: Int): ScrobblePage {
        failure?.let { throw it }
        pageRequests += page
        val size = if (limit == HistoryRepository.PAGE_SIZE) pageSize else limit
        val from = (page - 1) * size
        val items = (from until minOf(from + size, total)).map { scrobble(it - shift) }
        return ScrobblePage(items, page, (total + size - 1) / size, total)
    }
}
