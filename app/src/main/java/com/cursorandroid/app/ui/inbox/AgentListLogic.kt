package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.api.sortKey
import com.cursorandroid.app.ui.status.RunIndicator
import com.cursorandroid.app.ui.status.runIndicator
import com.cursorandroid.app.ui.status.shortRepo
import java.time.Instant

enum class CloudFilter {
    All,
    Running,
    NeedsAttention,
    Done,
    ;

    val label: String
        get() = when (this) {
            All -> "All"
            Running -> "Running"
            NeedsAttention -> "Needs attention"
            Done -> "Done"
        }
}

enum class HideFinishedAge(val days: Int, val label: String) {
    Off(0, "Off"),
    Day1(1, "1 day"),
    Day3(3, "3 days"),
    Day7(7, "7 days"),
    ;

    companion object {
        fun fromDays(days: Int): HideFinishedAge = entries.firstOrNull { it.days == days } ?: Off
    }
}

data class RepoGroup(
    val key: String,
    val label: String,
    val agents: List<AgentSummary>,
    val running: Int,
)

data class CloudArrangement(
    val pinned: List<AgentSummary>,
    val groups: List<RepoGroup>,
    val favorites: List<AgentSummary>,
    val rest: List<AgentSummary>,
    val hiddenFinished: Int,
)

fun repoGroupKey(url: String?): String = shortRepo(url)?.lowercase() ?: ""

fun repoGroupLabel(url: String?): String {
    val path = shortRepo(url) ?: return "No repo"
    return path.substringAfterLast('/').ifBlank { "No repo" }
}

fun cloudBucket(agent: AgentSummary, needsApproval: Boolean): CloudFilter {
    return when (runIndicator(agent.status, approvalPending = needsApproval)) {
        RunIndicator.Running -> CloudFilter.Running
        RunIndicator.NeedsApproval, RunIndicator.Failed -> CloudFilter.NeedsAttention
        RunIndicator.Done -> CloudFilter.Done
    }
}

fun arrangeCloudAgents(
    agents: List<AgentSummary>,
    git: Map<String, GitSnap>,
    pinnedAt: Map<String, Long>,
    favoriteIds: Set<String>,
    approvalIds: Set<String>,
    filter: CloudFilter,
    hideFinishedDays: Int,
    revealFinished: Boolean,
    groupByRepo: Boolean,
    nowMillis: Long,
): CloudArrangement {
    val matched = agents.filter { agent ->
        val bucket = cloudBucket(agent, agent.id in approvalIds)
        filter == CloudFilter.All || bucket == filter
    }
    val age = HideFinishedAge.fromDays(hideFinishedDays)
    val aged = matched.filter { agent ->
        finishedTooOld(agent, agent.id in approvalIds, age, nowMillis)
    }.map { it.id }.toSet()
    val shown = if (revealFinished) matched else matched.filter { it.id !in aged }
    val pinned = shown
        .filter { it.id in pinnedAt }
        .sortedWith(
            compareByDescending<AgentSummary> { pinnedAt[it.id] ?: 0L }
                .thenByDescending { it.sortKey() },
        )
    val pinnedIds = pinned.map { it.id }.toSet()
    val body = shown.filter { it.id !in pinnedIds }
    return if (groupByRepo) {
        CloudArrangement(
            pinned = pinned,
            groups = repoGroups(body, git, approvalIds),
            favorites = emptyList(),
            rest = emptyList(),
            hiddenFinished = aged.size,
        )
    } else {
        CloudArrangement(
            pinned = pinned,
            groups = emptyList(),
            favorites = body.filter { it.id in favoriteIds },
            rest = body.filter { it.id !in favoriteIds },
            hiddenFinished = aged.size,
        )
    }
}

fun repoGroups(
    agents: List<AgentSummary>,
    git: Map<String, GitSnap>,
    approvalIds: Set<String>,
): List<RepoGroup> {
    val buckets = LinkedHashMap<String, MutableList<AgentSummary>>()
    for (agent in agents) {
        val key = repoGroupKey(git[agent.id]?.repoUrl)
        buckets.getOrPut(key) { mutableListOf() }.add(agent)
    }
    return buckets.map { (key, members) ->
        val sorted = members.sortedByDescending { it.sortKey() }
        RepoGroup(
            key = key,
            label = repoGroupLabel(git[sorted.first().id]?.repoUrl),
            agents = sorted,
            running = sorted.count { cloudBucket(it, it.id in approvalIds) == CloudFilter.Running },
        )
    }.sortedByDescending { group -> group.agents.maxOf { it.sortKey() } }
}

internal fun finishedTooOld(
    agent: AgentSummary,
    needsApproval: Boolean,
    age: HideFinishedAge,
    nowMillis: Long,
): Boolean {
    if (age == HideFinishedAge.Off) return false
    if (cloudBucket(agent, needsApproval) != CloudFilter.Done) return false
    val iso = agent.updatedAt ?: agent.createdAt
    val then = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return false
    val cutoff = nowMillis - age.days * 86_400_000L
    return then < cutoff
}

internal fun cloudEmptyCopy(
    filter: CloudFilter,
    query: String,
    showHidden: Boolean,
    showArchived: Boolean,
): String {
    if (query.isNotBlank()) return "No chats match this search."
    if (showHidden && showArchived) {
        return "No hidden or archived chats. Turn them off in Settings."
    }
    if (showHidden) return "No hidden chats. Turn off Show hidden in Settings."
    if (showArchived) return "No archived chats. Turn off Show archived in Settings."
    return when (filter) {
        CloudFilter.Running -> "Nothing running."
        CloudFilter.NeedsAttention -> "Nothing needs attention."
        CloudFilter.Done -> "Nothing finished."
        CloudFilter.All -> "No agents yet. Start one on a cloud VM or a named machine."
    }
}
