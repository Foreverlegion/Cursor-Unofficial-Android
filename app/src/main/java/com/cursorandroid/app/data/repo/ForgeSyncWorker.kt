package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cursorandroid.app.CursorAndroidApp
import java.util.concurrent.TimeUnit

object ForgeSyncScheduler {
    private const val PERIODIC = "forge-repo-sync"

    /** Runs about every 6 hours while at least one forge has a token. Cancelled when none does. */
    fun sync(context: Context, forges: List<ForgeConnection>) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (forges.none { it.token.isNotBlank() && forgeSyncSupported(it) }) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ForgeSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(net)
                .build(),
        )
    }
}

class ForgeSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CursorAndroidApp ?: return Result.success()
        app.container.forgeSync.load()
        app.container.forgeSync.syncAll(maxAgeMs = FORGE_SYNC_WORKER_MIN_AGE_MS, force = false)
        return Result.success()
    }
}

// WorkManager may run a periodic job a little early; skip a run only when the last sync is clearly recent.
private const val FORGE_SYNC_WORKER_MIN_AGE_MS = FORGE_SYNC_BACKGROUND_MS - 30 * 60 * 1000L
