package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationStoreTest {

    @Test
    fun coalesceMovesThinkingBelowAssistant() {
        val user = line("user-r1", "user", "rename the repo", "r1")
        val think = line("think-r1", "thinking", "plan for the rename", "r1")
        val assistant = line("assistant-r1", "assistant", "I'll point the project at the new URL.", "r1")

        val ordered = coalesceTranscript(listOf(user, think, assistant))

        assertEquals(listOf("user", "assistant", "thinking"), ordered.map { it.kind })
        assertEquals(assistant.text, ordered[1].text)
        assertEquals(think.text, ordered[2].text)
    }

    @Test
    fun thinkingStaysAfterUserUntilReplyArrives() {
        val user = line("user-r1", "user", "hello", "r1")
        val think = line("think-r1", "thinking", "working", "r1")

        val ordered = coalesceTranscript(listOf(user, think))

        assertEquals(listOf("user", "thinking"), ordered.map { it.kind })
    }

    @Test
    fun laterUserDoesNotStealEarlierThinking() {
        val user1 = line("user-r1", "user", "first", "r1")
        val think1 = line("think-r1", "thinking", "plan 1", "r1")
        val user2 = line("user-r2", "user", "second", "r2")

        val ordered = coalesceTranscript(listOf(user1, think1, user2))

        assertEquals(listOf("user", "thinking", "user"), ordered.map { it.kind })
        assertEquals("r1", ordered[1].runId)
    }

    @Test
    fun mergeDoesNotParkThinkingAboveTheReply() {
        val user = line("user-r1", "user", "rename", "r1")
        val think = line("think-r1", "thinking", "long plan", "r1")
        val assistant = line("assistant-r1", "assistant", "I'll update the URL.", "r1")

        val merged = mergeTranscript(
            memory = listOf(user, assistant),
            disk = listOf(user, think, assistant),
        )

        assertEquals(listOf("user", "assistant", "thinking"), merged.map { it.kind })
    }

    @Test
    fun mergeDoesNotLiftOrphanThinkingToTheTop() {
        val user = line("user-r2", "user", "next", "r2")
        val assistant = line("assistant-r2", "assistant", "done", "r2")
        val leftover = line("think-r1", "thinking", "old plan", "r1")

        val merged = mergeTranscript(
            memory = listOf(user, assistant),
            disk = listOf(leftover, user, assistant),
        )

        assertEquals("user", merged.first().kind)
        assertEquals("thinking", merged.last().kind)
    }

    private fun line(id: String, kind: String, text: String, runId: String) =
        TranscriptLine(id = id, kind = kind, text = text, runId = runId)
}
