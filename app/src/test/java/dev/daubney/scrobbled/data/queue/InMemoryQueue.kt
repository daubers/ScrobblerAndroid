package dev.daubney.scrobbled.data.queue

import dev.daubney.scrobbled.data.scrobble.TrackDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryQueue : QueueStore {
    private val items = MutableStateFlow<List<QueuedItem>>(emptyList())
    private var nextId = 1L
    val all get() = items.value

    override suspend fun add(draft: TrackDraft) { items.value = items.value + QueuedItem(nextId++, draft) }
    override suspend fun pending(limit: Int) =
        items.value.sortedWith(compareBy({ it.draft.playedAt }, { it.id })).take(limit)
    override suspend fun remove(ids: List<Long>) { items.value = items.value.filter { it.id !in ids } }
    override suspend fun count() = items.value.size
    override fun observeCount() = items.map { it.size }
}

class RecordingScheduler : FlushScheduler {
    var calls = 0
    override fun schedule() { calls++ }
}
