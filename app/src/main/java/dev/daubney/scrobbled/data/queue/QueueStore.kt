package dev.daubney.scrobbled.data.queue

import dev.daubney.scrobbled.data.scrobble.TrackDraft
import kotlinx.coroutines.flow.Flow

data class QueuedItem(val id: Long, val draft: TrackDraft)

/** Scrobbles that couldn't be sent yet, oldest play first. */
interface QueueStore {
    suspend fun add(draft: TrackDraft)
    suspend fun pending(limit: Int): List<QueuedItem>
    suspend fun remove(ids: List<Long>)
    suspend fun count(): Int
    fun observeCount(): Flow<Int>
}
