package com.cursorandroid.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.work.WorkManager
import com.cursorandroid.app.data.repo.CachePruneWorker
import com.cursorandroid.app.data.notify.FeedbackReplyScheduler
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.notify.VisibleAgent

class CursorAndroidApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ForegroundCallbacks)
        container = AppContainer(this)
        container.notifier.ensureChannel()
        if (container.store.hasKey()) {
            RunWatchScheduler.resume(this)
        }
        FeedbackReplyScheduler.sync(this)
        CachePruneWorker.schedule(this)
        Thread({ runCatching { container.cache.prune() } }, "cache-prune").apply { priority = Thread.MIN_PRIORITY }.start()
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
        override fun onActivityStopped(activity: Activity) = VisibleAgent.activityStopped()
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
