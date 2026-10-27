package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** How long the archive call waits after the tap. Undo during this window never reaches the server. */
const val ARCHIVE_SEND_DELAY_MS = 7_000L

/** How long the Undo note stays on screen. Dismissing it does not send or cancel the archive. */
const val ARCHIVE_TOAST_MS = 5_000L

fun interface ArchiveTask {
    fun cancel()
}

/**
 * One archive the user already sees as done, whose Cursor call has not been sent yet.
 *
 * The record is written to the preferences file immediately. Leaving the app sends every pending
 * archive then, so a backgrounded process does not sit on them. If the process is killed before
 * that send finishes, the record is still on disk and the next launch sends it. Undo only removes
 * a record that has not started sending. It does not call unarchive.
 */
@Serializable
data class PendingArchive(
    @SerialName("agentId") val agentId: String,
    @SerialName("previousStatus") val previousStatus: String? = null,
    @SerialName("requestedAt") val requestedAt: Long = 0L,
    @SerialName("dueAt") val dueAt: Long = 0L,
    @SerialName("sending") val sending: Boolean = false,
)

interface PendingArchiveSink {
    fun load(): List<PendingArchive>
    fun save(items: List<PendingArchive>)
}

class PendingArchiveStore(private val ui: UiPrefsStore) : PendingArchiveSink {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun load(): List<PendingArchive> {
        val raw = ui[UiKeys.pendingArchives] ?: return emptyList()
        return runCatching { json.decodeFromString<List<PendingArchive>>(raw) }.getOrDefault(emptyList())
    }

    override fun save(items: List<PendingArchive>) {
        ui.put(UiKeys.pendingArchives, json.encodeToString(items))
        ui.flush()
    }
}

class DeferredArchiveQueue(
    private val clock: () -> Long,
    private val schedule: (delayMs: Long, block: () -> Unit) -> ArchiveTask,
    private val store: PendingArchiveSink,
    private val delayMs: Long = ARCHIVE_SEND_DELAY_MS,
    var onDue: (PendingArchive) -> Unit = {},
    var onRollback: (PendingArchive, String) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    private val pending = LinkedHashMap<String, PendingArchive>()
    private val timers = HashMap<String, ArchiveTask>()

    fun enqueue(agentId: String, previousStatus: String?): Boolean = synchronized(lock) {
        if (agentId.isBlank() || pending.containsKey(agentId)) return false
        val now = clock()
        val item = PendingArchive(
            agentId = agentId,
            previousStatus = previousStatus,
            requestedAt = now,
            dueAt = now + delayMs,
        )
        pending[agentId] = item
        store.save(pending.values.toList())
        timers[agentId] = schedule(delayMs) { deliver(agentId) }
        true
    }

    /** Drops a not-yet-sent archive. Returns null once the call has started or the id is unknown. */
    fun undo(agentId: String): PendingArchive? = synchronized(lock) {
        val current = pending[agentId] ?: return null
        if (current.sending) return null
        timers.remove(agentId)?.cancel()
        pending.remove(agentId)
        store.save(pending.values.toList())
        current
    }

    fun pendingIds(): Set<String> = synchronized(lock) { pending.keys.toSet() }

    fun pendingSnapshot(): List<PendingArchive> = synchronized(lock) { pending.values.toList() }

    /** Send every archive that is still waiting. Used when the app backgrounds, and on the next launch. */
    fun flush() {
        val ids = synchronized(lock) { pending.filterValues { !it.sending }.keys.toList() }
        ids.forEach { deliver(it) }
    }

    /** Load records left by a killed process. Sending flags from the dead process are not trusted. */
    fun load() {
        val stored = store.load()
        synchronized(lock) {
            pending.clear()
            timers.values.forEach { it.cancel() }
            timers.clear()
            stored.forEach { pending[it.agentId] = it.copy(sending = false) }
        }
    }

    fun complete(agentId: String, ok: Boolean, reason: String = "") {
        val item = synchronized(lock) {
            val current = pending.remove(agentId) ?: return
            timers.remove(agentId)?.cancel()
            store.save(pending.values.toList())
            current
        }
        if (!ok) onRollback(item, reason.ifBlank { "Request failed" })
    }

    private fun deliver(agentId: String) {
        val item = synchronized(lock) {
            val current = pending[agentId] ?: return
            if (current.sending) return
            timers.remove(agentId)?.cancel()
            val next = current.copy(sending = true)
            pending[agentId] = next
            store.save(pending.values.toList())
            next
        }
        onDue(item)
    }
}

fun List<AgentSummary>.withPendingArchives(pending: Set<String>): List<AgentSummary> {
    if (pending.isEmpty()) return this
    return map { agent ->
        if (agent.id in pending) agent.copy(archived = true, status = "ARCHIVED") else agent
    }
}
