package dev.daubney.scrobbled.data.queue

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import dev.daubney.scrobbled.data.scrobble.TrackDraft
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Entity(tableName = "queued_scrobbles")
data class QueuedScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artist: String,
    val track: String,
    val album: String,
    val albumArtist: String,
    val durationSeconds: Int?,
    val mbid: String,
    val playedAt: Long,
)

@Dao
interface QueueDao {
    @Insert
    suspend fun insert(entity: QueuedScrobbleEntity)

    @Query("SELECT * FROM queued_scrobbles ORDER BY playedAt ASC, id ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<QueuedScrobbleEntity>

    @Query("DELETE FROM queued_scrobbles WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM queued_scrobbles")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM queued_scrobbles")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM queued_scrobbles")
    fun observeCount(): Flow<Int>
}

@Database(entities = [QueuedScrobbleEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
}

class RoomQueueStore(private val dao: QueueDao) : QueueStore {
    override suspend fun add(draft: TrackDraft) = dao.insert(
        QueuedScrobbleEntity(
            artist = draft.artist, track = draft.track, album = draft.album, albumArtist = draft.albumArtist,
            durationSeconds = draft.durationSeconds, mbid = draft.mbid, playedAt = draft.playedAt.epochSecond,
        ),
    )

    override suspend fun pending(limit: Int) = dao.pending(limit).map {
        QueuedItem(
            it.id,
            TrackDraft(
                artist = it.artist, track = it.track, album = it.album, albumArtist = it.albumArtist,
                durationSeconds = it.durationSeconds, mbid = it.mbid, playedAt = Instant.ofEpochSecond(it.playedAt),
            ),
        )
    }

    override suspend fun remove(ids: List<Long>) = dao.delete(ids)
    override suspend fun count() = dao.count()
    override suspend fun clear() = dao.clear()
    override fun observeCount() = dao.observeCount()
}
