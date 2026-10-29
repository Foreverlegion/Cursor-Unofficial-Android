package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Errors and list patches that must survive the inbox leaving the screen. */
class InboxSignals {
    private val errors = ArrayDeque<String>()
    private val drops = ArrayDeque<String>()
    private val restores = ArrayDeque<AgentSummary>()
    private val tickState = MutableStateFlow(0)
    val tick: StateFlow<Int> = tickState

    fun error(message: String) = synchronized(this) {
        errors.add(message)
        bump()
    }

    fun drop(id: String) = synchronized(this) {
        drops.add(id)
        bump()
    }

    fun restore(agent: AgentSummary) = synchronized(this) {
        restores.add(agent)
        bump()
    }

    fun drainErrors(): List<String> = synchronized(this) { drain(errors) }

    fun drainDrops(): List<String> = synchronized(this) { drain(drops) }

    fun drainRestores(): List<AgentSummary> = synchronized(this) { drain(restores) }

    private fun bump() {
        tickState.value = tickState.value + 1
    }

    private fun <T> drain(from: ArrayDeque<T>): List<T> {
        if (from.isEmpty()) return emptyList()
        val copy = from.toList()
        from.clear()
        return copy
    }
}
