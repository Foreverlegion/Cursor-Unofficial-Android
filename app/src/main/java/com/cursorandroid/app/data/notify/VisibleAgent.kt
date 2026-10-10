package com.cursorandroid.app.data.notify

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object VisibleAgent {
    private val id = AtomicReference<String?>(null)
    private val started = AtomicInteger(0)

    private val inbox = AtomicInteger(0)

    fun inboxPolling(active: Boolean) {
        if (active) inbox.incrementAndGet() else inbox.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** The visible inbox polls the agent list and the live runs itself, so background watchers sit out. */
    fun inboxCoversRuns(): Boolean = started.get() > 0 && inbox.get() > 0

    fun set(agentId: String?) {
        id.set(agentId)
    }

    fun activityStarted() {
        started.incrementAndGet()
    }

    fun activityStopped() {
        started.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun isOpenInForeground(agentId: String): Boolean = started.get() > 0 && id.get() == agentId

    fun shouldSuppress(agentId: String? = null): Boolean {
        return started.get() > 0
    }

    internal fun resetForTest() {
        id.set(null)
        started.set(0)
        inbox.set(0)
    }
}
