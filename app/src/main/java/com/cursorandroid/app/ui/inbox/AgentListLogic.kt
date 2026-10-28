package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.api.sortKey
import com.cursorandroid.app.data.repo.FolderMembership
import com.cursorandroid.app.data.repo.RepoGroupPrefs
import com.cursorandroid.app.data.repo.DEFAULT_HIDE_FINISHED_DAYS
import com.cursorandroid.app.data.repo.normalizeHideFinishedDays
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
    Day14(14, "14 days"),
    Week4(28, "4 weeks"),
    Week8(56, "8 weeks"),
    Week12(84, "12 weeks"),
    ;

    companion object {
        const val DEFAULT_DAYS = DEFAULT_HIDE_FINISHED_DAYS

        val settingsChoices: List<HideFinishedAge> =
            listOf(Off, Day1, Day3, Day7, Day14, Week4, Week8, Week12)
            val normalized = normalizeHideFinishedDays(days)
            return entries.firstOrNull { it.days == normalized } ?: Off
        }

        fun normalizeDays(days: Int): Int = normalizeHideFinishedDays(days)
    }
}

data class RepoGroup(
    val key: String,
    val label: String,
    val agents: List<AgentSummary>,
    val running: Int,
    val hiddenOlder: List<AgentSummary> = emptyList(),
    val path: String = "",
    val customName: String? = null,
    val favorite: Boolean = false,
    val color: Int = 0,
) {
    val title: String get() = customName ?: label
}

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

/**
 * Fills a missing repo on each git snapshot. Newer agents often have no run git yet, so the repo
 * comes from the Get Agent record, then from what the app stored when it launched the chat.
 */
fun withRepoFallbacks(
    git: Map<String, GitSnap>,
    agentRepos: Map<String, String>,
    chatRepos: Map<String, String?>,
): Map<String, GitSnap> {
    val out = LinkedHashMap(git)
    val ids = git.keys + agentRepos.keys + chatRepos.keys
    for (id in ids) {
        val snap = git[id]
        if (!snap?.repoUrl.isNullOrBlank()) continue
        val repo = agentRepos[id]?.takeIf { it.isNotBlank() } ?: chatRepos[id]?.takeIf { it.isNotBlank() } ?: continue
        out[id] = (snap ?: GitSnap(agentId = id)).copy(repoUrl = repo)
    }
    return out
}

