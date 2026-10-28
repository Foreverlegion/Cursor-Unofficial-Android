package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.sortKey
import com.cursorandroid.app.data.repo.FolderMembership
import com.cursorandroid.app.data.repo.LocalFolder

data class FolderBlock(
    val folder: LocalFolder,
    val groups: List<RepoGroup>,
    val chats: List<AgentSummary>,
    val running: Int = 0,
) {
    val count: Int get() = chats.size + groups.sumOf { it.agents.size }
}

data class FolderedCloud(
    val pinned: List<AgentSummary>,
    val folders: List<FolderBlock>,
    val groups: List<RepoGroup>,
    val favorites: List<AgentSummary>,
    val rest: List<AgentSummary>,
    val hiddenFinished: Int,
)

/**
 * Pulls repo groups and individual chats into local folders.
 * A chat with its own folder leaves its repo group. A chat whose folder matches the group's folder
 * stays inside that group. Starred folders stay above the others, in the order they were created.
 */
fun applyLocalFolders(
    arrangement: CloudArrangement,
    membership: FolderMembership,
    approvalIds: Set<String>,
): FolderedCloud {
    if (membership.folders.isEmpty()) {
        return FolderedCloud(
            pinned = arrangement.pinned,
            folders = emptyList(),
            groups = arrangement.groups,
            favorites = arrangement.favorites,
            rest = arrangement.rest,
            hiddenFinished = arrangement.hiddenFinished,
        )
    }
    val known = membership.folders.map { it.id }.toSet()
    val pinnedIds = arrangement.pinned.map { it.id }.toSet()
    fun chatFolder(id: String): String? = membership.chats[id]?.takeIf { it in known }
    fun groupFolder(key: String): String? = membership.groups[key]?.takeIf { it in known }

    val loose = LinkedHashMap<String, MutableList<AgentSummary>>()
    val grouped = LinkedHashMap<String, MutableList<RepoGroup>>()
    val plain = mutableListOf<RepoGroup>()

    for (group in arrangement.groups) {
        val home = groupFolder(group.key)
        val stay = mutableListOf<AgentSummary>()
        for (agent in group.agents + group.hiddenOlder) {
            if (agent.id in pinnedIds) continue
            val own = chatFolder(agent.id)
            when {
                own == null || own == home -> stay += agent
                else -> loose.getOrPut(own) { mutableListOf() }.add(agent)
            }
        }
        if (stay.isEmpty()) continue
        val hiddenIds = group.hiddenOlder.map { it.id }.toSet()
        val stayVisible = stay.filter { it.id !in hiddenIds }
        val stayHidden = stay.filter { it.id in hiddenIds }
        val next = group.copy(
            agents = stayVisible,
            hiddenOlder = stayHidden,
            running = stayVisible.count { cloudBucket(it, it.id in approvalIds) == CloudFilter.Running },
        )
        if (home != null) grouped.getOrPut(home) { mutableListOf() }.add(next) else plain += next
    }

    fun pull(list: List<AgentSummary>): List<AgentSummary> {
        val keep = mutableListOf<AgentSummary>()
        for (agent in list) {
            if (agent.id in pinnedIds) {
                keep += agent
                continue
            }
            val own = chatFolder(agent.id)
            if (own != null) loose.getOrPut(own) { mutableListOf() }.add(agent) else keep += agent
        }
        return keep
    }

    val favorites = pull(arrangement.favorites)
    val rest = pull(arrangement.rest)
    val ordered = membership.folders.filter { it.favorite } + membership.folders.filter { !it.favorite }
    val blocks = ordered.map { folder ->
        val blockGroups = grouped[folder.id].orEmpty()
        val blockChats = loose[folder.id].orEmpty().sortedByDescending { it.sortKey() }
        FolderBlock(
            folder = folder,
            groups = blockGroups,
            chats = blockChats,
            running = blockChats.count { cloudBucket(it, it.id in approvalIds) == CloudFilter.Running } +
                blockGroups.sumOf { it.running },
        )
    }
    return FolderedCloud(
        pinned = arrangement.pinned,
        folders = blocks,
        groups = plain,
        favorites = favorites,
        rest = rest,
        hiddenFinished = arrangement.hiddenFinished,
    )
}

/** Empty folders stay visible. A folder whose chats were filtered out does not, while a search is on. */
fun FolderBlock.shownInList(membership: FolderMembership, filtering: Boolean): Boolean {
    if (groups.isNotEmpty() || chats.isNotEmpty()) return true
    if (!filtering) return true
    val assigned = membership.groups.any { it.value == folder.id } || membership.chats.any { it.value == folder.id }
    return !assigned
}
