package dev.daubney.scrobbled.data.search

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Spaces calls at least [intervalMs] apart; concurrent callers queue up. Clock and sleep are injectable for tests. */
class RateLimiter(
    private val intervalMs: Long,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var lastStart: Long? = null

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        lastStart?.let { last ->
            val wait = last + intervalMs - nowMs()
            if (wait > 0) sleep(wait)
        }
        lastStart = nowMs()
        block()
    }
}
