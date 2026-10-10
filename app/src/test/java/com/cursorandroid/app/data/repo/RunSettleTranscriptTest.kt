package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ConversationMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSettleTranscriptTest {
    private val seg1 = "The widget image and the launcher icon should both use the 2021 render. I'll ship a release APK."
    private val seg2 = "The compositor returns the 2021 nose with a transparent background."
    private val report = "docs/review-opus.md has a new entry for this bug."

    private fun msgs() = listOf(
        ConversationMessage(id = "m1", type = "user_message", text = "fix the widget"),
        ConversationMessage(id = "m2", type = "assistant_message", text = seg1),
        ConversationMessage(id = "m3", type = "assistant_message", text = seg2),
        ConversationMessage(id = "m4", type = "assistant_message", text = report),
    )

    private fun local(glued: String) = listOf(
        TranscriptLine("user-r1", "user", "fix the widget", "r1"),
        TranscriptLine("assistant-r1", "assistant", glued, "r1"),
    )

    @Test
    fun finishedRunDropsTheGluedStreamBubble() {
        val merged = mergeConversationTranscript(local(seg1 + seg2), msgs(), setOf("r1"))

        assertEquals(listOf("user", "assistant", "assistant", "assistant"), merged.map { it.kind })
        assertEquals(report, merged.last().text)
        assertTrue(merged.none { it.text == seg1 + seg2 })
    }

    @Test
    fun finishedRunWithResultLineStaysInServerOrder() {
        val lines = local(report)
        val merged = mergeConversationTranscript(lines, msgs(), setOf("r1"))

        assertEquals(listOf(seg1, seg2, report), merged.filter { it.kind == "assistant" }.map { it.text })
        assertEquals("r1", merged.last().runId)
    }

    @Test
    fun runningRunKeepsItsLiveTailAtTheEnd() {
        val live = "Still working on the compositor step."
        val merged = mergeConversationTranscript(local(live), msgs())

        assertEquals(live, merged.last().text)
        assertEquals(5, merged.size)
    }

    @Test
    fun finishedRunKeepsTextTheServerDoesNotHaveYet() {
        val glued = seg1 + seg2 + " One more thing the server has not listed."
        val merged = mergeConversationTranscript(local(glued), msgs(), setOf("r1"))

        assertEquals(glued, merged.last().text)
    }

    @Test
    fun streamTextInsideALongerServerMessageIsDropped() {
        val long = listOf(
            ConversationMessage(id = "m1", type = "user_message", text = "fix the widget"),
            ConversationMessage(id = "m2", type = "assistant_message", text = "$seg1 $seg2 Done."),
        )
        val merged = mergeConversationTranscript(local(seg1), long, setOf("r1"))

        assertEquals(1, merged.count { it.kind == "assistant" })
    }

    @Test
    fun otherRunsAreNotTouched() {
        val older = TranscriptLine("assistant-r0", "assistant", "partial", "r0")
        val merged = mergeConversationTranscript(local(seg1 + seg2) + older, msgs(), setOf("r1"))

        assertTrue(merged.any { it.id == "assistant-r0" })
    }
}
