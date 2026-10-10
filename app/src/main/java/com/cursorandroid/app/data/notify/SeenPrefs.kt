package com.cursorandroid.app.data.notify

/** Pruning for per-run bookkeeping prefs, which otherwise grow with every run ever seen. */
internal object SeenPrefs {
    const val MAX_KEYS = 800

    fun staleKeys(
        keys: Set<String>,
        keep: Set<String>,
        max: Int = MAX_KEYS,
        protect: (String) -> Boolean = { false },
    ): Set<String> {
        if (keys.size <= max || keep.isEmpty()) return emptySet()
        return keys.filterTo(HashSet()) { it !in keep && !protect(it) }
    }
}
