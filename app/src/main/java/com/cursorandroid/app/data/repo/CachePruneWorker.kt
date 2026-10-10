package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cursorandroid.app.CursorAndroidApp
import java.util.concurrent.TimeUnit

class CachePruneWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CursorAndroidApp ?: return Result.failure()
        runCatching { app.container.cache.prune() }
        return Result.success()
    }

    companion object {
        private const val NAME = "cache-prune"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CachePruneWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
