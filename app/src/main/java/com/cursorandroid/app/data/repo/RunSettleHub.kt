package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isTerminalStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RunSettled(
    val agentId: String,
    val runId: String,
    val status: String,
    val result: String? = null,
)

data class LiveRun(
    val agentId: String,
    val runId: String,
    val status: String,
)

/**
 * Shared "this run ended" state. Notification watchers, the inbox sweep and the open thread all
 * publish here; the inbox list and the thread subscribe, so a finished run flips every surface
 * without waiting for the next list poll.
 */
class RunSettleHub(private val persist: (RunSettled) -> Unit = {}) {
    private val state = MutableStateFlow<Map<String, RunSettled>>(emptyMap())
    val settled: StateFlow<Map<String, RunSettled>> = state.asStateFlow()

    private val liveState = MutableStateFlow<Map<String, LiveRun>>(emptyMap())

    /** Runs known to be live whose agent card may still say idle (a run started on another device). */
    val liveRuns: StateFlow<Map<String, LiveRun>> = liveState.asStateFlow()

    fun publishLive(agentId: String, runId: String, status: String?): Boolean {
        val key = status?.trim()?.uppercase().orEmpty()
        if (agentId.isBlank() || runId.isBlank() || !isLiveStatus(key)) return false
        synchronized(this) {
            if (state.value[agentId]?.runId == runId) return false
            val next = LiveRun(agentId, runId, key)
            if (liveState.value[agentId] == next) return false
            liveState.value = liveState.value + (agentId to next)
        }
        return true
    }

    fun publish(agentId: String, runId: String, status: String?, result: String? = null): Boolean {
        val key = status?.trim()?.uppercase().orEmpty()
        if (agentId.isBlank() || runId.isBlank() || !isEndedRun(key)) return false
        val next = RunSettled(agentId, runId, key, result?.takeIf { it.isNotBlank() })
        synchronized(this) {
            if (liveState.value[agentId] != null) liveState.value = liveState.value - agentId
            val current = state.value[agentId]
            if (current != null && current.runId == runId && current.status == key &&
                (next.result == null || next.result == current.result)
            ) {
                return false
            }
            state.value = state.value + (agentId to next)
        }
        persist(next)
        return true
    }

    private val local = MutableStateFlow<Set<String>>(emptySet())

    /** Agents with a send, stream or live run known to an open thread; their cards show Running. */
    val localActive: StateFlow<Set<String>> = local.asStateFlow()

    fun setLocalActive(agentId: String, active: Boolean) {
        if (agentId.isBlank()) return
        synchronized(this) {
            val has = agentId in local.value
            if (active && !has) local.value = local.value + agentId
            if (!active && has) local.value = local.value - agentId
        }
    }

    fun clear(agentId: String) {
        synchronized(this) {
            if (agentId in state.value) state.value = state.value - agentId
            if (agentId in liveState.value) liveState.value = liveState.value - agentId
        }
    }

    fun current(agentId: String): RunSettled? = state.value[agentId]

    /** Lists say an agent is live; ask the run itself. Publishes the ones that already ended. */
    suspend fun sweep(
        agents: List<AgentSummary>,
        limit: Int = SWEEP_LIMIT,
        onSettled: (AgentSummary, Run) -> Unit = { _, _ -> },
        fetch: suspend (agentId: String, runId: String) -> Run?,
    ): Int {
        val targets = agents
            .filter { isLiveStatus(it.status) && !it.latestRunId.isNullOrBlank() }
            .filter { current(it.id)?.runId != it.latestRunId }
            .take(limit)
        if (targets.isEmpty()) return 0
        val runs = coroutineScope {
            targets.map { agent ->
                async { agent to fetch(agent.id, agent.latestRunId!!) }
            }.map { it.await() }
        }
        return runs.count { (agent, run) ->
            val done = run != null && publish(agent.id, run.id, run.status, run.result)
            if (done) onSettled(agent, run!!)
            done
        }
    }

    companion object {
        const val SWEEP_LIMIT = 6
    }
}

private fun isEndedRun(status: String): Boolean = status != "ARCHIVED" && isTerminalStatus(status)

fun settleAgent(agent: AgentSummary, settled: RunSettled?, live: LiveRun? = null): AgentSummary {
    if (settled != null && settled.agentId == agent.id && agent.latestRunId == settled.runId) {
        return if (isLiveStatus(agent.status)) agent.copy(status = settled.status) else agent
    }
    if (live != null && live.agentId == agent.id && agent.latestRunId == live.runId &&
        !isLiveStatus(agent.status) && !agent.status.equals("ARCHIVED", true)
    ) {
        return agent.copy(status = live.status)
    }
    return agent
}

fun settleAgents(
    agents: List<AgentSummary>,
    settled: Map<String, RunSettled>,
    live: Map<String, LiveRun> = emptyMap(),
): List<AgentSummary> {
    if (settled.isEmpty() && live.isEmpty()) return agents
    var changed = false
    val out = agents.map { agent ->
        val next = settleAgent(agent, settled[agent.id], live[agent.id])
        if (next !== agent) changed = true
        next
    }
    return if (changed) out else agents
}

/**
 * The agent object can keep saying ACTIVE after its latest run ended. When the run we hold is
 * terminal and is the agent's latest, the run wins.
 */
fun settledAgentStatus(agentStatus: String?, latestRunId: String?, run: Run?): String? {
    if (run == null || !isLiveStatus(agentStatus)) return agentStatus
    val ended = run.status?.trim()?.uppercase().orEmpty()
    if (!isEndedRun(ended)) return agentStatus
    if (latestRunId != null && latestRunId != run.id) return agentStatus
    return ended
}

fun markLocalActive(agents: List<AgentSummary>, active: Set<String>): List<AgentSummary> {
    if (active.isEmpty()) return agents
    var changed = false
    val out = agents.map { agent ->
        if (agent.id in active && !isLiveStatus(agent.status) && !agent.status.equals("ARCHIVED", true)) {
            changed = true
            agent.copy(status = "ACTIVE")
        } else {
            agent
        }
    }
    return if (changed) out else agents
}
