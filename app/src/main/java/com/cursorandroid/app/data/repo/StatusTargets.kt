package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.isArchived
import com.cursorandroid.app.data.api.isLiveStatus

/**
 * Agents worth a per-agent status call. The list already says whether an agent is finished, so a finished
 * agent is never asked about again; only live ones, or ones the list gave no status for, are.
 */
fun hydrateTargets(agents: List<AgentSummary>, limit: Int, only: Set<String>? = null): List<AgentSummary> =
    agents.take(limit).filter { agent ->
        if (only != null) agent.id in only
        else !agent.isArchived() && (agent.status.isNullOrBlank() || isLiveStatus(agent.status))
    }

/** Agents whose latest run is worth fetching again for its branch and PR: live ones, and ones never fetched. */
fun gitSnapTargets(agents: List<AgentSummary>, known: Set<String>, limit: Int = 10): List<AgentSummary> =
    agents
        .filter { agent ->
            !agent.latestRunId.isNullOrBlank() &&
                !agent.isArchived() &&
                (isLiveStatus(agent.status) || agent.id !in known)
        }
        .take(limit)
