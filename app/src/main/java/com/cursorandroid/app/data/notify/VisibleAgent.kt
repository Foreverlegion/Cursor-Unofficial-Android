package com.cursorandroid.app.data.notify

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object VisibleAgent {
    private val id = AtomicReference<String?>(null)
    private val resumed = AtomicBoolean(false)

    fun set(agentId: String?) {
        id.set(agentId)
    }

    fun setResumed(value: Boolean) {
        resumed.set(value)
    }

    fun shouldSuppress(agentId: String): Boolean {
        return resumed.get() && id.get() == agentId
    }
}
