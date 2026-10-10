package com.cursorandroid.app.data.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalChatStore(private val ui: UiPrefsStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun snapshot(): Map<String, ChatMeta> = loadAll()

    fun replaceAll(items: Map<String, ChatMeta>) {
        persist(items)
    }

    fun mergeAll(items: Map<String, ChatMeta>) {
        if (items.isEmpty()) return
        persist(loadAll() + items)
    }

    fun meta(agentId: String): ChatMeta = loadAll()[agentId] ?: ChatMeta()

    fun title(agentId: String): String? = loadAll()[agentId]?.title?.takeIf { it.isNotBlank() }

    fun displayName(agentId: String, fallback: String?): String {
        return title(agentId) ?: fallback?.takeIf { it.isNotBlank() } ?: agentId
    }

    fun setTitle(agentId: String, title: String?) {
        val trimmed = title?.trim()?.takeIf { it.isNotEmpty() }
        update(agentId) { it.copy(title = trimmed) }
    }

    fun setRepoBase(agentId: String, repoUrl: String?, baseBranch: String?, startSha: String?) {
        update(agentId) {
            it.copy(
                repoUrl = repoUrl?.trim()?.takeIf { value -> value.isNotEmpty() },
                baseBranch = baseBranch?.trim()?.takeIf { value -> value.isNotEmpty() },
                startSha = startSha?.trim()?.takeIf { value -> value.isNotEmpty() },
            )
        }
    }

    fun ignoreRemote(agentId: String, sha: String?) {
        update(agentId) {
            it.copy(ignoredRemoteSha = sha?.trim()?.takeIf { value -> value.isNotEmpty() })
        }
    }

    fun isFavorite(agentId: String): Boolean = loadAll()[agentId]?.favorite == true

    fun setFavorite(agentId: String, favorite: Boolean) {
        update(agentId) {
            it.copy(
                favorite = favorite,
                favoritedAt = if (favorite) System.currentTimeMillis() else 0L,
            )
        }
    }

    fun remove(agentId: String) {
        val all = loadAll().toMutableMap()
        if (all.remove(agentId) != null) persist(all)
    }

    fun toggleFavorite(agentId: String): Boolean {
        val next = !isFavorite(agentId)
        setFavorite(agentId, next)
        return next
    }

    fun isHidden(agentId: String): Boolean = loadAll()[agentId]?.hidden == true

    fun setHidden(agentId: String, hidden: Boolean) {
        update(agentId) { it.copy(hidden = hidden) }
    }

    fun isMuted(agentId: String): Boolean = loadAll()[agentId]?.muted == true

    fun setMuted(agentId: String, muted: Boolean) {
        update(agentId) { it.copy(muted = muted) }
    }

    fun toggleMuted(agentId: String): Boolean {
        val next = !isMuted(agentId)
        setMuted(agentId, next)
        return next
    }

    fun favoriteIds(): List<String> {
        return loadAll().entries
            .filter { it.value.favorite }
            .sortedByDescending { it.value.favoritedAt }
            .map { it.key }
    }

    var inboxWorkingOnly: Boolean
        get() = ui[UiKeys.inboxWorkingOnly] ?: false
        set(value) = ui.put(UiKeys.inboxWorkingOnly, value)

    var inboxShowArchived: Boolean
        get() = ui[UiKeys.inboxShowArchived] ?: false
        set(value) = ui.put(UiKeys.inboxShowArchived, value)

    var inboxShowHidden: Boolean
        get() = ui[UiKeys.inboxShowHidden] ?: false
        set(value) = ui.put(UiKeys.inboxShowHidden, value)

    var groupByRepo: Boolean
        get() = ui[UiKeys.groupByRepo] ?: true
        set(value) = ui.put(UiKeys.groupByRepo, value)

    var compactCards: Boolean
        get() = ui[UiKeys.compactCards] ?: true
        set(value) = ui.put(UiKeys.compactCards, value)

    var hideFinishedDays: Int
        get() {
            val days = ui[UiKeys.hideFinishedDays] ?: 0
            return if (days == 1 || days == 3 || days == 7) days else 0
        }
        set(value) {
            val days = if (value == 1 || value == 3 || value == 7) value else 0
            ui.put(UiKeys.hideFinishedDays, days)
        }

    var collapsedRepos: Set<String>
        get() = ui[UiKeys.collapsedRepos]?.toSet() ?: emptySet()
        set(value) = ui.put(UiKeys.collapsedRepos, value.toSet())

    var repoGroupPrefs: RepoGroupPrefs
        get() {
            val raw = ui[UiKeys.repoGroupPrefs] ?: return RepoGroupPrefs()
            return runCatching { json.decodeFromString<RepoGroupPrefs>(raw) }.getOrDefault(RepoGroupPrefs())
        }
        set(value) = ui.put(UiKeys.repoGroupPrefs, json.encodeToString(value))

    fun setOpenFinishedPr(agentId: String, open: Boolean) {
        update(agentId) { it.copy(openFinishedPr = open) }
    }

    fun claimFinishedPr(snaps: Map<String, com.cursorandroid.app.data.api.GitSnap>): String? {
        val hit = loadAll().entries.firstOrNull { (id, meta) ->
            meta.openFinishedPr && !snaps[id]?.prUrl.isNullOrBlank()
        } ?: return null
        update(hit.key) { it.copy(openFinishedPr = false) }
        return snaps[hit.key]?.prUrl?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setPinned(agentId: String, pinned: Boolean) {
        update(agentId) {
            it.copy(
                pinned = pinned,
                pinnedAt = if (pinned) System.currentTimeMillis() else 0L,
            )
        }
    }

    private fun update(agentId: String, block: (ChatMeta) -> ChatMeta) {
        val all = loadAll().toMutableMap()
        all[agentId] = block(all[agentId] ?: ChatMeta())
        if (all[agentId] == ChatMeta()) {
            all.remove(agentId)
        }
        persist(all)
    }

    private fun persist(all: Map<String, ChatMeta>) {
        ui.put(UiKeys.chatMeta, json.encodeToString(all))
    }

    private fun loadAll(): Map<String, ChatMeta> {
        val raw = ui[UiKeys.chatMeta] ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, ChatMeta>>(raw) }.getOrElse {
            if (ui[UiKeys.chatMetaUnreadable] == null) ui.put(UiKeys.chatMetaUnreadable, raw)
            emptyMap()
        }
    }
}

@Serializable
data class ChatMeta(
    @SerialName("title") val title: String? = null,
    @SerialName("favorite") val favorite: Boolean = false,
    @SerialName("favoritedAt") val favoritedAt: Long = 0L,
    @SerialName("hidden") val hidden: Boolean = false,
    @SerialName("muted") val muted: Boolean = false,
    @SerialName("repoUrl") val repoUrl: String? = null,
    @SerialName("baseBranch") val baseBranch: String? = null,
    @SerialName("startSha") val startSha: String? = null,
    @SerialName("ignoredRemoteSha") val ignoredRemoteSha: String? = null,
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("pinnedAt") val pinnedAt: Long = 0L,
    @SerialName("openFinishedPr") val openFinishedPr: Boolean = false,
)
