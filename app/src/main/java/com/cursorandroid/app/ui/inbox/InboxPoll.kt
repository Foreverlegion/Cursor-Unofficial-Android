package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.isLiveStatus

/**
 * Foreground inbox poll budget. The list endpoint lags on status, so the top
 * rows are hydrated from GET /v1/agents/{id}; only busy or changed rows are
 * hydrated each tick, with a full sweep on a slower cadence.
 */
internal object InboxPoll {
    const val BUSY_MS = 5_000L
    const val IDLE_MS = 15_000L
    const val FULL_HYDRATE_MS = 30_000L
    const val COMPUTERS_MS = 60_000L
    const val HYDRATE_LIMIT = 15

    fun busy(agents: List<AgentSummary>, live: Map<String, String>): Boolean =
        agents.any { isLiveStatus(it.status) } || live.values.any { isLiveStatus(it) }

    fun delayMs(busy: Boolean): Long = if (busy) BUSY_MS else IDLE_MS

    fun hydrateIds(
        previous: List<AgentSummary>,
        incoming: List<AgentSummary>,
        live: Map<String, String>,
        full: Boolean,
    ): Set<String> {
        val top = incoming.take(HYDRATE_LIMIT)
        if (full) return top.mapTo(LinkedHashSet()) { it.id }
        val prev = previous.associateBy { it.id }
        return top.filter { agent ->
            val old = prev[agent.id]
            old == null ||
                isLiveStatus(agent.status) ||
                isLiveStatus(old.status) ||
                isLiveStatus(live[agent.id]) ||
                old.updatedAt != agent.updatedAt ||
                old.latestRunId != agent.latestRunId
        }.mapTo(LinkedHashSet()) { it.id }
    }

    fun due(lastAt: Long, now: Long, everyMs: Long): Boolean = lastAt == 0L || now < lastAt || now - lastAt >= everyMs
}
