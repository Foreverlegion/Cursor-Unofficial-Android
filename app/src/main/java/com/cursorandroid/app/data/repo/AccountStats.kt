package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.AgentUsageRow
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.TokenUsage
import com.cursorandroid.app.data.api.WorkerPool
import com.cursorandroid.app.data.api.isWorking
import com.cursorandroid.app.data.api.sortKey
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.CancellationException

/**
 * A completed load replaces the cache, including a genuine empty list.
 * A failed load may fall back to a cache that already has rows.
 * A failed load with nothing cached throws, so the UI does not paint zeros
 * for a request that never returned.
 */
fun <T> pickFreshList(fresh: Result<List<T>>, cached: List<T>): List<T> {
    fresh.onSuccess { loaded -> return loaded }
    val error = fresh.exceptionOrNull()
    if (error is CancellationException) throw error
    if (cached.isNotEmpty()) return cached
    throw error ?: IllegalStateException("Couldn't load")
}

data class AccountCounts(
    val agentCount: Int,
    val runningCount: Int,
    val computerCount: Int,
    val computersOnline: Int,
    val poolCount: Int,
    val poolsConnected: Int,
    val repoCount: Int,
)

fun countRunning(agents: List<AgentSummary>): Int = agents.count { it.isWorking() }

fun accountCounts(
    agents: List<AgentSummary>,
    computers: List<Computer>,
    pools: List<WorkerPool>,
    repoCount: Int,
): AccountCounts {
    return AccountCounts(
        agentCount = agents.size,
        runningCount = countRunning(agents),
        computerCount = computers.size,
        computersOnline = computers.count { it.online },
        poolCount = pools.size,
        poolsConnected = pools.sumOf { it.connectedWorkerCount },
        repoCount = repoCount,
    )
}

@Serializable
data class UsageSample(
    val usage: TokenUsage,
    val sampledAgents: Int,
    val top: List<AgentUsageRow>,
    val windowDays: Int = USAGE_WINDOW_DAYS,
)

const val USAGE_WINDOW_DAYS = 30
const val USAGE_CHAT_CAP = 12

/**
 * Chats worth sampling for usage: active inside the window, newest first, capped.
 * The usage endpoint totals a whole chat, so this bounds how many chats are read.
 */
fun usageWindowAgents(
    agents: List<AgentSummary>,
    nowMillis: Long,
    days: Int = USAGE_WINDOW_DAYS,
    limit: Int = USAGE_CHAT_CAP,
): List<AgentSummary> {
    val cutoff = nowMillis - days * 86_400_000L
    return agents
        .mapNotNull { agent ->
            val at = runCatching { Instant.parse(agent.sortKey()).toEpochMilli() }.getOrNull()
            if (at != null && at >= cutoff) agent to at else null
        }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
}

fun TokenUsage.displayTotal(): Long {
    totalTokens?.let { return it }
    return (inputTokens ?: 0L) +
        (outputTokens ?: 0L) +
        (cacheWriteTokens ?: 0L) +
        (cacheReadTokens ?: 0L)
}

fun combineTokenUsage(rows: List<Pair<AgentSummary, TokenUsage>>): UsageSample {
    val total = rows.fold(TokenUsage()) { acc, row -> acc.plus(row.second) }
        .copy(totalTokens = rows.sumOf { it.second.displayTotal() })
    val top = rows
        .map { (agent, used) ->
            AgentUsageRow(
                id = agent.id,
                name = agent.name?.ifBlank { null } ?: agent.id,
                tokens = used.displayTotal(),
            )
        }
        .sortedByDescending { it.tokens }
        .take(8)
    return UsageSample(
        usage = total,
        sampledAgents = rows.size,
        top = top,
    )
}
