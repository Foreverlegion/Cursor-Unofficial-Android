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
    fun sameUserTextFromDifferentRunsStaysTwice() {
        val first = line("user-r1", "user", "See attached.", "r1")
        val second = line("user-r2", "user", "See attached.", "r2")

        val ordered = coalesceTranscript(listOf(first, second))

        assertEquals(2, ordered.count { it.kind == "user" })
        assertEquals(listOf("r1", "r2"), ordered.filter { it.kind == "user" }.map { it.runId })
    }

    @Test
    fun duplicateUserLineSameRunCollapses() {
        val a = line("user-r1", "user", "ok", "r1")
        val b = TranscriptLine(id = "user-local-9", kind = "user", text = "ok", runId = "r1")

        val ordered = coalesceTranscript(listOf(a, b))

        assertEquals(1, ordered.count { it.kind == "user" })
        assertEquals("r1", ordered.single { it.kind == "user" }.runId)
    }

    @Test
    fun localFollowUpsWithSameCaptionStay() {
        val first = TranscriptLine(id = "user-local-1", kind = "user", text = "See attached.")
        val second = TranscriptLine(id = "user-local-2", kind = "user", text = "See attached.")

        val ordered = coalesceTranscript(listOf(first, second))

        assertEquals(listOf("user-local-1", "user-local-2"), ordered.map { it.id })
    }

    @Test
    fun mergeKeepsRepeatedUserCaptions() {
        val user1 = line("user-r1", "user", "ok", "r1")
        val assistant = line("assistant-r1", "assistant", "done", "r1")
        val user2 = line("user-r2", "user", "ok", "r2")

        val merged = mergeTranscript(
            memory = listOf(user1, assistant, user2),
            disk = listOf(user1, assistant),
        )

        assertEquals(2, merged.count { it.kind == "user" })
        assertEquals(listOf("r1", "r2"), merged.filter { it.kind == "user" }.map { it.runId })
    }

    @Test
    fun clipDropsToolsBeforeUserMessages() {
        val user = line("user-r1", "user", "keep me", "r1")
        val assistant = line("assistant-r1", "assistant", "reply", "r1")
        val tools = (1..8).map { i ->
            TranscriptLine(id = "tool-$i", kind = "tool", text = "call $i", runId = "r1")
        }

        val clipped = clipTranscript(listOf(user) + tools + assistant, maxLines = 4)

        assertEquals(listOf("user", "assistant"), clipped.filter { it.kind == "user" || it.kind == "assistant" }.map { it.kind })
        assertEquals("keep me", clipped.single { it.kind == "user" }.text)
        assertEquals(4, clipped.size)
    }

    @Test
    fun mergeRunTranscriptInsertsUserPrompts() {
        val assistant = line("assistant-r2", "assistant", "done", "r2")
        val runs = listOf(
            com.cursorandroid.app.data.api.Run(
                id = "r2",
                createdAt = "2026-09-13T10:02:00.000Z",
                result = "done",
                prompt = com.cursorandroid.app.data.api.Prompt("second"),
            ),
            com.cursorandroid.app.data.api.Run(
                id = "r1",
                createdAt = "2026-09-13T10:00:00.000Z",
                result = "first reply",
                prompt = com.cursorandroid.app.data.api.Prompt("[client=cursor-android]\n\nfirst"),
            ),
        )

        val merged = mergeRunTranscript(listOf(assistant), runs)

        assertEquals(listOf("user", "assistant", "user", "assistant"), merged.map { it.kind })
        assertEquals(listOf("first", "second"), merged.filter { it.kind == "user" }.map { it.text })
    }

    @Test
    fun conversationFillsMissingUserMessages() {
        val assistant = line("assistant-r1", "assistant", "I'll add the README.", "r1")
        val messages = listOf(
            com.cursorandroid.app.data.api.ConversationMessage(
                type = "user_message",
                text = "[client=cursor-android]\n\nAdd a README",
            ),
            com.cursorandroid.app.data.api.ConversationMessage(
                type = "assistant_message",
                text = "I'll add the README.",
            ),
            com.cursorandroid.app.data.api.ConversationMessage(
                type = "user_message",
                text = "Also add troubleshooting",
            ),
        )

        val merged = mergeConversationTranscript(listOf(assistant), messages)

        assertEquals(listOf("user", "assistant", "user"), merged.map { it.kind })
        assertEquals(listOf("Add a README", "Also add troubleshooting"), merged.filter { it.kind == "user" }.map { it.text })
        assertEquals("r1", merged.single { it.kind == "assistant" }.runId)
    }

    @Test
    fun conversationKeepsRepeatedUserTextAndLocalQueue() {
        val queued = TranscriptLine(id = "user-local-1", kind = "user", text = "next", queued = true)
        val messages = listOf(
            com.cursorandroid.app.data.api.ConversationMessage(type = "user_message", text = "ok"),
            com.cursorandroid.app.data.api.ConversationMessage(type = "assistant_message", text = "done"),
            com.cursorandroid.app.data.api.ConversationMessage(type = "user_message", text = "ok"),
        )

        val merged = mergeConversationTranscript(listOf(queued), messages)

        assertEquals(listOf("ok", "ok", "next"), merged.filter { it.kind == "user" }.map { it.text })
        assertEquals(true, merged.last { it.kind == "user" }.queued)
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
