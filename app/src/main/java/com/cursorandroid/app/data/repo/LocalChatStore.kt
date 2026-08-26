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
        private const val INBOX_ARCHIVED = "inbox_show_archived"
    }
}

@Serializable
data class ChatMeta(
    val title: String? = null,
    val favorite: Boolean = false,
    val favoritedAt: Long = 0L,
    val repoUrl: String? = null,
    val baseBranch: String? = null,
    val startSha: String? = null,
    val ignoredRemoteSha: String? = null,
)
