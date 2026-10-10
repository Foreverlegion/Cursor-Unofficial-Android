package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.Run
import java.time.Instant

/**
 * Only a message this app wrote itself and has not delivered yet is "queued". Those lines have a
 * local id until the send succeeds. Anything that came from the server (conversation or run list)
 * is delivered by definition, whatever its stored flag says.
 */
const val LOCAL_USER_PREFIX = "user-local-"

fun isLocalQueueId(id: String): Boolean = id.startsWith(LOCAL_USER_PREFIX)

fun lineShowsQueued(line: TranscriptLine): Boolean =
    line.kind == "user" && line.queued && line.runId == null && isLocalQueueId(line.id)

/**
 * Sets `queued` for exactly the lines backed by an outbound entry and clears it everywhere else.
 * This also heals a flag that an older build stored on server messages.
 */
fun applyQueuedFlags(lines: List<TranscriptLine>, outboundIds: Set<String>): List<TranscriptLine> {
    var changed = false
    val next = lines.map { line ->
        if (line.kind != "user") return@map line
        val want = line.id in outboundIds
        if (line.queued == want) line else line.copy(queued = want).also { changed = true }
    }
    return if (changed) next else lines
}

/** Local unsent lines to rebuild a lost queue from. Server lines never qualify. */
fun leftoverLocalLines(lines: List<TranscriptLine>): List<TranscriptLine> =
    lines.filter { it.kind == "user" && it.queued && it.runId == null && isLocalQueueId(it.id) }

class QueueProbe(val id: String, val texts: List<String>, val queuedAtMs: Long)

private const val CLOCK_SKEW_MS = 120_000L

/**
 * Queue entries the server already has. An entry with a timestamp is delivered when a run created
 * after it carries the same prompt. An entry from before timestamps existed is judged only when the
 * agent is idle, and is dropped if the server already has a user message with that text: sending a
 * prompt twice to an agent is worse than dropping a stale one.
 */
fun staleQueueIds(
    entries: List<QueueProbe>,
    runs: List<Run>,
    serverUserTexts: List<String>,
    idle: Boolean,
): Set<String> {
    val promptRuns = runs.mapNotNull { run ->
        val text = visibleUserText(run.prompt?.text.orEmpty())
        if (text.isEmpty()) return@mapNotNull null
        text to runCatching { Instant.parse(run.createdAt ?: "").toEpochMilli() }.getOrNull()
    }
    val serverTexts = (serverUserTexts.map(::visibleUserText) + promptRuns.map { it.first }).toHashSet()
    val stale = HashSet<String>()
    for (entry in entries) {
        val wanted = entry.texts.map(::visibleUserText).filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty()) continue
        if (entry.queuedAtMs > 0L) {
            val delivered = promptRuns.any { (text, createdMs) ->
                text in wanted && createdMs != null && createdMs >= entry.queuedAtMs - CLOCK_SKEW_MS
            }
            if (delivered) stale += entry.id
        } else if (idle && wanted.any { it in serverTexts }) {
            stale += entry.id
        }
    }
    return stale
}
