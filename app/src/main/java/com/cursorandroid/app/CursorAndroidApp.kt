package com.cursorandroid.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.notify.VisibleAgent
import com.cursorandroid.app.data.repo.AutoUpdateScheduler

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
        AutoUpdateScheduler.sync(this, container.store.autoUpdate)
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
