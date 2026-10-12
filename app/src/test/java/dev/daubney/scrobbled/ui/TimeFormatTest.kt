package dev.daubney.scrobbled.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimeFormatTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private fun ago(secs: Long) = formatAgo(now.minusSeconds(secs), now, ZoneOffset.UTC)

    @Test fun recent() = assertEquals("just now", ago(30))
    @Test fun minutes() = assertEquals("5 min ago", ago(5 * 60 + 10))
    @Test fun hours() = assertEquals("3 h ago", ago(3 * 3600))
    @Test fun yesterday() = assertEquals("yesterday", ago(20 * 3600 + 13 * 3600)) // 11 Oct 15:00
    @Test fun older() = assertEquals(true, ago(10 * 86_400L).contains("2026"))
}
