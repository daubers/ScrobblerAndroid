package dev.daubney.scrobbled.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "just now", "5 min ago", "3 h ago", "yesterday", otherwise a short local date. */
fun formatAgo(then: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val secs = Duration.between(then, now).seconds
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60} min ago"
        secs < 86_400 -> "${secs / 3600} h ago"
        then.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate().minusDays(1) -> "yesterday"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone).format(then)
    }
}
