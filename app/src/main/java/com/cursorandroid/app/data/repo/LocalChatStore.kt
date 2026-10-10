package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalChatStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val durable = app.getSharedPreferences(PREFS_DURABLE, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        recover()
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
        get() = durable.getBoolean(INBOX_WORKING, false)
        set(value) {
            durable.edit { putBoolean(INBOX_WORKING, value) }
        }

    var inboxShowArchived: Boolean
        get() = durable.getBoolean(INBOX_ARCHIVED, false)
        set(value) {
            durable.edit { putBoolean(INBOX_ARCHIVED, value) }
        }

    var inboxShowHidden: Boolean
        get() = durable.getBoolean(INBOX_HIDDEN, false)
        set(value) {
            durable.edit { putBoolean(INBOX_HIDDEN, value) }
        }

    var groupByRepo: Boolean
        get() = durable.getBoolean(GROUP_BY_REPO, true)
        set(value) {
            durable.edit { putBoolean(GROUP_BY_REPO, value) }
        }

    var compactCards: Boolean
        get() = durable.getBoolean(COMPACT_CARDS, true)
        set(value) {
            durable.edit { putBoolean(COMPACT_CARDS, value) }
        }

    var hideFinishedDays: Int
        get() {
            val days = durable.getInt(HIDE_FINISHED, 0)
            return if (days == 1 || days == 3 || days == 7) days else 0
        }
        set(value) {
            val days = if (value == 1 || value == 3 || value == 7) value else 0
            durable.edit { putInt(HIDE_FINISHED, days) }
        }

    var collapsedRepos: Set<String>
        get() = durable.getStringSet(COLLAPSED_REPOS, emptySet())?.toSet() ?: emptySet()
        set(value) {
            durable.edit { putStringSet(COLLAPSED_REPOS, value.toHashSet()) }
        }

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
        val encoded = json.encodeToString(all)
        prefs.edit { putString(ALL, encoded) }
        durable.edit { putString(MIRROR, encoded) }
    }

    private fun loadAll(): Map<String, ChatMeta> {
        val raw = prefs.getString(ALL, null)
            ?: durable.getString(MIRROR, null)
            ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, ChatMeta>>(raw) }
            .getOrDefault(emptyMap())
    }

    private fun recover() {
        val found = loadAll()
        if (found.isNotEmpty()) persist(found)
    }

    companion object {
        private const val PREFS = "local_chats"
        private const val PREFS_DURABLE = "cursor_prefs"
        private const val ALL = "meta"
        private const val MIRROR = "chat_meta"
        private const val INBOX_WORKING = "inbox_working_only"
        private const val INBOX_ARCHIVED = "inbox_archived_view"
        private const val INBOX_HIDDEN = "inbox_show_hidden"
        private const val GROUP_BY_REPO = "agent_group_by_repo"
        private const val COMPACT_CARDS = "agent_compact_cards"
        private const val HIDE_FINISHED = "agent_hide_finished_days"
        private const val COLLAPSED_REPOS = "agent_collapsed_repos"
    }
}

@Serializable
data class ChatMeta(
    val title: String? = null,
    val favorite: Boolean = false,
    val favoritedAt: Long = 0L,
    val hidden: Boolean = false,
    val muted: Boolean = false,
    val repoUrl: String? = null,
    val baseBranch: String? = null,
    val startSha: String? = null,
    val ignoredRemoteSha: String? = null,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0L,
    val openFinishedPr: Boolean = false,
)