fun cloudBucket(agent: AgentSummary, needsApproval: Boolean): CloudFilter {
    return when (runIndicator(agent.status, agent.runStatus, needsApproval)) {
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
    groupPrefs: RepoGroupPrefs = RepoGroupPrefs(),
    folders: FolderMembership = FolderMembership(),
    unreadAgentIds: Set<String> = emptySet(),
    applyAgeFilter: Boolean = true,
): CloudArrangement {
    val matched = agents.filter { agent ->
        val bucket = cloudBucket(agent, agent.id in approvalIds)
        filter == CloudFilter.All || bucket == filter
    }
    val age = HideFinishedAge.fromDays(hideFinishedDays)
    val pinnedIds = pinnedAt.keys
    // Starred repos and starred folders stay in the list on every filter. Age overflow is not a hide.
    val protected = ageProtectedIds(matched, git, groupPrefs, folders)
    fun tooOld(agent: AgentSummary): Boolean {
        if (!applyAgeFilter || age == HideFinishedAge.Off) return false
        if (agent.id in protected) return false
        return agentTooOldForList(
            agent = agent,
            needsApproval = agent.id in approvalIds,
            age = age,
            nowMillis = nowMillis,
            pinnedIds = pinnedIds,
            favoriteIds = favoriteIds,
            unreadAgentIds = unreadAgentIds,
        )
    }
    val aged = matched.filter(::tooOld).map { it.id }.toSet()
    val revealAged = revealFinished || !applyAgeFilter
    val shown = if (revealAged) matched else matched.filter { it.id !in aged }
    val pinned = shown
        .filter { it.id in pinnedAt }
        .sortedWith(
            compareByDescending<AgentSummary> { pinnedAt[it.id] ?: 0L }
                .thenByDescending { it.sortKey() },
        )
    val pinnedShownIds = pinned.map { it.id }.toSet()
    val body = shown.filter { it.id !in pinnedShownIds }
    val bodyAll = matched.filter { it.id !in pinnedShownIds }
    return if (groupByRepo) {
        val groups = repoGroups(
            agents = if (revealAged) body else bodyAll,
            git = git,
            approvalIds = approvalIds,
            prefs = groupPrefs,
            tooOld = if (revealAged) { _ -> false } else ::tooOld,
        )
        val hiddenFinished = if (revealAged) 0 else groups.sumOf { it.hiddenOlder.size }
        CloudArrangement(
            pinned = pinned,
            groups = groups,
            favorites = emptyList(),
            rest = emptyList(),
            hiddenFinished = hiddenFinished,
        )
    } else {
        CloudArrangement(
            pinned = pinned,
            groups = emptyList(),
            favorites = body.filter { it.id in favoriteIds }.starredFirst(protected),
            rest = body.filter { it.id !in favoriteIds }.starredFirst(protected),
            hiddenFinished = if (revealAged) 0 else aged.size,
        )
    }
}

/**
 * Chats that belong to a starred repo, or to a starred folder, are not eligible for the
 * "Show N hidden" age collapse. An explicit Hide on the chat is applied earlier and still wins.
 */
internal fun ageProtectedIds(
    agents: List<AgentSummary>,
    git: Map<String, GitSnap>,
    groupPrefs: RepoGroupPrefs,
    folders: FolderMembership,
): Set<String> {
    if (agents.isEmpty()) return emptySet()
    val starredRepos = groupPrefs.styles.filterValues { it.favorite }.keys
    val starredFolders = folders.folders.filter { it.favorite }.map { it.id }.toSet()
    if (starredRepos.isEmpty() && starredFolders.isEmpty()) return emptySet()
    val knownFolders = folders.folders.map { it.id }.toSet()
    return agents.mapNotNull { agent ->
        val repo = repoGroupKey(git[agent.id]?.repoUrl)
        if (repo in starredRepos) return@mapNotNull agent.id
        val folder = folders.chats[agent.id]?.takeIf { it in knownFolders }
            ?: folders.groups[repo]?.takeIf { it in knownFolders }
        if (folder != null && folder in starredFolders) agent.id else null
    }.toSet()
}

private fun List<AgentSummary>.starredFirst(protected: Set<String>): List<AgentSummary> {
    if (protected.isEmpty()) return this
    val (hit, miss) = partition { it.id in protected }
    return hit + miss
}

fun repoGroups(
    agents: List<AgentSummary>,
    git: Map<String, GitSnap>,
    approvalIds: Set<String>,
    prefs: RepoGroupPrefs = RepoGroupPrefs(),
    tooOld: (AgentSummary) -> Boolean = { false },
): List<RepoGroup> {
    val buckets = LinkedHashMap<String, MutableList<AgentSummary>>()
    for (agent in agents) {
        val key = repoGroupKey(git[agent.id]?.repoUrl)
        buckets.getOrPut(key) { mutableListOf() }.add(agent)
    }
    val byActivity = buckets.map { (key, members) ->
        val sorted = members.sortedByDescending { it.sortKey() }
        val visible = sorted.filterNot(tooOld)
        val hiddenOlder = sorted.filter(tooOld)
        val url = git[sorted.first().id]?.repoUrl
        val style = prefs.style(key)
        RepoGroup(
            key = key,
            label = repoGroupLabel(url),
            agents = visible,
            hiddenOlder = hiddenOlder,
            running = visible.count { cloudBucket(it, it.id in approvalIds) == CloudFilter.Running },
            path = shortRepo(url).orEmpty(),
            customName = style.name?.takeIf { it.isNotBlank() },
            favorite = style.favorite,
            color = style.color,
        )
    }.sortedByDescending { group ->
        (group.agents + group.hiddenOlder).maxOfOrNull { it.sortKey() } ?: 0L
    }
    val manual = if (prefs.order.isEmpty()) {
        byActivity
    } else {
        val rank = prefs.order.withIndex().associate { it.value to it.index }
        byActivity.sortedBy { rank[it.key] ?: Int.MAX_VALUE }
    }
    return manual.filter { it.favorite } + manual.filterNot { it.favorite }
}

internal fun agentActivityMillis(agent: AgentSummary): Long? {
    val iso = agent.updatedAt ?: agent.createdAt ?: return null
    return runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
}

internal fun agentTooOldForList(
    agent: AgentSummary,
    needsApproval: Boolean,
    age: HideFinishedAge,
    nowMillis: Long,
    pinnedIds: Set<String>,
    favoriteIds: Set<String>,
    unreadAgentIds: Set<String>,
): Boolean {
    if (age == HideFinishedAge.Off) return false
    if (agent.id in pinnedIds) return false
    if (agent.id in favoriteIds) return false
    if (agent.id in unreadAgentIds) return false
    when (cloudBucket(agent, needsApproval)) {
        CloudFilter.Running, CloudFilter.NeedsAttention -> return false
        else -> Unit
    }
    val then = agentActivityMillis(agent) ?: return false
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
