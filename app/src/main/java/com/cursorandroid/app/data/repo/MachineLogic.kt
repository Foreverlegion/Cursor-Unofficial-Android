package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.isRemoteEnvType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

const val MACHINE_DAY_MS = 24L * 60 * 60 * 1000
private const val MAX_SEEN = 200
private const val MAX_MARKS = 200

/** What the phone knows about one machine or worker. [viaAgent] means the time comes from an agent that used it. */
@Serializable
data class MachineRecord(
    @SerialName("name") val name: String,
    @SerialName("worker_id") val workerId: String? = null,
    @SerialName("last_seen_ms") val lastSeenMs: Long = 0,
    @SerialName("first_seen_ms") val firstSeenMs: Long = 0,
    @SerialName("via_agent") val viaAgent: Boolean = false,
)

object MachineMarkState {
    const val HIDDEN = "hidden"
    const val FORGOTTEN = "forgotten"
    const val SHOWN = "shown"
}

@Serializable
data class MachineMark(
    @SerialName("state") val state: String,
    @SerialName("at_ms") val atMs: Long = 0,
    @SerialName("name") val name: String = "",
)

@Serializable
data class MachinePrefs(
    @SerialName("seen") val seen: Map<String, MachineRecord> = emptyMap(),
    @SerialName("marks") val marks: Map<String, MachineMark> = emptyMap(),
    @SerialName("auto_hide_days") val autoHideDays: Int = 0,
) {
    val isEmpty: Boolean get() = seen.isEmpty() && marks.isEmpty() && autoHideDays == 0
}

enum class MachineVisibility { Visible, Hidden, AutoHidden, Forgotten }

fun machineKey(workerId: String?, name: String): String {
    val id = workerId?.trim().orEmpty()
    return if (id.isNotEmpty()) "id:$id" else "name:${name.trim().lowercase()}"
}

fun Computer.machineKey(): String = machineKey(workerId, name)

fun machineVisibility(
    key: String,
    online: Boolean,
    lastSeenMs: Long?,
    prefs: MachinePrefs,
    nowMs: Long,
): MachineVisibility {
    when (prefs.marks[key]?.state) {
        MachineMarkState.FORGOTTEN -> return MachineVisibility.Forgotten
        MachineMarkState.HIDDEN -> return MachineVisibility.Hidden
        MachineMarkState.SHOWN -> return MachineVisibility.Visible
    }
    val days = prefs.autoHideDays
    if (days > 0 && !online && lastSeenMs != null && lastSeenMs > 0 && nowMs - lastSeenMs > days * MACHINE_DAY_MS) {
        return MachineVisibility.AutoHidden
    }
    return MachineVisibility.Visible
}

fun Computer.lastSeen(prefs: MachinePrefs, nowMs: Long): Long? {
    if (online) return nowMs
    return prefs.seen[machineKey()]?.lastSeenMs?.takeIf { it > 0 }
}

fun visibleMachines(computers: List<Computer>, prefs: MachinePrefs, nowMs: Long): List<Computer> {
    return computers.filter {
        machineVisibility(it.machineKey(), it.online, it.lastSeen(prefs, nowMs), prefs, nowMs) == MachineVisibility.Visible
    }
}

/**
 * Folds one machine listing into what the phone remembers. Online workers are stamped with [nowMs].
 * A name-only entry (an offline machine an agent used) takes the time of the newest agent that used it,
 * and is dropped once a worker with the same name is online. Nothing about marks or the auto-hide
 * setting changes here.
 */
fun observeMachines(
    prefs: MachinePrefs,
    computers: List<Computer>,
    agents: List<AgentSummary>,
    nowMs: Long,
): MachinePrefs {
    val seen = LinkedHashMap(prefs.seen)
    val onlineNames = computers.filter { it.online }.map { it.name.trim().lowercase() }.toSet()
    computers.filter { it.online }.forEach { c ->
        val key = c.machineKey()
        val before = seen[key]
        seen[key] = MachineRecord(
            name = c.name,
            workerId = c.workerId,
            lastSeenMs = nowMs,
            firstSeenMs = before?.firstSeenMs?.takeIf { it > 0 } ?: nowMs,
            viaAgent = false,
        )
    }
    onlineNames.forEach { seen.remove("name:$it") }
    computers.filter { !it.online && it.workerId.isNullOrBlank() }.forEach { c ->
        val key = c.machineKey()
        if (c.name.trim().lowercase() in onlineNames) return@forEach
        val before = seen[key]
        val used = lastAgentUse(c.name, agents)
        val stamp = maxOf(before?.lastSeenMs ?: 0, used ?: 0)
        seen[key] = MachineRecord(
            name = c.name,
            lastSeenMs = stamp,
            firstSeenMs = before?.firstSeenMs?.takeIf { it > 0 } ?: stamp,
            viaAgent = before?.viaAgent ?: (stamp > 0),
        )
    }
    val kept = if (seen.size > MAX_SEEN) {
        seen.entries.sortedByDescending { it.value.lastSeenMs }.take(MAX_SEEN).associate { it.key to it.value }
    } else {
        seen
    }
    return prefs.copy(seen = kept)
}

private fun lastAgentUse(name: String, agents: List<AgentSummary>): Long? {
    val wanted = name.trim().lowercase()
    return agents
        .filter { isRemoteEnvType(it.env?.type) && it.env?.name?.trim()?.lowercase() == wanted }
        .mapNotNull { parseInstantMs(it.updatedAt ?: it.createdAt) }
        .maxOrNull()
}

