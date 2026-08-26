package com.cursorandroid.app.data.notify

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object VisibleAgent {
    private val id = AtomicReference<String?>(null)
    private val started = AtomicInteger(0)

    fun set(agentId: String?) {
        id.set(agentId)
    }

    fun activityStarted() {
        started.incrementAndGet()
    }

    fun activityStopped() {
        started.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun shouldSuppress(agentId: String? = null): Boolean {
        return started.get() > 0
    }

    internal fun resetForTest() {
        id.set(null)
        started.set(0)
    }
}
