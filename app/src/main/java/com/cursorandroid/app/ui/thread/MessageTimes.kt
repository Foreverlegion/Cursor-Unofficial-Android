package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.data.repo.runFinishMs
import com.cursorandroid.app.data.repo.runStartMs
import com.cursorandroid.app.data.repo.runsOldestFirst
import com.cursorandroid.app.data.repo.visibleUserText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class RunSpan(
    val startMs: Long?,
    val finishMs: Long?,
    val prompt: String? = null,
    val result: String? = null,
)

/** Oldest run first, so text matching walks the thread in order. */
internal fun spansOf(runs: List<Run>): Map<String, RunSpan> =
    runsOldestFirst(runs).associate { run ->
        run.id to RunSpan(
            startMs = runStartMs(run),
            finishMs = runFinishMs(run),
            prompt = run.prompt?.text?.let(::visibleUserText)?.takeIf { it.isNotEmpty() },
            result = run.result?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

/** A line's own time wins. Otherwise a prompt takes its run's start and a reply or note its run's finish. */
internal fun lineTimeMs(line: TranscriptLine, spans: Map<String, RunSpan>): Long? {
    line.atMs?.let { return it }
    val run = line.runId?.takeIf { it.isNotBlank() }
        ?: line.id.substringAfter('-', "").takeIf { line.id.startsWith("user-") || line.id.startsWith("assistant-") }
    val span = run?.let { spans[it] } ?: return null
    return if (line.kind == "user") span.startMs else span.finishMs ?: span.startMs
}

/**
 * A time for every user and agent message. Known times (the line's own, its run's, or a prompt or final reply matched to a
 * run by its text) anchor the thread. Messages between two anchors, such as the agent's intermediate replies, are spread
 * evenly between them, and none ever goes backwards. Before the first anchor and after the last, the nearest anchor is used.
 */
internal fun resolveLineTimes(lines: List<TranscriptLine>, spans: Map<String, RunSpan>): Map<String, Long> {
    val messages = lines.filter { it.kind == "user" || it.kind == "assistant" }
    if (messages.isEmpty()) return emptyMap()
    val times = arrayOfNulls<Long>(messages.size)
    val usedPrompts = HashSet<String>()
    val usedResults = HashSet<String>()
    messages.forEachIndexed { i, line ->
        times[i] = lineTimeMs(line, spans) ?: run {
            val text = if (line.kind == "user") visibleUserText(line.text) else line.text.trim()
            if (text.isEmpty()) return@run null
            spans.entries.firstOrNull { (id, span) ->
                if (line.kind == "user") span.prompt == text && id !in usedPrompts
                else span.result == text && id !in usedResults
            }?.let { (id, span) ->
                if (line.kind == "user") {
                    usedPrompts += id
                    span.startMs
                } else {
                    usedResults += id
                    span.finishMs ?: span.startMs
                }
            }
        }
    }
    val firstKnown = times.indexOfFirst { it != null }
    if (firstKnown < 0) return emptyMap()
    val known = times.copyOf()
    for (i in times.indices) {
        if (known[i] != null) continue
        var next = i + 1
        while (next < times.size && known[next] == null) next++
        val before = (i - 1 downTo 0).firstOrNull { known[it] != null }
        if (before == null) {
            times[i] = known[firstKnown]
        } else if (next >= times.size) {
            times[i] = known[before]
        } else {
            val start = known[before]!!
            val end = maxOf(start, known[next]!!)
            times[i] = start + (end - start) * (i - before) / (next - before)
        }
    }
    return messages.indices.associate { messages[it].id to times[it]!! }
}

internal fun formatClock(ms: Long, zone: ZoneId, use24h: Boolean, locale: Locale): String {
    val pattern = if (use24h) "HH:mm" else "h:mm a"
    return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(ms).atZone(zone))
}

internal fun formatFullTime(ms: Long, zone: ZoneId, use24h: Boolean, locale: Locale): String {
    val pattern = if (use24h) "EEE, MMM d, yyyy, HH:mm" else "EEE, MMM d, yyyy, h:mm a"
    return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(ms).atZone(zone))
}

internal fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale): String = when {
    day == today -> "Today"
    day == today.minusDays(1) -> "Yesterday"
    day.year == today.year -> DateTimeFormatter.ofPattern("MMM d", locale).format(day)
    else -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(day)
}

internal fun localDay(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

/** Puts a [ChatRow.Day] ahead of the first timed message of each local day. Rows without a time stay with the day before them. */
internal fun withDaySeparators(
    rows: List<ChatRow>,
    timeOf: (TranscriptLine) -> Long?,
    zone: ZoneId,
): List<ChatRow> {
    val out = ArrayList<ChatRow>(rows.size + 4)
    var current: Long? = null
    for (row in rows) {
        if (row is ChatRow.Message && row.line.kind != "notice") {
            val day = timeOf(row.line)?.let { localDay(it, zone).toEpochDay() }
            if (day != null && day != current) {
                out += ChatRow.Day(day)
                current = day
            }
        }
        out += row
    }
    return out
}