internal fun parseInstantMs(raw: String?): Long? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
}

fun MachinePrefs.withMark(key: String, name: String, state: String, nowMs: Long): MachinePrefs {
    val next = LinkedHashMap(marks)
    next[key] = MachineMark(state = state, atMs = nowMs, name = name)
    val capped = if (next.size > MAX_MARKS) {
        next.entries.sortedByDescending { it.value.atMs }.take(MAX_MARKS).associate { it.key to it.value }
    } else {
        next
    }
    return copy(marks = capped)
}

fun MachinePrefs.withAutoHideDays(days: Int): MachinePrefs = copy(autoHideDays = days.coerceIn(0, 3650))

/** One row on Settings > Connections > Machines. */
data class MachineRow(
    val key: String,
    val name: String,
    val workerId: String?,
    val online: Boolean,
    val inUse: Boolean,
    val detail: String?,
    val lastSeenMs: Long?,
    val viaAgent: Boolean,
    val visibility: MachineVisibility,
)

/** Every machine seen: the current listing plus remembered ones that are no longer listed. */
fun machineRows(current: List<Computer>, prefs: MachinePrefs, nowMs: Long): List<MachineRow> {
    val rows = LinkedHashMap<String, MachineRow>()
    current.forEach { c ->
        val key = c.machineKey()
        val record = prefs.seen[key]
        val seenAt = if (c.online) nowMs else record?.lastSeenMs?.takeIf { it > 0 }
        rows[key] = MachineRow(
            key = key,
            name = c.name,
            workerId = c.workerId,
            online = c.online,
            inUse = c.inUse,
            detail = c.detail,
            lastSeenMs = seenAt,
            viaAgent = !c.online && (record?.viaAgent == true),
            visibility = machineVisibility(key, c.online, seenAt, prefs, nowMs),
        )
    }
    prefs.seen.forEach { (key, record) ->
        if (key in rows) return@forEach
        val seenAt = record.lastSeenMs.takeIf { it > 0 }
        rows[key] = MachineRow(
            key = key,
            name = record.name,
            workerId = record.workerId,
            online = false,
            inUse = false,
            detail = null,
            lastSeenMs = seenAt,
            viaAgent = record.viaAgent,
            visibility = machineVisibility(key, false, seenAt, prefs, nowMs),
        )
    }
    prefs.marks.forEach { (key, mark) ->
        if (key in rows || mark.state == MachineMarkState.SHOWN) return@forEach
        rows[key] = MachineRow(
            key = key,
            name = mark.name.ifBlank { key.substringAfter(':') },
            workerId = key.removePrefix("id:").takeIf { key.startsWith("id:") },
            online = false,
            inUse = false,
            detail = null,
            lastSeenMs = null,
            viaAgent = false,
            visibility = machineVisibility(key, false, null, prefs, nowMs),
        )
    }
    return rows.values.sortedWith(
        compareByDescending<MachineRow> { it.online }
            .thenByDescending { it.lastSeenMs ?: 0L }
            .thenBy { it.name.lowercase() },
    )
}

val AUTO_HIDE_CHOICES: List<Pair<Int, String>> = listOf(
    0 to "Off",
    7 to "7 days",
    14 to "14 days",
    30 to "30 days",
    60 to "60 days",
    90 to "90 days",
)

fun autoHideLabel(days: Int): String =
    AUTO_HIDE_CHOICES.firstOrNull { it.first == days }?.second ?: "$days days"

const val MACHINE_DELETE_NOTE =
    "Cursor's API has no way to remove a worker, only to deregister a pool. Delete forgets the machine on this phone only. It stays forgotten until a machine comes back under a new id."

fun relativeSeen(ms: Long, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val age = (nowMs - ms).coerceAtLeast(0)
    val minute = 60_000L
    return when {
        age < minute -> "just now"
        age < 60 * minute -> "${age / minute} min ago"
        age < 24 * 60 * minute -> "${age / (60 * minute)} h ago"
        age < 30 * MACHINE_DAY_MS -> "${age / MACHINE_DAY_MS} d ago"
        else -> java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
    }
}

fun MachineRow.statusLine(nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    if (online) {
        return "Online · " + (if (inUse) "busy" else "idle") + (detail?.let { " · $it" } ?: "")
    }
    val seen = lastSeenMs
    return when {
        seen == null || seen <= 0 -> "Offline · last seen unknown"
        viaAgent -> "Offline · last used by an agent ${relativeSeen(seen, nowMs, zone)}"
        else -> "Offline · last seen ${relativeSeen(seen, nowMs, zone)}"
    }
}

/** An import adds what this phone lacks and never overrides a mark made here. */
fun mergeMachinePrefs(local: MachinePrefs, incoming: MachinePrefs): MachinePrefs {
    val seen = LinkedHashMap(local.seen)
    incoming.seen.forEach { (key, record) ->
        val have = seen[key]
        if (have == null || record.lastSeenMs > have.lastSeenMs) seen[key] = record
    }
    return MachinePrefs(
        seen = seen,
        marks = incoming.marks + local.marks,
        autoHideDays = if (local.autoHideDays > 0) local.autoHideDays else incoming.autoHideDays,
    )
}
