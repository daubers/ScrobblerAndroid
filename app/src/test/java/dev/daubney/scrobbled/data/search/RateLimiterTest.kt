package dev.daubney.scrobbled.data.search

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimiterTest {
    private var clock = 0L
    private val sleeps = mutableListOf<Long>()
    private val limiter = RateLimiter(1_000, nowMs = { clock }, sleep = { clock += it; sleeps += it })

    @Test
    fun firstCallDoesNotWait() = runTest {
        limiter.run { }
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun backToBackCallsAreSpacedByTheInterval() = runTest {
        limiter.run { }
        limiter.run { }
        assertEquals(listOf(1_000L), sleeps)
    }

    @Test
    fun onlyWaitsForTheRemainingTime() = runTest {
        limiter.run { }
        clock += 600
        limiter.run { }
        assertEquals(listOf(400L), sleeps)
    }

    @Test
    fun noWaitAfterEnoughTimePasses() = runTest {
        limiter.run { }
        clock += 5_000
        limiter.run { }
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun returnsTheBlockResult() = runTest {
        assertEquals(42, limiter.run { 42 })
    }
}
