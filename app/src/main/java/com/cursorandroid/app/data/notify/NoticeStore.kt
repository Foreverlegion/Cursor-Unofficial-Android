package com.cursorandroid.app.data.notify

import android.content.Context
import androidx.core.content.edit
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.isLiveStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NoticeStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()

    fun visible(): List<Notice> {
        return synchronized(lock) {
            loadItems()
                .filter { !it.dismissed }
                .sortedByDescending { it.at }
                .take(MAX_VISIBLE)
        }
    }

    fun record(
        agentId: String,
        agentName: String?,
        runId: String,
        status: String?,
        result: String?,
    ): Boolean {
        val kind = kindOf(status)
        val title = agentName?.takeIf { it.isNotBlank() } ?: "Agent"
        val snippet = result?.trim()?.replace('\n', ' ')?.take(120)
        val body = buildString {
            append(labelOf(status))
            if (!snippet.isNullOrBlank() && kind != "working") {
                append(" — ")
                append(snippet)
            }
        }
        return upsert(
            Notice(
                id = runId,
                agentId = agentId,
                title = title,
                body = body,
                kind = kind,
                at = System.currentTimeMillis(),
            ),
        )
    }

    fun recordApproval(
        agentId: String,
        agentName: String?,
        callId: String,
        body: String,
    ): Boolean {
        return upsert(
            Notice(
                id = "approval-$callId",
                agentId = agentId,
                title = agentName?.takeIf { it.isNotBlank() } ?: "Cursor",
                body = body,
                kind = "approval",
                at = System.currentTimeMillis(),
            ),
        )
    }

    fun dismiss(id: String, cancelShade: Boolean = true) {
        synchronized(lock) {
            val dismissed = rememberDismissed(loadDismissed(), listOf(id))
            save(loadItems().map { if (it.id == id) it.copy(dismissed = true) else it }, dismissed)
        }
        if (cancelShade) cancelShade(listOf(id))
    }

    fun dismissAll() {
        val ids: List<String>
        synchronized(lock) {
            val items = loadItems()
            ids = items.map { it.id }
            val dismissed = rememberDismissed(loadDismissed(), ids)
            save(items.map { it.copy(dismissed = true) }, dismissed)
        }
        cancelShade(ids)
    }

    fun dismissAgent(agentId: String) {
        val ids: List<String>
        synchronized(lock) {
            val items = loadItems()
            ids = items.filter { it.agentId == agentId }.map { it.id }
            val dismissed = rememberDismissed(loadDismissed(), ids)
            save(
                items.map { if (it.agentId == agentId) it.copy(dismissed = true) else it },
                dismissed,
            )
        }
        cancelShade(ids)
    }

    fun relabel(agentId: String, title: String) {
        val next = title.trim()
        if (agentId.isBlank() || next.isBlank()) return
        synchronized(lock) {
            save(applyRelabel(loadItems(), agentId, next), loadDismissed())
        }
    }

    fun reconcile(
        agents: List<AgentSummary>,
        live: Map<String, String> = emptyMap(),
        titles: Map<String, String> = emptyMap(),
    ) {
        synchronized(lock) {
            save(reconcileNotices(loadItems(), agents, live, titles), loadDismissed())
        }
    }

    private fun upsert(notice: Notice): Boolean {
        val visible: Boolean
        synchronized(lock) {
            val (items, dismissed) = applyUpsert(loadItems(), notice, loadDismissed())
            save(items, dismissed)
            visible = !dismissed.contains(notice.id)
        }
        return visible
    }

    private fun loadItems(): List<Notice> {
        val raw = prefs.getString(ALL, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Notice>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadDismissed(): List<String> {
        val raw = prefs.getString(DISMISSED, null) ?: return loadItems().mapNotNull { n ->
            n.id.takeIf { n.dismissed }
        }
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    private fun save(items: List<Notice>, dismissed: List<String>) {
        prefs.edit {
            putString(ALL, json.encodeToString(items.take(MAX_STORED)))
            putString(DISMISSED, json.encodeToString(dismissed.takeLast(MAX_DISMISSED)))
        }
    }

    private fun cancelShade(ids: List<String>) {
        ids.forEach { id ->
            NotifyShade.cancel(app, shadeId(id))
        }
    }

    companion object {
        private const val PREFS = "notice_feed"
        private const val ALL = "items"
        private const val DISMISSED = "dismissed_ids"
        private const val MAX_STORED = 40
        private const val MAX_VISIBLE = 6
        private const val MAX_DISMISSED = 200

        fun kindOf(status: String?): String {
            val s = status?.uppercase()
            return when {
                s == "ERROR" || s == "EXPIRED" -> "error"
                s == "CANCELLED" -> "cancelled"
                s == "FINISHED" -> "finished"
                isLiveStatus(status) -> "working"
                else -> "finished"
            }
        }

        fun labelOf(status: String?): String {
            return when (status?.uppercase()) {
                "CREATING" -> "Starting"
                "RUNNING", "ACTIVE" -> "Working"
                "FINISHED" -> "Finished"
                "ERROR" -> "Failed"
                "CANCELLED" -> "Cancelled"
                "EXPIRED" -> "Expired"
                else -> status ?: "Updated"
            }
        }
    }
}

internal fun shadeId(noticeId: String): Int = noticeId.hashCode()

internal fun rememberDismissed(ids: List<String>, extra: Collection<String>, max: Int = 200): List<String> {
    val next = LinkedHashSet(ids)
    extra.forEach { id ->
        if (id.isBlank()) return@forEach
        next.remove(id)
        next.add(id)
    }
    return next.toList().takeLast(max)
}

internal fun applyUpsert(
    items: List<Notice>,
    incoming: Notice,
    dismissedIds: Collection<String>,
    maxStored: Int = 40,
): Pair<List<Notice>, List<String>> {
    val dismissed = rememberDismissed(dismissedIds.toList(), emptyList())
    val wasDismissed = incoming.id in dismissed || items.any { it.id == incoming.id && it.dismissed }
    val nextDismissed = if (wasDismissed) rememberDismissed(dismissed, listOf(incoming.id)) else dismissed
    val next = items.toMutableList()
    if (incoming.kind == "working") {
        for (i in next.indices) {
            val prev = next[i]
            if (prev.agentId == incoming.agentId && prev.kind == "working" && prev.id != incoming.id) {
                next[i] = prev.copy(dismissed = true)
            }
        }
    }
    val idx = next.indexOfFirst { it.id == incoming.id }
    if (idx >= 0) {
        val prev = next[idx]
        next[idx] = incoming.copy(
            dismissed = wasDismissed,
            at = if (wasDismissed) prev.at else incoming.at,
        )
    } else if (!wasDismissed) {
        next.add(0, incoming)
    }
    return next.take(maxStored) to nextDismissed
}

internal fun applyRelabel(items: List<Notice>, agentId: String, title: String): List<Notice> {
    val next = title.trim()
    if (agentId.isBlank() || next.isBlank()) return items
    return items.map { notice ->
        if (notice.agentId == agentId && notice.title != next) notice.copy(title = next) else notice
    }
}

internal fun reconcileNotices(
    notices: List<Notice>,
    agents: List<AgentSummary>,
    live: Map<String, String> = emptyMap(),
    titles: Map<String, String> = emptyMap(),
): List<Notice> {
    val byId = agents.associateBy { it.id }
    return notices.map { notice ->
        val local = titles[notice.agentId]?.trim()?.takeIf { it.isNotEmpty() }
        val named = if (local != null && notice.title != local) notice.copy(title = local) else notice
        if (named.dismissed || named.kind != "working") return@map named
        val agent = byId[named.agentId]
        val status = live[named.agentId] ?: agent?.status
        val creating = status.equals("CREATING", ignoreCase = true)
        val currentRun = agent?.latestRunId == null || agent.latestRunId == named.id
        if (agent != null && creating && currentRun) named
        else named.copy(dismissed = true)
    }
}

@Serializable
data class Notice(
    val id: String,
    val agentId: String,
    val title: String,
    val body: String,
    val kind: String,
    val at: Long,
    val dismissed: Boolean = false,
)
