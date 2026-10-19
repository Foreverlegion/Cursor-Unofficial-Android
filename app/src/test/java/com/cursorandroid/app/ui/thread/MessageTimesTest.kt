package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class MessageTimesTest {
    private val ny = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val us = Locale.US

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun msg(id: String, kind: String, at: Long? = null, runId: String? = null) =
        TranscriptLine(id, kind, "text $id", runId, atMs = at)

    @Test
    fun clockUsesTheZoneAndTheClockStyle() {
        val at = ms("2026-10-09T22:41:00Z")
        assertEquals("6:41 PM", formatClock(at, ny, use24h = false, locale = us))
        assertEquals("18:41", formatClock(at, ny, use24h = true, locale = us))
        assertEquals("7:41 AM", formatClock(at, tokyo, use24h = false, locale = us))
        assertEquals("07:41", formatClock(at, tokyo, use24h = true, locale = us))
    }

    @Test
    fun clockHandlesMidnightAndNoon() {
        assertEquals("12:05 AM", formatClock(ms("2026-10-10T04:05:00Z"), ny, false, us))
        assertEquals("12:00 PM", formatClock(ms("2026-10-10T16:00:00Z"), ny, false, us))
        assertEquals("00:05", formatClock(ms("2026-10-10T04:05:00Z"), ny, true, us))
    }

    @Test
    fun fullTimeCarriesDateAndYear() {
        val at = ms("2026-10-09T22:41:00Z")
        assertEquals("Fri, Oct 9, 2026, 6:41 PM", formatFullTime(at, ny, false, us))
        assertEquals("Fri, Oct 9, 2026, 18:41", formatFullTime(at, ny, true, us))
    }

    @Test
    fun dayLabelsAreRelativeToToday() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals("Today", dayLabel(today, today, us))
        assertEquals("Yesterday", dayLabel(LocalDate.of(2026, 10, 9), today, us))
        assertEquals("Oct 8", dayLabel(LocalDate.of(2026, 10, 8), today, us))
        assertEquals("Jan 2", dayLabel(LocalDate.of(2026, 1, 2), today, us))
        assertEquals("Dec 31, 2025", dayLabel(LocalDate.of(2025, 12, 31), today, us))
    }

    @Test
    fun yesterdayCrossesAMonthBoundary() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals("Yesterday", dayLabel(LocalDate.of(2026, 10, 31), today, us))
    }

    @Test
    fun separatorsGoBeforeTheFirstMessageOfEachLocalDay() {
        val rows = listOf(
            ChatRow.Message(msg("a", "user", ms("2026-10-08T15:00:00Z"))),
            ChatRow.Message(msg("b", "assistant", ms("2026-10-08T15:05:00Z"))),
            ChatRow.Message(msg("c", "user", ms("2026-10-09T15:00:00Z"))),
            ChatRow.Message(msg("d", "assistant", ms("2026-10-09T15:01:00Z"))),
        )

        val out = withDaySeparators(rows, { it.atMs }, ny)

        assertEquals(
            listOf("day", "m-user", "m-assistant", "day", "m-user", "m-assistant"),
            out.map(::chatRowType),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 10, 8).toEpochDay(), LocalDate.of(2026, 10, 9).toEpochDay()),
            out.filterIsInstance<ChatRow.Day>().map { it.epochDay },
        )
    }

    @Test
    fun theDayChangesAtLocalMidnightNotUtc() {
        val rows = listOf(
            ChatRow.Message(msg("a", "user", ms("2026-10-10T03:30:00Z"))),
            ChatRow.Message(msg("b", "assistant", ms("2026-10-10T04:30:00Z"))),
        )

        val inNewYork = withDaySeparators(rows, { it.atMs }, ny)
        val inUtc = withDaySeparators(rows, { it.atMs }, ZoneId.of("UTC"))

        assertEquals(listOf("day", "m-user", "day", "m-assistant"), inNewYork.map(::chatRowType))
        assertEquals(listOf("day", "m-user", "m-assistant"), inUtc.map(::chatRowType))
    }

    @Test
    fun noSeparatorWhenTheDateStaysTheSame() {
        val rows = listOf(
            ChatRow.Message(msg("a", "user", ms("2026-10-09T13:00:00Z"))),
            ChatRow.Message(msg("b", "assistant", ms("2026-10-09T23:00:00Z"))),
        )

        assertEquals(1, withDaySeparators(rows, { it.atMs }, ny).count { it is ChatRow.Day })
    }

    @Test
    fun untimedRowsStayWithTheDayBeforeThem() {
        val rows = listOf(
            ChatRow.Message(msg("a", "user", ms("2026-10-09T13:00:00Z"))),
            ChatRow.Tools("t1", listOf(msg("tool-1", "tool"))),
            ChatRow.Message(msg("b", "assistant", null)),
            ChatRow.Message(msg("c", "user", ms("2026-10-10T13:00:00Z"))),
        )

        val out = withDaySeparators(rows, { it.atMs }, ny)

        assertEquals(listOf("day", "m-user", "tools", "m-assistant", "day", "m-user"), out.map(::chatRowType))
    }

    @Test
    fun noticesNeverStartADay() {
        val rows = listOf(
            ChatRow.Message(TranscriptLine("n1", "notice", "hello", atMs = ms("2026-10-09T13:00:00Z"))),
            ChatRow.Message(msg("a", "user", ms("2026-10-10T13:00:00Z"))),
        )

        val out = withDaySeparators(rows, { it.atMs }, ny)

        assertEquals(listOf("m-notice", "day", "m-user"), out.map(::chatRowType))
    }

    @Test
    fun emptyThreadHasNoSeparators() {
        assertEquals(emptyList<ChatRow>(), withDaySeparators(emptyList(), { it.atMs }, ny))
    }

    @Test
    fun separatorKeysAreStableAndDistinct() {
        assertEquals("day-20000", chatRowKey(ChatRow.Day(20000)))
        assertEquals(chatRowKey(ChatRow.Day(5)), chatRowKey(ChatRow.Day(5)))
        assert(chatRowKey(ChatRow.Day(5)) != chatRowKey(ChatRow.Day(6)))
    }

    @Test
    fun ownTimeBeatsTheRunFallback() {
        val spans = mapOf("r1" to RunSpan(1_000, 9_000))

        assertEquals(42L, lineTimeMs(msg("user-r1", "user", 42, "r1"), spans))
    }

    @Test
    fun userFallsBackToRunStartAndReplyToRunFinish() {
        val spans = mapOf("r1" to RunSpan(1_000, 9_000))

        assertEquals(1_000L, lineTimeMs(msg("user-r1", "user", null, "r1"), spans))
        assertEquals(9_000L, lineTimeMs(msg("assistant-r1", "assistant", null, "r1"), spans))
    }

    @Test
    fun replyOfARunStillGoingUsesItsStart() {
        val spans = mapOf("r1" to RunSpan(1_000, null))

        assertEquals(1_000L, lineTimeMs(msg("assistant-r1", "assistant", null, "r1"), spans))
    }

    @Test
    fun fallbackReadsTheRunFromTheIdWhenRunIdIsMissing() {
        val spans = mapOf("r7" to RunSpan(5, 6))

        assertEquals(5L, lineTimeMs(msg("user-r7", "user"), spans))
        assertEquals(6L, lineTimeMs(msg("assistant-r7", "assistant"), spans))
    }

    @Test
    fun unknownRunHasNoTime() {
        assertNull(lineTimeMs(msg("user-local-1", "user"), mapOf("r1" to RunSpan(1, 2))))
        assertNull(lineTimeMs(msg("assistant-r9", "assistant", null, "r9"), emptyMap()))
    }

    @Test
    fun spansComeFromRunTimestamps() {
        val done = Run(
            id = "r1",
            status = "FINISHED",
            createdAt = "2026-10-09T22:00:00Z",
            updatedAt = "2026-10-09T22:03:00Z",
            prompt = Prompt("hi"),
        )
        val timed = done.copy(id = "r2", durationMs = 90_000, updatedAt = null)
        val running = done.copy(id = "r3", status = "RUNNING")

        val spans = spansOf(listOf(done, timed, running))

        assertEquals(ms("2026-10-09T22:00:00Z"), spans.getValue("r1").startMs)
        assertEquals(ms("2026-10-09T22:03:00Z"), spans.getValue("r1").finishMs)
        assertEquals(ms("2026-10-09T22:01:30Z"), spans.getValue("r2").finishMs)
        assertNull(spans.getValue("r3").finishMs)
    }

    private fun convo(id: String, kind: String, text: String, at: Long? = null) =
        TranscriptLine("$kind-$id", kind, text, atMs = at)

    private fun span(start: Long, finish: Long?, prompt: String? = null, result: String? = null) =
        RunSpan(start, finish, prompt, result)

    @Test
    fun everyMessageOfARunGetsATimeFromTheRunAndItsTextMatches() {
        val lines = listOf(
            convo("m1", "user", "fix the grid"),
            convo("m2", "assistant", "Now Grid.kt model functions."),
            convo("m3", "assistant", "Update these two to the new rule."),
            convo("m4", "assistant", "All tests pass."),
        )
        val spans = mapOf("r1" to span(1_000, 5_000, prompt = "fix the grid", result = "All tests pass."))

        val times = resolveLineTimes(lines, spans)

        assertEquals(1_000L, times["user-m1"])
        assertEquals(5_000L, times["assistant-m4"])
        assertEquals(4, times.size)
        assertEquals(2_333L, times["assistant-m2"])
        assertEquals(3_666L, times["assistant-m3"])
    }

    @Test
    fun interpolatedTimesNeverGoBackwards() {
        val lines = listOf(
            convo("a", "user", "one", 9_000),
            convo("b", "assistant", "two"),
            convo("c", "assistant", "three", 4_000),
        )

        val times = resolveLineTimes(lines, emptyMap())

        assertEquals(9_000L, times["assistant-b"])
        assertEquals(4_000L, times["assistant-c"])
    }

    @Test
    fun aMessageSeenWhileLiveKeepsItsOwnTimeBetweenAnchors() {
        val lines = listOf(
            convo("a", "user", "go", 1_000),
            convo("b", "assistant", "step", 2_500),
            convo("c", "assistant", "step 2"),
            convo("d", "assistant", "done", 9_000),
        )

        val times = resolveLineTimes(lines, emptyMap())

        assertEquals(2_500L, times["assistant-b"])
        assertEquals(5_750L, times["assistant-c"])
    }

    @Test
    fun messagesAfterTheLastKnownTimeTakeIt() {
        val lines = listOf(convo("a", "user", "go", 1_000), convo("b", "assistant", "working"))

        assertEquals(1_000L, resolveLineTimes(lines, emptyMap())["assistant-b"])
    }

    @Test
    fun messagesBeforeTheFirstKnownTimeTakeIt() {
        val lines = listOf(convo("a", "user", "old"), convo("b", "assistant", "older", 7_000))

        assertEquals(7_000L, resolveLineTimes(lines, emptyMap())["user-a"])
    }

    @Test
    fun noKnownTimeMeansNoTimes() {
        assertEquals(emptyMap<String, Long>(), resolveLineTimes(listOf(convo("a", "user", "x")), emptyMap()))
    }

    @Test
    fun twoRunsWithTheSamePromptMatchInOrder() {
        val lines = listOf(convo("m1", "user", "continue"), convo("m2", "user", "continue"))
        val spans = linkedMapOf(
            "r1" to span(1_000, 2_000, prompt = "continue"),
            "r2" to span(8_000, 9_000, prompt = "continue"),
        )

        val times = resolveLineTimes(lines, spans)

        assertEquals(1_000L, times["user-m1"])
        assertEquals(8_000L, times["user-m2"])
    }

    @Test
    fun anOverlappingFollowUpKeepsItsOwnStart() {
        val lines = listOf(
            convo("m1", "user", "first"),
            convo("m2", "assistant", "first done"),
            convo("m3", "user", "second"),
        )
        val spans = linkedMapOf(
            "r1" to span(1_000, 9_000, prompt = "first", result = "first done"),
            "r2" to span(5_000, null, prompt = "second"),
        )

        assertEquals(5_000L, resolveLineTimes(lines, spans)["user-m3"])
    }

    @Test
    fun spansCarryPromptAndResultInRunOrder() {
        val newer = Run("r2", status = "FINISHED", createdAt = "2026-10-09T23:00:00Z", result = " ok ", prompt = Prompt("later"))
        val older = Run("r1", status = "FINISHED", createdAt = "2026-10-09T22:00:00Z", prompt = Prompt("earlier"))

        val spans = spansOf(listOf(newer, older))

        assertEquals(listOf("r1", "r2"), spans.keys.toList())
        assertEquals("later", spans.getValue("r2").prompt)
        assertEquals("ok", spans.getValue("r2").result)
        assertNull(spans.getValue("r1").result)
    }
}
