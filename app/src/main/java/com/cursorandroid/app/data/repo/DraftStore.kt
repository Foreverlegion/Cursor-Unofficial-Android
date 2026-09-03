package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DraftAttach(
    val path: String,
    val name: String,
    val mime: String,
)

@Serializable
data class ChatDraft(
    val text: String = "",
    val mode: String = "",
    val modelId: String = "",
    val attaches: List<DraftAttach> = emptyList(),
    val envType: String = "",
    val envName: String = "",
    val provider: String = "",
    val repoUrl: String = "",
    val startingRef: String = "",
    val autoPr: Boolean? = null,
    val subName: String = "",
    val subDesc: String = "",
    val subPrompt: String = "",
    val agentName: String = "",
) {
    fun isEmpty(): Boolean {
        return text.isBlank() &&
            mode.isBlank() &&
            modelId.isBlank() &&
            attaches.isEmpty() &&
            envType.isBlank() &&
            envName.isBlank() &&
            provider.isBlank() &&
            repoUrl.isBlank() &&
            startingRef.isBlank() &&
            autoPr == null &&
            subName.isBlank() &&
            subDesc.isBlank() &&
            subPrompt.isBlank() &&
            agentName.isBlank()
    }

    fun toItems(): List<AttachItem> {
        return attaches.map { Attachments.fromCache(it.path, it.name, it.mime) }
    }
}

class DraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(id: String): ChatDraft {
        val raw = prefs.getString(key(id), null) ?: return ChatDraft()
        return runCatching { json.decodeFromString<ChatDraft>(raw) }.getOrDefault(ChatDraft())
    }

    fun save(id: String, draft: ChatDraft) {
        if (draft.isEmpty()) {
            prefs.edit { remove(key(id)) }
            return
        }
        prefs.edit { putString(key(id), json.encodeToString(draft)) }
    }

    fun clear(id: String) {
        prefs.edit { remove(key(id)) }
    }

    fun clearQueue(id: String) {
        prefs.edit { remove(queueKey(id)) }
    }

    fun exportAll(): Map<String, ChatDraft> {
        return prefs.all.mapNotNull { (name, value) ->
            if (!name.startsWith(PREFIX) || value !is String) return@mapNotNull null
            val draft = runCatching { json.decodeFromString<ChatDraft>(value) }.getOrNull()
            if (draft == null || draft.isEmpty()) null else name.removePrefix(PREFIX) to draft.copy(attaches = emptyList())
        }.toMap()
    }

    fun importAll(items: Map<String, ChatDraft>) {
        items.forEach { (id, draft) -> save(id, draft.copy(attaches = emptyList())) }
    }

    fun loadQueue(agentId: String): List<QueuedItem> {
        val raw = prefs.getString(queueKey(agentId), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<QueuedItem>>(raw) }.getOrDefault(emptyList())
    }

    fun saveQueue(agentId: String, items: List<QueuedItem>) {
        if (items.isEmpty()) {
            prefs.edit { remove(queueKey(agentId)) }
            return
        }
        prefs.edit { putString(queueKey(agentId), json.encodeToString(items)) }
    }

    companion object {
        const val NEW_AGENT = "new"
        private const val PREFS = "draft_store"
        private const val PREFIX = "d_"
        private const val QUEUE_PREFIX = "q_"
        private fun key(id: String) = PREFIX + id
        private fun queueKey(id: String) = QUEUE_PREFIX + id
    }
}

@Serializable
data class QueuedItem(
    val id: String,
    val text: String,
    val attaches: List<DraftAttach> = emptyList(),
    val caption: String = "",
)

fun List<AttachItem>.toDraft(): List<DraftAttach> {
    return mapNotNull { item ->
        val path = item.cachePath ?: return@mapNotNull null
        DraftAttach(path, item.name, item.mime)
    }
}

fun List<DraftAttach>.toItems(): List<AttachItem> {
    return map { Attachments.fromCache(it.path, it.name, it.mime) }
}
