package com.cursorandroid.app.data.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * App-only folders for the agent list. Nothing here is sent to Cursor.
 * The record lives in the same versioned preferences file as pins and repo groups, so an update
 * keeps it. Deleting a folder only removes the grouping.
 */
@Serializable
data class LocalFolder(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("collapsed") val collapsed: Boolean = false,
    @SerialName("favorite") val favorite: Boolean = false,
)

@Serializable
data class FolderMembership(
    @SerialName("folders") val folders: List<LocalFolder> = emptyList(),
    @SerialName("chats") val chats: Map<String, String> = emptyMap(),
    @SerialName("groups") val groups: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = folders.isEmpty() && chats.isEmpty() && groups.isEmpty()
}

class LocalFolderStore(
    private val ui: UiPrefsStore,
    private val newId: () -> String = { "fld-${System.currentTimeMillis().toString(36)}-${randomSuffix()}" },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun snapshot(): FolderMembership = load()

    fun create(name: String): LocalFolder? {
        val trimmed = name.trim().take(NAME_MAX)
        if (trimmed.isEmpty()) return null
        val current = load()
        if (current.folders.any { it.name.equals(trimmed, ignoreCase = true) }) return null
        val folder = LocalFolder(id = newId(), name = trimmed, createdAt = now())
        persist(current.copy(folders = current.folders + folder))
        return folder
    }

    fun rename(id: String, name: String): Boolean {
        val trimmed = name.trim().take(NAME_MAX)
        if (trimmed.isEmpty()) return false
        val current = load()
        if (current.folders.none { it.id == id }) return false
        if (current.folders.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) return false
        persist(current.copy(folders = current.folders.map { if (it.id == id) it.copy(name = trimmed) else it }))
        return true
    }

    fun setCollapsed(id: String, collapsed: Boolean) {
        update(id) { it.copy(collapsed = collapsed) }
    }

    fun setFavorite(id: String, favorite: Boolean) {
        update(id) { it.copy(favorite = favorite) }
    }

    /** Removes the folder. Chats and repo groups go back to the main list. */
    fun delete(id: String) {
        val current = load()
        if (current.folders.none { it.id == id }) return
        persist(
            current.copy(
                folders = current.folders.filterNot { it.id == id },
                chats = current.chats.filterValues { it != id },
                groups = current.groups.filterValues { it != id },
            ),
        )
    }

    fun moveChats(ids: Collection<String>, folderId: String?) {
        if (ids.isEmpty()) return
        val current = load()
        if (folderId != null && current.folders.none { it.id == folderId }) return
        val next = current.chats.toMutableMap()
        for (id in ids) {
            if (folderId == null) next.remove(id) else next[id] = folderId
        }
        persist(current.copy(chats = next))
    }

    fun moveGroup(key: String, folderId: String?) {
        if (key.isBlank()) return
        val current = load()
        if (folderId != null && current.folders.none { it.id == folderId }) return
        val next = current.groups.toMutableMap()
        if (folderId == null) next.remove(key) else next[key] = folderId
        persist(current.copy(groups = next))
    }

    private fun update(id: String, block: (LocalFolder) -> LocalFolder) {
        val current = load()
        if (current.folders.none { it.id == id }) return
        persist(current.copy(folders = current.folders.map { if (it.id == id) block(it) else it }))
    }

    private fun persist(value: FolderMembership) {
        ui.put(UiKeys.agentFolders, json.encodeToString(value))
        ui.flush()
    }

    private fun load(): FolderMembership {
        val raw = ui[UiKeys.agentFolders] ?: return FolderMembership()
        return runCatching { json.decodeFromString<FolderMembership>(raw) }.getOrDefault(FolderMembership())
    }

    companion object {
        const val NAME_MAX = 60

        private fun randomSuffix(): String = (0..4).joinToString("") {
            ('a'..'z').random().toString()
        }
    }
}
