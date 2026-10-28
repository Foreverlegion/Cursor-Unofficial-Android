package com.cursorandroid.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.work.WorkManager
import com.cursorandroid.app.data.notify.FeedbackReplyScheduler
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.notify.VisibleAgent
import com.cursorandroid.app.data.repo.FORGE_SYNC_START_MS
import com.cursorandroid.app.data.repo.ForgeSyncScheduler

class CursorAndroidApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ForegroundCallbacks)
        container = AppContainer(this)
        container.archives.recoverAfterProcessDeath()
        container.folderIcons.pruneOrphans(
            container.folders.snapshot().folders.map { it.icon }.filter { it.isNotBlank() }.toSet(),
        )
        container.notifier.ensureChannel()
        if (container.store.hasKey()) {
            RunWatchScheduler.resume(this)
        }
        FeedbackReplyScheduler.sync(this)
        container.forgeSync.requestLoad()
        container.forgeSync.requestDue(FORGE_SYNC_START_MS)
        ForgeSyncScheduler.sync(this, container.store.forges())
        // Older builds enqueued these. Cancel them so they stop pinging.
        val work = WorkManager.getInstance(this)
        work.cancelUniqueWork("install-pulse")
        work.cancelUniqueWork("install-pulse-now")
        work.cancelUniqueWork("auto-update")
        work.cancelUniqueWork("auto-update-now")
    }

    private object ForegroundCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = VisibleAgent.activityStarted()
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) {
            VisibleAgent.activityStopped()
            if (!VisibleAgent.foreground()) {
                val app = activity.application as CursorAndroidApp
                app.container.archives.flush()
                app.container.ui.flush()
            }
        }
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
