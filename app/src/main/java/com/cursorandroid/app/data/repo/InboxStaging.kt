package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentDetail
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.mergeInboxAgents
import java.util.concurrent.ConcurrentHashMap

/** Agents created locally before the list API returns them. Dropped once the list includes the id. */
class InboxStagingHub {
    private val staged = ConcurrentHashMap<String, AgentSummary>()

    fun stage(agent: AgentSummary) {
        staged[agent.id] = agent
    }

    fun merge(existing: List<AgentSummary>, incoming: List<AgentSummary>): List<AgentSummary> {
        if (incoming.isNotEmpty()) {
            incoming.forEach { staged.remove(it.id) }
        }
        val withStaged = if (staged.isEmpty()) {
            existing
        } else {
            mergeInboxAgents(existing, staged.values.toList())
        }
        return mergeInboxAgents(withStaged, incoming)
    }
}

fun AgentDetail.toInboxSummary(run: Run? = null): AgentSummary = AgentSummary(
    id = id,
    name = name,
    status = status,
    env = env,
    url = url,
    createdAt = createdAt,
    updatedAt = updatedAt ?: createdAt,
    latestRunId = run?.id ?: latestRunId,
    archived = archived,
    runStatus = run?.status,
)
