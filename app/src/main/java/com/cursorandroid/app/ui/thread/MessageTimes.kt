package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.data.repo.runFinishMs
import com.cursorandroid.app.data.repo.runStartMs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class RunSpan(val startMs: Long?, val finishMs: Long?)

internal fun spansOf(runs: List<Run>): Map<String, RunSpan> =
    runs.associate { it.id to RunSpan(runStartMs(it), runFinishMs(it)) }

/** A line's own time wins. Otherwise a prompt takes its run's start and a reply or note its run's finish. */
internal fun lineTimeMs(line: TranscriptLine, spans: Map<String, RunSpan>): Long? {
    line.atMs?.let { return it }
    val run = line.runId?.takeIf { it.isNotBlank() }
        ?: line.id.substringAfter('-', "").takeIf { line.id.startsWith("user-") || line.id.startsWith("assistant-") }
    val span = run?.let { spans[it] } ?: return null
    return if (line.kind == "user") span.startMs else span.finishMs ?: span.startMs
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
