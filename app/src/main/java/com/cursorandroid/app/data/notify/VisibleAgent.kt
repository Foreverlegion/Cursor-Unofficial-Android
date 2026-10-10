package com.cursorandroid.app.data.notify

import java.util.concurrent.atomic.AtomicInteger

/** Shade notifications are suppressed while any app activity is started; the in-app notice tray covers them. */
object VisibleAgent {
    private val started = AtomicInteger(0)

    fun activityStarted() {
        started.incrementAndGet()
    }

    fun activityStopped() {
        started.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun shouldSuppress(): Boolean = started.get() > 0

    internal fun resetForTest() {
        started.set(0)
    }
}
