package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import com.cursorandroid.app.data.api.ArtifactItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class ArtifactHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun ingest(agentId: String, incoming: List<ArtifactItem>): ArtifactItem? {
        val now = System.currentTimeMillis()
        val current = load(agentId)
        val merged = LinkedHashMap<String, ArtifactRecord>()
        for (item in ArtifactHistoryLogic.prune(current.items, now)) {
            merged[item.path] = item
        }
        for (item in incoming) {
            val existing = merged[item.path]
            merged[item.path] = ArtifactRecord(
                path = item.path,
                sizeBytes = item.sizeBytes ?: existing?.sizeBytes,
                updatedAt = item.updatedAt ?: existing?.updatedAt,
                seenAt = now,
            )
        }
        val kept = ArtifactHistoryLogic.keepLatest(merged.values.toList())
        var pin = current.pin
        if (incoming.isNotEmpty()) {
            val latest = ArtifactHistoryLogic.latest(incoming)
            val key = ArtifactHistoryLogic.key(latest)
            if (pin == null || ArtifactHistoryLogic.key(pin) != key) {
                pin = ArtifactPin(latest.path, latest.updatedAt, visible = true)
            }
        }
        if (pin != null && kept.none { it.path == pin.path }) {
            pin = pin.copy(visible = false)
        }
        save(agentId, AgentArtifacts(kept, pin))
        return visibleOf(kept, pin)
    }

    fun hideLatest(agentId: String) {
        val current = load(agentId)
        val pin = current.pin ?: return
        save(agentId, current.copy(pin = pin.copy(visible = false)))
    }

    fun visible(agentId: String): ArtifactItem? {
        val current = load(agentId)
        return visibleOf(current.items, current.pin)
    }

    fun remove(agentId: String) {
        prefs.edit { remove(key(agentId)) }
    }

    fun history(agentId: String): List<ArtifactItem> {
        val now = System.currentTimeMillis()
        val current = load(agentId)
        val kept = ArtifactHistoryLogic.prune(current.items, now)
        if (kept.size != current.items.size) {
            save(agentId, current.copy(items = kept))
        }
        return kept.map { it.toItem() }
    }

    private fun visibleOf(items: List<ArtifactRecord>, pin: ArtifactPin?): ArtifactItem? {
        if (pin == null || !pin.visible) return null
        return items.firstOrNull { it.path == pin.path }?.toItem()
    }

    private fun load(agentId: String): AgentArtifacts {
        val raw = prefs.getString(key(agentId), null) ?: return AgentArtifacts()
        return runCatching { json.decodeFromString<AgentArtifacts>(raw) }.getOrDefault(AgentArtifacts())
    }

    private fun save(agentId: String, value: AgentArtifacts) {
        if (value.items.isEmpty() && value.pin == null) {
            prefs.edit { remove(key(agentId)) }
            return
        }
        prefs.edit { putString(key(agentId), json.encodeToString(value)) }
    }

    companion object {
        private const val PREFS = "artifact_history"
        private const val PREFIX = "a_"
        private fun key(id: String) = PREFIX + id
    }
}

internal object ArtifactHistoryLogic {
    const val MAX_PER_AGENT = 8
    const val TTL_MS = 3L * 24 * 60 * 60 * 1000

    fun prune(items: List<ArtifactRecord>, now: Long): List<ArtifactRecord> {
        val cutoff = now - TTL_MS
        return keepLatest(items.filter { it.seenAt >= cutoff })
    }

    fun keepLatest(items: List<ArtifactRecord>): List<ArtifactRecord> {
        return items
            .sortedWith(
                compareByDescending<ArtifactRecord> { it.sortMillis() }
                    .thenByDescending { it.seenAt },
            )
            .take(MAX_PER_AGENT)
    }

    fun latest(items: List<ArtifactItem>): ArtifactItem {
        return items.maxWith(
            compareBy<ArtifactItem> { sortMillis(it.updatedAt) }.thenBy { it.path },
        )
    }

    fun key(item: ArtifactItem): String = "${item.path}|${item.updatedAt.orEmpty()}"

    fun key(pin: ArtifactPin): String = "${pin.path}|${pin.updatedAt.orEmpty()}"

    fun sortMillis(updatedAt: String?): Long {
        if (updatedAt.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(updatedAt).toEpochMilli() }.getOrDefault(0L)
    }
}

@Serializable
internal data class ArtifactRecord(
    val path: String,
    val sizeBytes: Long? = null,
    val updatedAt: String? = null,
    val seenAt: Long = 0L,
) {
    fun sortMillis(): Long = ArtifactHistoryLogic.sortMillis(updatedAt)

    fun toItem(): ArtifactItem = ArtifactItem(path, sizeBytes, updatedAt)
}

@Serializable
internal data class ArtifactPin(
    val path: String,
    val updatedAt: String? = null,
    val visible: Boolean = true,
)

@Serializable
internal data class AgentArtifacts(
    val items: List<ArtifactRecord> = emptyList(),
    val pin: ArtifactPin? = null,
)
