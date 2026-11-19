package com.n3sensei.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.domain.repository.SyncHttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Starts or stops the background upload/pull for a paired device. */
interface BackgroundSyncScheduler {
    fun schedule()
    fun cancel()
}

/**
 * While paired, WorkManager runs [SyncWorker] about every 6 hours when a network is available, so
 * history recorded offline is sent later without the learner opening Settings. Only the server the
 * learner paired with is contacted; nothing runs before pairing or after "Ngắt kết nối".
 */
class WorkManagerSyncScheduler(private val context: Context) : BackgroundSyncScheduler {
    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "jlpt-web-sync"
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as N3SenseiApplication).container.syncRepository
        if (!repository.isPaired()) return Result.success()
        return try {
            repository.syncNow()
            Result.success()
        } catch (error: SyncHttpException) {
            // 401/403 (revoked) or a contract error will not fix itself; the next periodic run tries again.
            if (error.temporary) Result.retry() else Result.failure()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
