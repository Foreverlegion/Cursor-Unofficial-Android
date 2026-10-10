package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ConversationMessage
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Run
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStateTest {
    private val notification = "<system_notification source=\"timer\" name=\"agentic-rth-guard\">POSITIONS are STALE</system_notification>"

    private fun serverMessages(count: Int): List<ConversationMessage> = (0 until count).flatMap { i ->
        listOf(
            ConversationMessage(id = "m$i", type = "user_message", text = "$notification #$i"),
            ConversationMessage(id = "a$i", type = "assistant_message", text = "ran $i"),
        )
    }

    private fun finishedAgentLines(count: Int, queuedByOldBuild: Boolean): List<TranscriptLine> {
        val serverShaped = serverMessages(count).mapIndexedNotNull { index, msg ->
            when (msg.type) {
                "user_message" -> TranscriptLine(
                    id = if (index % 4 == 0) "user-conv-$index" else "user-${msg.id}",
                    kind = "user",
                    text = msg.text.orEmpty(),
                    queued = queuedByOldBuild,
                )
                else -> TranscriptLine("assistant-${msg.id}", "assistant", msg.text.orEmpty())
            }
        }
        return serverShaped
    }

    @Test
    fun finishedAgentWithManyServerMessagesAndAStaleFlagShowsNothingQueued() {
        val lines = finishedAgentLines(40, queuedByOldBuild = true)
        assertEquals(40, lines.count { it.kind == "user" && it.queued })

        assertTrue("no server message may ever render as queued", lines.none(::lineShowsQueued))

        val healed = applyQueuedFlags(lines, emptySet())
        assertEquals(0, healed.count { it.queued })
        assertEquals(lines.size, healed.size)
        assertEquals(lines.map { it.text }, healed.map { it.text })
    }

    @Test
    fun staleFlagsAreNotTurnedIntoPromptsToResend() {
        val lines = finishedAgentLines(40, queuedByOldBuild = true)
        assertTrue(leftoverLocalLines(lines).isEmpty())
    }

    @Test
    fun conversationMergeOfAFinishedAgentWithAnEmptyLocalQueueHasNoQueuedLines() {
        val merged = mergeConversationTranscript(emptyList(), serverMessages(40))
        assertEquals(40, merged.count { it.kind == "user" })
        assertTrue(merged.none { it.queued })
        assertTrue(merged.none(::lineShowsQueued))
        assertTrue(leftoverLocalLines(merged).isEmpty())
    }

    @Test
    fun onlyTheAppsOwnUnsentMessageStaysQueuedAmongServerMessages() {
        val server = finishedAgentLines(10, queuedByOldBuild = true)
        val own = TranscriptLine("user-local-abc", "user", "please also run the tests", queued = true)
        val flagged = applyQueuedFlags(server + own, setOf("user-local-abc"))
        assertEquals(listOf("user-local-abc"), flagged.filter(::lineShowsQueued).map { it.id })
        assertEquals(1, flagged.count { it.queued })
    }

    @Test
    fun aMessageTheServerAlreadyOwnsIsNotQueuedEvenIfItKeepsALocalId() {
        val delivered = TranscriptLine("user-local-old", "user", "go", runId = "run-1", queued = true)
        assertFalse(lineShowsQueued(delivered))
    }

    @Test
    fun staleLegacyEntryIsDroppedWhenIdleAndTheServerHasTheText() {
        val entries = listOf(
            QueueProbe("user-local-1", listOf("$notification #3", ""), queuedAtMs = 0L),
            QueueProbe("user-local-2", listOf("brand new instruction"), queuedAtMs = 0L),
        )
        val texts = serverMessages(10).filter { it.type == "user_message" }.map { it.text.orEmpty() }
        assertEquals(setOf("user-local-1"), staleQueueIds(entries, emptyList(), texts, idle = true))
        assertTrue(staleQueueIds(entries, emptyList(), texts, idle = false).isEmpty())
    }

    @Test
    fun timestampedEntryIsStaleOnlyWhenALaterRunCarriesItsPrompt() {
        val queuedAt = java.time.Instant.parse("2026-10-10T10:00:00Z").toEpochMilli()
        val entry = listOf(QueueProbe("user-local-1", listOf("continue"), queuedAt))

        val delivered = Run(id = "r2", createdAt = "2026-10-10T10:00:05Z", prompt = Prompt("continue"))
        assertEquals(setOf("user-local-1"), staleQueueIds(entry, listOf(delivered), emptyList(), idle = false))

        val olderSameText = Run(id = "r1", createdAt = "2026-10-09T10:00:00Z", prompt = Prompt("continue"))
        assertTrue(staleQueueIds(entry, listOf(olderSameText), listOf("continue"), idle = true).isEmpty())

        val unrelated = Run(id = "r3", createdAt = "2026-10-10T10:00:05Z", prompt = Prompt("something else"))
        assertTrue(staleQueueIds(entry, listOf(unrelated), emptyList(), idle = true).isEmpty())
    }

    @Test
    fun clientOriginPrefixDoesNotHideADeliveredMessage() {
        val queuedAt = java.time.Instant.parse("2026-10-10T10:00:00Z").toEpochMilli()
        val entry = listOf(QueueProbe("user-local-1", listOf("ship it"), queuedAt))
        val run = Run(
            id = "r2",
            createdAt = "2026-10-10T10:00:30Z",
            prompt = Prompt("${ClientOrigin.PREFIX} ship it"),
        )
        assertEquals(setOf("user-local-1"), staleQueueIds(entry, listOf(run), emptyList(), idle = false))
    }

    @Test
    fun queueEntriesSavedByEarlierBuildsStillDecode() {
        val json = Json { ignoreUnknownKeys = true }
        val item = json.decodeFromString<QueuedItem>("""{"id":"user-local-9","text":"hello"}""")
        assertEquals(0L, item.at)
        assertEquals("hello", item.text)
    }
}
