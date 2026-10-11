package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.ConversationMessage
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MessageTimestampTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun run(id: String, created: String, updated: String? = null, status: String = "FINISHED", result: String? = "done") =
        Run(
            id = id,
            status = status,
            createdAt = created,
            updatedAt = updated,
            result = result,
            prompt = Prompt("question $id"),
        )

    @Test
    fun runLinesCarryStartForPromptAndFinishForReply() {
        val merged = mergeRunTranscript(
            emptyList(),
            listOf(run("r1", "2026-10-09T22:00:00Z", "2026-10-09T22:04:00Z")),
        )

        assertEquals(ms("2026-10-09T22:00:00Z"), merged.first { it.kind == "user" }.atMs)
        assertEquals(ms("2026-10-09T22:04:00Z"), merged.first { it.kind == "assistant" }.atMs)
    }

    @Test
    fun runWithNoUpdatedTimeFallsBackToItsStart() {
        val merged = mergeRunTranscript(emptyList(), listOf(run("r1", "2026-10-09T22:00:00Z")))

        assertEquals(ms("2026-10-09T22:00:00Z"), merged.first { it.kind == "assistant" }.atMs)
    }

    @Test
    fun aRunStillGoingGivesItsReplyTheStartTime() {
        val merged = mergeRunTranscript(
            emptyList(),
            listOf(run("r1", "2026-10-09T22:00:00Z", "2026-10-09T22:01:00Z", status = "RUNNING", result = "partial")),
        )

        assertEquals(ms("2026-10-09T22:00:00Z"), merged.first { it.kind == "assistant" }.atMs)
    }

    @Test
    fun localSendTimeSurvivesTheServerRunAndTheEarlierOneWins() {
        val local = TranscriptLine("user-r1", "user", "question r1", "r1", atMs = ms("2026-10-09T21:59:58Z"))

        val merged = mergeRunTranscript(listOf(local), listOf(run("r1", "2026-10-09T22:00:00Z")))

        assertEquals(ms("2026-10-09T21:59:58Z"), merged.first { it.kind == "user" }.atMs)
    }

    @Test
    fun aLineWithoutATimeGainsOneFromTheServerRun() {
        val old = TranscriptLine("user-r1", "user", "question r1", "r1")

        val merged = mergeRunTranscript(listOf(old), listOf(run("r1", "2026-10-09T22:00:00Z")))

        assertEquals(ms("2026-10-09T22:00:00Z"), merged.first { it.kind == "user" }.atMs)
    }

    @Test
    fun pickLineKeepsAKnownTimeWhenOneSideHasNone() {
        val a = TranscriptLine("assistant-r1", "assistant", "short", "r1", atMs = 500)
        val b = TranscriptLine("assistant-r1", "assistant", "short but longer", "r1")

        assertEquals(500L, pickLine(a, b).atMs)
        assertEquals(500L, pickLine(b, a).atMs)
    }

    @Test
    fun conversationMessagesCarryTheirCreatedAt() {
        val messages = listOf(
            ConversationMessage("m1", "user_message", "hello", "2026-10-09T22:00:00Z"),
            ConversationMessage("m2", "assistant_message", "hi there", "2026-10-09T22:00:09Z"),
            ConversationMessage("m3", "user_message", "again", null),
        )

        val merged = mergeConversationTranscript(emptyList(), messages)

        assertEquals(
            listOf(ms("2026-10-09T22:00:00Z"), ms("2026-10-09T22:00:09Z"), null),
            merged.map { it.atMs },
        )
    }

    @Test
    fun conversationTimeFillsAMatchingLocalLineThatHasNone() {
        val local = TranscriptLine("user-local-1", "user", "hello", atMs = null)

        val merged = mergeConversationTranscript(
            listOf(local),
            listOf(ConversationMessage("m1", "user_message", "hello", "2026-10-09T22:00:00Z")),
        )

        assertEquals(1, merged.size)
        assertEquals(ms("2026-10-09T22:00:00Z"), merged.single().atMs)
    }

    @Test
    fun conversationDoesNotOverwriteALocalTime() {
        val local = TranscriptLine("user-local-1", "user", "hello", atMs = 123)

        val merged = mergeConversationTranscript(
            listOf(local),
            listOf(ConversationMessage("m1", "user_message", "hello", "2026-10-09T22:00:00Z")),
        )

        assertEquals(123L, merged.single().atMs)
    }

    @Test
    fun badCreatedAtIsIgnored() {
        val merged = mergeConversationTranscript(
            emptyList(),
            listOf(ConversationMessage("m1", "user_message", "hello", "yesterday-ish")),
        )

        assertNull(merged.single().atMs)
    }

    @Test
    fun timesSurviveSaveAndLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ConversationStore(context)
        val lines = listOf(
            TranscriptLine("user-r1", "user", "hi", "r1", atMs = 1_700_000_000_000),
            TranscriptLine("assistant-r1", "assistant", "yo", "r1"),
        )

        store.save("agent-ts", lines, immediate = true)
        val loaded = ConversationStore(context).load("agent-ts")

        assertEquals(listOf<Long?>(1_700_000_000_000, null), loaded.map { it.atMs })
    }

    @Test
    fun oldSavedLinesWithoutATimeStillLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = java.io.File(context.filesDir, "conversations").apply { mkdirs() }
        java.io.File(dir, "agent-old.json").writeText(
            """{"lines":[{"id":"user-r1","kind":"user","text":"hi","runId":"r1"}]}""",
        )

        val loaded = ConversationStore(context).load("agent-old")

        assertEquals(1, loaded.size)
        assertNull(loaded.single().atMs)
    }

    @Test
    fun newMessagesSeenWhileWatchingAreStampedWithTheMomentTheyArrived() {
        val local = TranscriptLine("user-m1", "user", "go", atMs = 1_000)
        val messages = listOf(
            ConversationMessage("m1", "user_message", "go"),
            ConversationMessage("m2", "assistant_message", "Now Grid.kt model functions."),
        )

        val merged = mergeConversationTranscript(listOf(local), messages, seenAtMs = 5_000)

        assertEquals(listOf<Long?>(1_000, 5_000), merged.map { it.atMs })
    }

    @Test
    fun aServerTimeBeatsTheMomentSeen() {
        val messages = listOf(ConversationMessage("m2", "assistant_message", "hello", "2026-10-09T22:00:09Z"))

        val merged = mergeConversationTranscript(emptyList(), messages, seenAtMs = 5_000)

        assertEquals(ms("2026-10-09T22:00:09Z"), merged.single().atMs)
    }

    @Test
    fun withoutAWatchedMomentNewMessagesStayUntimed() {
        val messages = listOf(ConversationMessage("m2", "assistant_message", "hello"))

        assertNull(mergeConversationTranscript(emptyList(), messages).single().atMs)
    }

    @Test
    fun aStampedMessageKeepsItsTimeOnTheNextMerge() {
        val messages = listOf(ConversationMessage("m2", "assistant_message", "hello"))
        val first = mergeConversationTranscript(emptyList(), messages, seenAtMs = 5_000)

        val second = mergeConversationTranscript(first, messages, seenAtMs = 9_000)

        assertEquals(5_000L, second.single().atMs)
    }

    private val wire = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    private fun decode(json: String) = wire.decodeFromString<ConversationMessage>(json)

    @Test
    fun messageTimeReadsFromAnyOfTheLikelyFields() {
        listOf("createdAt", "created_at", "timestamp", "time", "sentAt").forEach { field ->
            val msg = decode("""{"id":"m1","type":"assistant_message","text":"hi","$field":"2026-10-09T22:00:09Z"}""")
            assertEquals(field, ms("2026-10-09T22:00:09Z"), parseMessageTimeMs(msg.createdAt))
        }
    }

    @Test
    fun messageTimeAcceptsEpochSecondsAndMilliseconds() {
        val seconds = decode("""{"id":"m1","type":"user_message","text":"hi","timestamp":1791583209}""")
        val millis = decode("""{"id":"m2","type":"user_message","text":"hi","createdAt":1791583209000}""")

        assertEquals(1_791_583_209_000L, parseMessageTimeMs(seconds.createdAt))
        assertEquals(1_791_583_209_000L, parseMessageTimeMs(millis.createdAt))
    }

    @Test
    fun messageWithoutATimeOrWithJunkStillDecodes() {
        assertNull(decode("""{"id":"m1","type":"user_message","text":"hi"}""").createdAt)
        assertNull(decode("""{"id":"m1","type":"user_message","text":"hi","createdAt":null}""").createdAt)
        assertNull(decode("""{"id":"m1","type":"user_message","text":"hi","createdAt":{"a":1}}""").createdAt)
        assertNull(parseMessageTimeMs(decode("""{"text":"x","createdAt":"soon"}""").createdAt))
    }
}
