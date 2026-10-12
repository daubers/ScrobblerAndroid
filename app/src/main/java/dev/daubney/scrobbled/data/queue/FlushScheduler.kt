package dev.daubney.scrobbled.data.queue

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.daubney.scrobbled.ScrobbledApplication
import java.util.concurrent.TimeUnit

fun interface FlushScheduler {
    /** Arranges for the queue to be sent as soon as the device is online. */
    fun schedule()
}

class WorkManagerFlushScheduler(private val context: Context) : FlushScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE so a worker sitting in backoff is restarted when new work arrives.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "flush-scrobble-queue"
    }
}

class QueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ScrobbledApplication
        return when (app.queueFlusher.flush()) {
            is FlushResult.Done, is FlushResult.Blocked -> Result.success()
            FlushResult.Retry -> Result.retry()
        }
    }
}
