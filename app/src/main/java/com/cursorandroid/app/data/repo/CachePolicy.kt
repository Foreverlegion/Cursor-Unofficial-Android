package com.cursorandroid.app.data.repo

internal data class CacheEntry(
    val key: String,
    val bytes: Long,
    val lastAccess: Long,
    val maxAgeMs: Long,
    val pinned: Boolean = false,
)

internal object CachePolicy {
    const val MB = 1024L * 1024L
    const val DEFAULT_CAP_MB = 50
    val CAP_CHOICES_MB = listOf(25, 50, 100, 200)
    const val DAY_MS = 24L * 60L * 60L * 1000L
    const val FINISHED_MAX_AGE_MS = 30L * DAY_MS
    const val GONE_MAX_AGE_MS = 7L * DAY_MS
    const val MAX_CONVERSATIONS = 300
    const val MAX_BRANCH_LISTS = 60

    /**
     * Keys to drop: anything unpinned past its max age, then least recently used
     * unpinned entries until the rest fit in [budgetBytes] and [maxEntries].
     */
    fun evict(
        entries: List<CacheEntry>,
        budgetBytes: Long,
        now: Long,
        maxEntries: Int = Int.MAX_VALUE,
    ): Set<String> {
        val out = LinkedHashSet<String>()
        entries.forEach { e ->
            if (!e.pinned && now - e.lastAccess > e.maxAgeMs) out += e.key
        }
        val rest = entries.filter { it.key !in out }
        var total = rest.sumOf { it.bytes.coerceAtLeast(0L) }
        var count = rest.size
        for (e in rest.filter { !it.pinned }.sortedBy { it.lastAccess }) {
            if (total <= budgetBytes && count <= maxEntries) break
            out += e.key
            total -= e.bytes.coerceAtLeast(0L)
            count -= 1
        }
        return out
    }
}
