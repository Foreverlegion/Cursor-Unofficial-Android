package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ConversationMessage
import com.cursorandroid.app.data.api.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunOrderTranscriptTest {
    private val oldA = "The widget image and the launcher icon should both use the 2021 render. I'll ship a release APK."
    private val oldB = "The compositor returns the 2021 nose with a transparent background."
    private val glued = oldA + oldB
    private val resize = "I'll start by reading how the resize handle is wired."
    private val newest = "updated. I'll commit the fix and the version bump separately, push, then build the release APK."
    private val order = listOf("r1", "r2", "r3")

    private fun user(run: String, text: String) = TranscriptLine("user-$run", "user", text, run)
    private fun assistant(run: String, text: String) = TranscriptLine("assistant-$run", "assistant", text, run)
    private fun msg(id: String, type: String, text: String) = ConversationMessage(id, type, text)

    private fun window() = listOf(
        msg("m3", "user_message", "second prompt"),
        msg("m4", "assistant_message", resize),
        msg("m5", "user_message", "third prompt"),
        msg("m6", "assistant_message", newest),
    )

    private fun local() = listOf(
        user("r1", "first prompt"),
        assistant("r1", glued),
        user("r2", "second prompt"),
        assistant("r2", resize),
        user("r3", "third prompt"),
        assistant("r3", newest),
    )

    private fun texts(lines: List<TranscriptLine>) = lines.filter { it.kind == "assistant" }.map { it.text }

    @Test
    fun oldRunTextMissingFromTheServerWindowNeverBecomesTheTail() {
        val merged = mergeConversationTranscript(local(), window(), setOf("r1", "r2", "r3"), order, "r3")

        assertEquals(newest, merged.last().text)
        assertEquals(listOf(glued, resize, newest), texts(merged))
        assertEquals("r1", merged.first { it.text == glued }.runId)
        assertTrue(merged.indexOf(merged.first { it.text == glued }) < merged.indexOf(merged.first { it.text == resize }))
    }

    @Test
    fun oldRunWithoutRunInfoStillStaysOutOfTheTail() {
        val merged = mergeConversationTranscript(local(), window(), emptySet(), order, "r3")
        assertEquals(newest, merged.last().text)
        assertEquals(glued, texts(merged).first())
    }

    @Test
    fun oldRunStaysInItsOwnTurnWhenTheServerListsItsUserMessage() {
        val server = listOf(msg("m1", "user_message", "first prompt")) + window()
        val merged = mergeConversationTranscript(local(), server, setOf("r1", "r2", "r3"), order, "r3")

        val kinds = merged.map { it.kind to it.runId }
        assertEquals("user" to "r1", kinds[0])
        assertEquals(glued, merged[1].text)
        assertEquals(newest, merged.last().text)
    }

    @Test
    fun oldRunGoesBeforeTheFirstNewerTurnWhenOnlyNewerTurnsAreListed() {
        val server = window().drop(2)
        val merged = mergeConversationTranscript(local(), server, setOf("r1", "r2"), order, "r3")

        val ids = merged.map { it.id }
        assertTrue(ids.indexOf("assistant-r1") < ids.indexOf("user-r3"))
        assertTrue(ids.indexOf("assistant-r2") < ids.indexOf("user-r3"))
        assertEquals(newest, merged.last().text)
    }

    @Test
    fun liveTailOfTheLiveRunStaysLast() {
        val live = "Reading the gesture handlers now."
        val lines = local().dropLast(1) + assistant("r3", live)
        val merged = mergeConversationTranscript(lines, window().dropLast(1), emptySet(), order, "r3")
        assertEquals(live, merged.last().text)
    }

    @Test
    fun repeatedMergesGiveTheSameOrder() {
        val first = mergeConversationTranscript(local(), window(), setOf("r1", "r2", "r3"), order, "r3")
        val second = mergeConversationTranscript(first, window(), setOf("r1", "r2", "r3"), order, "r3")
        val third = mergeConversationTranscript(second, window(), setOf("r1", "r2", "r3"), order, "r3")
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(second.map { it.id }, third.map { it.id })
    }

    @Test
    fun staleOldUserLineIsNotPinnedToTheBottomEither() {
        val merged = mergeConversationTranscript(local(), window().drop(2), emptySet(), order, "r3")
        assertEquals("assistant", merged.last().kind)
        assertEquals("user-r1", merged.first().id)
    }

    @Test
    fun withoutRunInfoNothingIsDropped() {
        val merged = mergeConversationTranscript(local(), window(), emptySet())
        assertTrue(texts(merged).containsAll(listOf(glued, resize, newest)))
    }

    @Test
    fun runsSortOldestFirstByCreatedAtOrByListOrder() {
        val dated = listOf(
            Run(id = "b", createdAt = "2026-10-10T05:00:00Z"),
            Run(id = "a", createdAt = "2026-10-10T04:00:00Z"),
        )
        assertEquals(listOf("a", "b"), runsOldestFirst(dated).map { it.id })
        val undated = listOf(Run(id = "new"), Run(id = "old"))
        assertEquals(listOf("old", "new"), runsOldestFirst(undated).map { it.id })
    }
}
