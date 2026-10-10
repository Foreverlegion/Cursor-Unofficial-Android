package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import com.cursorandroid.app.data.api.ArtifactItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class ArtifactHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun ingest(agentId: String, incoming: List<ArtifactItem>, producedAt: String? = null): ArtifactItem? {
        val now = System.currentTimeMillis()
        val current = load(agentId)
        val merged = ArtifactHistoryLogic.merge(current.items, incoming, producedAt, now)
        var pin = current.pin
        if (merged.resolved.isNotEmpty()) {
            val latest = ArtifactHistoryLogic.latest(merged.resolved)
            val key = ArtifactHistoryLogic.key(latest)
            if (pin == null || ArtifactHistoryLogic.key(pin) != key) {
                pin = ArtifactPin(latest.path, latest.updatedAt, visible = true)
            }
        }
        if (pin != null && merged.kept.none { it.path == pin.path && it.updatedAt == pin.updatedAt }) {
            pin = pin.copy(visible = false)
        }
        save(agentId, AgentArtifacts(merged.kept, pin))
        return visibleOf(merged.kept, pin)
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

    fun agentIds(): Set<String> =
        prefs.all.keys.filter { it.startsWith(PREFIX) }.mapTo(HashSet()) { it.removePrefix(PREFIX) }

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
        return items.firstOrNull { it.path == pin.path && it.updatedAt == pin.updatedAt }?.toItem()
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

internal object ArtifactTime {
    fun format(
        iso: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        if (iso.isNullOrBlank()) return ""
        val trimmed = iso.trim()
        val instant = runCatching { Instant.parse(trimmed) }.getOrNull() ?: return trimmed
        val zoned = instant.atZone(zone)
        val date = DateTimeFormatter.ofPattern("MMM d", locale).format(zoned)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(zoned)
        return "$date, $time".replace('\u202f', ' ').replace('\u00a0', ' ')
    }
}

internal data class MergedArtifacts(
    val kept: List<ArtifactRecord>,
    val resolved: List<ArtifactItem>,
)

internal object ArtifactHistoryLogic {
    const val MAX_PER_AGENT = 8
    const val TTL_MS = 3L * 24 * 60 * 60 * 1000

    fun prune(items: List<ArtifactRecord>, now: Long): List<ArtifactRecord> {
        val cutoff = now - TTL_MS
        return keepLatest(items.filter { it.seenAt >= cutoff })
    }

    fun merge(
        existing: List<ArtifactRecord>,
        incoming: List<ArtifactItem>,
        producedAt: String?,
        now: Long,
    ): MergedArtifacts {
        val merged = LinkedHashMap<String, ArtifactRecord>()
        for (item in prune(existing, now)) {
            merged[recordKey(item)] = item
        }
        val resolved = ArrayList<ArtifactItem>(incoming.size)
        for (raw in incoming) {
            val stamped = stamp(raw, producedAt, merged.values)
            resolved += stamped
            if (!stamped.updatedAt.isNullOrBlank()) {
                merged.remove("${stamped.path}|")
            }
            val key = key(stamped)
            val prior = merged[key]
            merged[key] = ArtifactRecord(
                path = stamped.path,
                sizeBytes = stamped.sizeBytes ?: prior?.sizeBytes,
                updatedAt = stamped.updatedAt,
                seenAt = now,
            )
        }
        return MergedArtifacts(keepLatest(merged.values.toList()), resolved)
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
            compareBy<ArtifactItem> { sortMillis(it.whenIso()) }.thenBy { it.path },
        )
    }

    fun key(item: ArtifactItem): String = "${item.path}|${item.whenIso().orEmpty()}"

    fun key(pin: ArtifactPin): String = "${pin.path}|${pin.updatedAt.orEmpty()}"

    fun recordKey(record: ArtifactRecord): String = "${record.path}|${record.updatedAt.orEmpty()}"

    fun sortMillis(updatedAt: String?): Long {
        if (updatedAt.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(updatedAt).toEpochMilli() }.getOrDefault(0L)
    }

    // List Artifacts documents updatedAt. createdAt is kept when a payload includes
    // it and updatedAt is absent. A missing timestamp uses the producing run's time.
    fun stamp(
        item: ArtifactItem,
        producedAt: String?,
        existing: Collection<ArtifactRecord>,
    ): ArtifactItem {
        val api = item.whenIso()
        if (api != null) return item.copy(updatedAt = api)
        val samePath = existing.filter { it.path == item.path && !it.updatedAt.isNullOrBlank() }
        val candidates = if (item.sizeBytes != null) {
            samePath.filter { it.sizeBytes == item.sizeBytes }
        } else {
            samePath
        }
        val reuse = candidates.maxByOrNull { it.sortMillis() }
        if (reuse != null) return item.copy(updatedAt = reuse.updatedAt)
        return item.copy(updatedAt = producedAt?.takeIf { it.isNotBlank() })
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

    fun toItem(): ArtifactItem = ArtifactItem(path = path, sizeBytes = sizeBytes, updatedAt = updatedAt)
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
