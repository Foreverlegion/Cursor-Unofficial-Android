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

object InstallPulseScheduler {
    private const val PERIODIC = "install-pulse"
    private const val ONCE = "install-pulse-now"

    fun sync(context: Context) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        val net = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<InstallPulseWorker>(12, TimeUnit.HOURS)
                .setConstraints(net)
                .build(),
        )
        wm.enqueueUniqueWork(
            ONCE,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<InstallPulseWorker>()
                .setConstraints(net)
                .build(),
        )
    }
}

class InstallPulseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (applicationContext !is CursorAndroidApp) return Result.success()
        runCatching { InstallPulse.ping(applicationContext) }
        return Result.success()
    }
}
