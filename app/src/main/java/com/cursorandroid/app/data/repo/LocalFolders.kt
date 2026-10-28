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
    /** 0 keeps the theme color. Any other value is an opaque ARGB accent for the folder name. */
    @SerialName("color") val color: Int = 0,
    /** Empty keeps the folder icon. Any other value is an id from LocalFolderStore.ICON_IDS. */
    @SerialName("icon") val icon: String = "",
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

    fun setColor(id: String, color: Int) {
        if (color != 0 && (color ushr 24) == 0) return
        update(id) { it.copy(color = color) }
    }

    fun setIcon(id: String, icon: String) {
        val next = icon.trim()
        if (next.isNotEmpty() && next !in ICON_IDS) return
        update(id) { it.copy(icon = next) }
    }

    fun replace(value: FolderMembership) = persist(value)

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

        /** Mid-tone presets so a folder name stays readable on a light or dark background. */
        val NAME_COLORS = listOf(
            0xFFEF4444.toInt(),
            0xFFFB923C.toInt(),
            0xFFCA8A04.toInt(),
            0xFF16A34A.toInt(),
            0xFF0F766E.toInt(),
            0xFF2563EB.toInt(),
            0xFF7C3AED.toInt(),
            0xFFDB2777.toInt(),
        )

        val ICON_CHOICES = listOf(
            "work",
            "code",
            "bug",
            "build",
            "cloud",
            "home",
            "science",
            "terminal",
            "bookmark",
            "inventory",
            "hub",
            "memory",
            "public",
            "lock",
            "label",
            "tree",
            "storage",
        )

        val ICON_IDS = ICON_CHOICES.toSet()

        private fun randomSuffix(): String = (0..4).joinToString("") {
            ('a'..'z').random().toString()
        }
    }
}
