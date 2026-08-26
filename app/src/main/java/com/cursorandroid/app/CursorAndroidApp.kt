package com.cursorandroid.app

import android.app.Application
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.AutoUpdateScheduler

class CursorAndroidApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannel()
        if (container.store.hasKey()) {
            RunWatchScheduler.resume(this)
        }
        AutoUpdateScheduler.sync(this, container.store.autoUpdate)
    }
}
