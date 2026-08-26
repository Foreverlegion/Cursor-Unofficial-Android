package com.cursorandroid.app.data.repo

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.cursorandroid.app.data.api.isLiveStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

@Serializable
data class TranscriptLine(
    val id: String,
    val kind: String,
    val text: String,
    val runId: String? = null,
    val queued: Boolean = false,
    val thumbs: List<String> = emptyList(),
)

@Serializable
data class ConversationSnap(
    val lines: List<TranscriptLine> = emptyList(),
    val runId: String? = null,
    val runStatus: String? = null,
)

class ConversationStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, DIR).apply { mkdirs() }
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val livePrefs = app.getSharedPreferences(LIVE_PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val pending = HashMap<String, ConversationSnap>()
    private val live = HashMap<String, String>()
    private val diskLock = Any()
    private val flush = Runnable { flushPending() }

    init {
        live.putAll(loadLive())
        migratePrefs()
    }

    fun load(agentId: String): List<TranscriptLine> = loadSnap(agentId).lines

    fun loadSnap(agentId: String): ConversationSnap {
        val file = fileFor(agentId)
        if (file.isFile) {
            val snap = readFile(file)
            if (snap != null) return snap
        }
        val raw = prefs.getString(key(agentId), null) ?: return ConversationSnap()
        val lines = decodeLines(raw)
        val snap = ConversationSnap(lines = clip(lines))
        if (lines.isNotEmpty()) writeFile(agentId, snap)
        return snap
    }

    fun save(
        agentId: String,
        lines: List<TranscriptLine>,
        runId: String? = null,
        runStatus: String? = null,
        immediate: Boolean = false,
    ) {
        val snap = ConversationSnap(
            lines = clip(lines),
            runId = runId,
            runStatus = runStatus,
        )
        rememberLive(agentId, runStatus)
        synchronized(pending) { pending[agentId] = snap }
        handler.removeCallbacks(flush)
        if (immediate) {
            val toWrite = synchronized(pending) { pending.remove(agentId) } ?: snap
            writeFile(agentId, toWrite)
        } else {
            handler.postDelayed(flush, FLUSH_MS)
        }
    }

    fun flush(agentId: String? = null) {
        handler.removeCallbacks(flush)
        val batch = synchronized(pending) {
            if (agentId == null) {
                val copy = pending.toMap()
                pending.clear()
                copy
            } else {
                val snap = pending.remove(agentId) ?: return
                mapOf(agentId to snap)
            }
        }
        batch.forEach { (id, snap) -> writeFile(id, snap) }
    }

    fun liveStatuses(): Map<String, String> = synchronized(live) { live.toMap() }

    fun liveStatus(agentId: String): String? = synchronized(live) { live[agentId] }

    fun exportAll(): Map<String, List<TranscriptLine>> {
        val out = LinkedHashMap<String, List<TranscriptLine>>()
        dir.listFiles()?.forEach { file ->
            if (!file.name.endsWith(".json")) return@forEach
            val id = file.name.removeSuffix(".json")
            val lines = readFile(file)?.lines.orEmpty()
            if (lines.isNotEmpty()) out[id] = lines
        }
        prefs.all.forEach { (name, value) ->
            if (!name.startsWith(PREFIX) || value !is String) return@forEach
            val id = name.removePrefix(PREFIX)
            if (id in out) return@forEach
            val lines = decodeLines(value)
            if (lines.isNotEmpty()) out[id] = lines
        }
        return out
    }

    fun importAll(items: Map<String, List<TranscriptLine>>) {
        items.forEach { (id, lines) ->
            writeFile(id, ConversationSnap(lines = clip(lines)))
        }
    }

    fun remove(agentId: String) {
        synchronized(pending) { pending.remove(agentId) }
        synchronized(live) {
            live.remove(agentId)
            livePrefs.edit { putString(LIVE, json.encodeToString(live.toMap())) }
        }
        fileFor(agentId).delete()
        prefs.edit { remove(key(agentId)) }
    }

    private fun flushPending() {
        val batch = synchronized(pending) {
            val copy = pending.toMap()
            pending.clear()
            copy
        }
        if (batch.isEmpty()) return
        io.execute {
            batch.forEach { (id, snap) -> writeFile(id, snap) }
        }
    }

    private fun writeFile(agentId: String, snap: ConversationSnap) {
        val file = fileFor(agentId)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        synchronized(diskLock) {
            runCatching {
                tmp.writeText(json.encodeToString(snap))
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
                prefs.edit { remove(key(agentId)) }
            }
        }
    }

    private fun readFile(file: File): ConversationSnap? {
        val raw = synchronized(diskLock) {
            runCatching { file.readText() }.getOrNull()
        } ?: return null
        decodeSnap(raw)?.let { return it }
        val lines = decodeLines(raw)
        return if (lines.isEmpty()) null else ConversationSnap(lines = lines)
    }

    private fun decodeSnap(raw: String): ConversationSnap? {
        return runCatching { json.decodeFromString<ConversationSnap>(raw) }.getOrNull()
            ?.takeIf { it.lines.isNotEmpty() || !it.runId.isNullOrBlank() }
    }

    private fun decodeLines(raw: String): List<TranscriptLine> {
        return runCatching { json.decodeFromString<List<StoredLine>>(raw) }
            .getOrDefault(emptyList())
            .map { it.toLine() }
    }

    private fun migratePrefs() {
        prefs.all.forEach { (name, value) ->
            if (!name.startsWith(PREFIX) || value !is String) return@forEach
            val id = name.removePrefix(PREFIX)
            if (fileFor(id).isFile) return@forEach
            val lines = decodeLines(value)
            if (lines.isNotEmpty()) writeFile(id, ConversationSnap(lines = clip(lines)))
        }
    }

    private fun rememberLive(agentId: String, runStatus: String?) {
        synchronized(live) {
            if (isLiveStatus(runStatus)) {
                live[agentId] = runStatus!!
            } else {
                live.remove(agentId)
            }
            livePrefs.edit { putString(LIVE, json.encodeToString(live.toMap())) }
        }
    }

    private fun loadLive(): Map<String, String> {
        val raw = livePrefs.getString(LIVE, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    private fun fileFor(agentId: String): File {
        val safe = agentId.replace(UNSAFE, "_")
        return File(dir, "$safe.json")
    }

    private fun key(agentId: String) = PREFIX + agentId

    companion object {
        private const val DIR = "conversations"
        private const val PREFS = "conversation_store"
        private const val LIVE_PREFS = "conversation_live"
        private const val LIVE = "status"
        private const val PREFIX = "t_"
        private const val MAX_LINES = 400
        private const val MAX_TEXT = 32_000
        private const val FLUSH_MS = 350L
        private val UNSAFE = Regex("[^A-Za-z0-9._-]")
        private val DURABLE = setOf("user", "assistant", "thinking", "tool", "notice")
    }

    private fun clip(lines: List<TranscriptLine>): List<TranscriptLine> {
        val durable = coalesceTranscript(lines).filter { it.kind in DURABLE }.map { line ->
            if (line.text.length <= MAX_TEXT) line else line.copy(text = line.text.take(MAX_TEXT))
        }
        return if (durable.size <= MAX_LINES) durable else durable.takeLast(MAX_LINES)
    }
}

internal fun lineKey(line: TranscriptLine): String {
    val runId = line.runId?.takeIf { it.isNotBlank() } ?: runIdFromId(line)
    if (!runId.isNullOrBlank() && line.kind in setOf("user", "assistant", "thinking")) {
        return "${line.kind}:$runId"
    }
    return "${line.kind}:${line.id}"
}

private fun runIdFromId(line: TranscriptLine): String? {
    val id = line.id
    val prefix = when (line.kind) {
        "user" -> "user-"
        "assistant" -> "assistant-"
        "thinking" -> "think-"
        else -> return null
    }
    if (!id.startsWith(prefix) || id.startsWith("user-local-")) return null
    return id.removePrefix(prefix).takeIf { it.isNotBlank() }
}

internal fun pickLine(left: TranscriptLine, right: TranscriptLine): TranscriptLine {
    val longer = if (right.text.length > left.text.length) right else left
    val other = if (longer === right) left else right
    val stableId = when {
        longer.id.startsWith("user-local-") && !other.id.startsWith("user-local-") -> other.id
        else -> longer.id
    }
    return longer.copy(
        id = stableId,
        runId = longer.runId ?: other.runId,
        queued = left.queued && right.queued,
        thumbs = longer.thumbs.ifEmpty { other.thumbs },
    )
}

internal fun coalesceTranscript(lines: List<TranscriptLine>): List<TranscriptLine> {
    val out = ArrayList<TranscriptLine>(lines.size)
    val keys = HashMap<String, Int>()
    val assistantText = HashSet<String>()
    val userText = HashMap<String, Int>()
    for (line in lines) {
        val key = lineKey(line)
        val existing = keys[key]
        if (existing != null) {
            out[existing] = pickLine(out[existing], line)
            continue
        }
        if (line.kind == "assistant") {
            val text = line.text.trim()
            if (text.isNotEmpty() && !assistantText.add(text)) continue
        }
        if (line.kind == "user") {
            val text = line.text.trim()
            val prev = userText[text]
            if (prev != null) {
                out[prev] = pickLine(out[prev], line)
                keys[key] = prev
                continue
            }
            if (text.isNotEmpty()) userText[text] = out.size
        }
        keys[key] = out.size
        out += line
    }
    return orderThinkingAfterAssistant(out)
}

internal fun orderThinkingAfterAssistant(lines: List<TranscriptLine>): List<TranscriptLine> {
    val pending = LinkedHashMap<String, TranscriptLine>()
    val out = ArrayList<TranscriptLine>(lines.size)

    fun runOf(line: TranscriptLine): String? {
        line.runId?.takeIf { it.isNotBlank() }?.let { return it }
        return when (line.kind) {
            "thinking" -> line.id.removePrefix("think-").takeIf { line.id.startsWith("think-") && it.isNotBlank() }
            "assistant" -> line.id.removePrefix("assistant-").takeIf {
                line.id.startsWith("assistant-") && it.isNotBlank()
            }
            else -> null
        }
    }

    fun hasAssistant(run: String): Boolean {
        return out.any { line ->
            line.kind == "assistant" && (line.runId == run || line.id == "assistant-$run")
        }
    }

    fun flushPending() {
        if (pending.isEmpty()) return
        out.addAll(pending.values)
        pending.clear()
    }

    for (line in lines) {
        when (line.kind) {
            "thinking" -> {
                val run = runOf(line) ?: line.id
                if (hasAssistant(run)) out += line else pending[run] = line
            }
            "assistant" -> {
                out += line
                runOf(line)?.let { pending.remove(it) }?.let { out += it }
            }
            "user", "notice" -> {
                flushPending()
                out += line
            }
            else -> out += line
        }
    }
    flushPending()
    return out
}

internal fun mergeTranscript(
    memory: List<TranscriptLine>,
    disk: List<TranscriptLine>,
): List<TranscriptLine> {
    if (memory.isEmpty()) return coalesceTranscript(disk)
    if (disk.isEmpty()) return coalesceTranscript(memory)
    val chosen = LinkedHashMap<String, TranscriptLine>()
    fun consider(line: TranscriptLine) {
        val key = lineKey(line)
        val prev = chosen[key]
        chosen[key] = if (prev == null) line else pickLine(prev, line)
    }
    memory.forEach(::consider)
    disk.forEach(::consider)
    val order = ArrayList<String>(chosen.size)
    val seen = HashSet<String>()
    for (line in memory) {
        val key = lineKey(line)
        if (seen.add(key)) order.add(key)
    }
    var last = -1
    for (line in disk) {
        val key = lineKey(line)
        val at = order.indexOf(key)
        if (at >= 0) {
            last = at
            continue
        }
        if (!seen.add(key)) continue
        val insertAt = if (last < 0 && line.kind == "thinking") {
            order.size
        } else {
            (last + 1).coerceIn(0, order.size)
        }
        order.add(insertAt, key)
        if (last >= 0 || line.kind != "thinking") {
            last = insertAt
        }
    }
    return coalesceTranscript(order.mapNotNull { chosen[it] })
}

@Serializable
private data class StoredLine(
    val id: String,
    val kind: String,
    val text: String,
    val runId: String? = null,
    val queued: Boolean = false,
    val thumbs: List<String> = emptyList(),
) {
    fun toLine() = TranscriptLine(id, kind, text, runId, queued, thumbs)
}
