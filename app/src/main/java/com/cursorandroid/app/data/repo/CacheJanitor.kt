package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Keeps on-device caches under a user-set size cap. Evictable: finished chats'
 * transcripts (with their artifact lists, usage and git rows) and image thumbs.
 * Pinned: live runs, chats with queued messages, favorites. Drafts, settings,
 * tokens and attachments waiting to send are never touched.
 */
class CacheJanitor(
    context: Context,
    private val conversations: ConversationStore,
    private val catalog: CatalogCache,
    private val artifacts: ArtifactHistoryStore,
    private val chats: LocalChatStore,
    private val drafts: DraftStore,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    var capMb: Int
        get() = prefs.getInt(CAP, CachePolicy.DEFAULT_CAP_MB)
        set(value) {
            prefs.edit { putInt(CAP, value.coerceIn(CachePolicy.CAP_CHOICES_MB.first(), CachePolicy.CAP_CHOICES_MB.last())) }
        }

    fun sizeBytes(): Long =
        conversations.stored().sumOf { it.second } + dirBytes(app.cacheDir) + prefsBytes()

    fun prune(now: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val known = catalog.agents().associateBy { it.id }
        val live = conversations.liveStatuses().keys
        val convs = conversations.stored()
        val entries = ArrayList<CacheEntry>(convs.size + 32)
        convs.forEach { (id, bytes, at) ->
            val agent = known[id]
            val gone = known.isNotEmpty() && (agent == null || agent.archived == true)
            entries += CacheEntry(
                key = CONV + id,
                bytes = bytes,
                lastAccess = at,
                maxAgeMs = if (gone) CachePolicy.GONE_MAX_AGE_MS else CachePolicy.FINISHED_MAX_AGE_MS,
                pinned = id in live || drafts.loadQueue(id).isNotEmpty() || chats.isFavorite(id),
            )
        }
        thumbFiles().forEach { f ->
            entries += CacheEntry(THUMB + f.name, f.length(), f.lastModified(), CachePolicy.FINISHED_MAX_AGE_MS)
        }
        val budget = capMb * CachePolicy.MB - prefsBytes()
        val evicted = CachePolicy.evict(entries, budget.coerceAtLeast(0L), now)
        val convCount = entries.count { it.key.startsWith(CONV) && it.key !in evicted }
        val overCount = if (convCount > CachePolicy.MAX_CONVERSATIONS) {
            CachePolicy.evict(
                entries.filter { it.key.startsWith(CONV) && it.key !in evicted },
                Long.MAX_VALUE,
                now,
                maxEntries = CachePolicy.MAX_CONVERSATIONS,
            )
        } else {
            emptySet()
        }
        val drop = evicted + overCount
        drop.forEach { key ->
            when {
                key.startsWith(CONV) -> forgetChat(key.removePrefix(CONV))
                key.startsWith(THUMB) -> File(thumbDir(), key.removePrefix(THUMB)).delete()
            }
        }
        val kept = conversations.stored().mapTo(HashSet()) { it.first }
        if (known.isNotEmpty()) {
            val keep = kept + known.keys
            catalog.pruneRows(keep, CachePolicy.MAX_BRANCH_LISTS)
            artifacts.agentIds().filter { it !in keep }.forEach { artifacts.remove(it) }
        }
        prefs.edit { putLong(LAST_PRUNE, now) }
        drop.size
    }

    /** Drops cached lists and finished transcripts. Live chats, drafts, queues and settings stay. */
    fun clear() = synchronized(lock) {
        val live = conversations.liveStatuses().keys
        conversations.stored().forEach { (id, _, _) ->
            if (id !in live && drafts.loadQueue(id).isEmpty() && !chats.isFavorite(id)) forgetChat(id)
        }
        artifacts.agentIds().filter { it !in live }.forEach { artifacts.remove(it) }
        catalog.clearAll()
        thumbFiles().forEach { it.delete() }
    }

    private fun forgetChat(id: String) {
        conversations.remove(id)
        artifacts.remove(id)
        catalog.forgetAgent(id)
    }

    private fun thumbDir() = File(app.cacheDir, "thumbs")

    private fun thumbFiles(): List<File> = thumbDir().listFiles()?.filter { it.isFile }.orEmpty()

    private fun prefsBytes(): Long {
        val dir = File(app.applicationInfo.dataDir, "shared_prefs")
        return CACHE_PREFS.sumOf { File(dir, "$it.xml").length() }
    }

    private fun dirBytes(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        private const val PREFS = "cache_policy"
        private const val CAP = "cap_mb"
        private const val LAST_PRUNE = "last_prune"
        private const val CONV = "conv:"
        private const val THUMB = "thumb:"
        private val CACHE_PREFS = listOf("catalog_cache", "catalog_cache_demo", "artifact_history", "conversation_store")
    }
}
