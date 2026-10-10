package com.cursorandroid.app.data.repo

import kotlinx.serialization.Serializable

/** Local look for one repo group in the agent list. Keyed by the group key (the repo path, lowercase). */
@Serializable
data class RepoGroupStyle(
    val name: String? = null,
    val favorite: Boolean = false,
    val color: Int = 0,
) {
    val isDefault: Boolean get() = name.isNullOrBlank() && !favorite && color == 0
}

@Serializable
data class RepoGroupPrefs(
    val styles: Map<String, RepoGroupStyle> = emptyMap(),
    val order: List<String> = emptyList(),
) {
    fun style(key: String): RepoGroupStyle = styles[key] ?: RepoGroupStyle()

    fun withStyle(key: String, change: (RepoGroupStyle) -> RepoGroupStyle): RepoGroupPrefs {
        val next = change(style(key)).let { it.copy(name = it.name?.trim()?.takeIf { n -> n.isNotEmpty() }) }
        val map = LinkedHashMap(styles)
        if (next.isDefault) map.remove(key) else map[key] = next
        return copy(styles = map)
    }

    fun withOrder(keys: List<String>): RepoGroupPrefs = copy(order = keys.distinct())

    fun resetOrder(): RepoGroupPrefs = copy(order = emptyList())

    val isEmpty: Boolean get() = styles.isEmpty() && order.isEmpty()
}

enum class GroupMove { Top, Up, Down }

/**
 * Returns the full display order after moving [key]. Favorites stay above the rest, so a group only
 * moves inside its own section.
 */
fun moveGroupKey(
    displayKeys: List<String>,
    favorites: Set<String>,
    key: String,
    move: GroupMove,
): List<String> {
    val index = displayKeys.indexOf(key)
    if (index < 0) return displayKeys
    val section = favorites.contains(key)
    val peers = displayKeys.filter { favorites.contains(it) == section }
    val at = peers.indexOf(key)
    val target = when (move) {
        GroupMove.Top -> 0
        GroupMove.Up -> at - 1
        GroupMove.Down -> at + 1
    }
    if (target < 0 || target >= peers.size || target == at) return displayKeys
    val reordered = peers.toMutableList().apply {
        removeAt(at)
        add(target, key)
    }
    val others = displayKeys.filter { favorites.contains(it) != section }
    return if (section) reordered + others else others + reordered
}
