package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cursorandroid.app.CursorAndroidApp
import java.util.concurrent.TimeUnit

object AutoUpdateScheduler {
    private const val PERIODIC = "auto-update"
    private const val ONCE = "auto-update-now"

    fun sync(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(ONCE)
            return
        }
        val net = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoUpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(net)
                .build(),
        )
        wm.enqueueUniqueWork(
            ONCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoUpdateWorker>()
                .setConstraints(net)
                .build(),
        )
    }
}

class AutoUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CursorAndroidApp ?: return Result.success()
        if (!app.container.store.autoUpdate) return Result.success()
        runCatching {
            AppUpdate.applyIfAvailable(applicationContext, app.container.store.githubToken)
        }
        return Result.success()
    }
}
