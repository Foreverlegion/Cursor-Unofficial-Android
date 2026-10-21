package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.ConversationMessage
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.api.StreamEvent
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.data.repo.dropSettledAssistant
import com.cursorandroid.app.data.repo.mergeConversationTranscript
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTextTest {
    private val oldSentence =
        "its psync and per-file results will be kept, while the qd64 and fine64-re32 qd64 completed at 9977."
    private val revised =
        "its psync and per-file results kept as finished samples, while the qd64 and fine64-re32 qd64 completed at 9977."
    private val revisedTail =
        "kept as finished samples, while the qd64 and fine64-re32 qd64 completed at 9977."

    @Test
    fun leadingSpaceOnADeltaIsKept() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "Done.")
        fold.onAssistant("2", " Next sentence.")
        assertEquals("Done. Next sentence.", fold.assistantText())
    }

    @Test
    fun sentenceSplitAcrossDeltasStaysOneSegment() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "The box hit a ")
        fold.onAssistant("2", "real cold run.")
        assertEquals("The box hit a real cold run.", fold.assistantText())
    }

    @Test
    fun thinkingDoesNotSplitTheAssistantSentence() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "The box hit a real")
        fold.onThinking("t1", "check the key")
        fold.onAssistant("2", " cold cache. The key, not the value")
        fold.onThinking("t2", "still building")
        fold.onAssistant("3", ", is still unbuilt.")
        assertEquals(
            "The box hit a real cold cache. The key, not the value, is still unbuilt.",
            fold.assistantText(),
        )
        assertEquals("check the keystill building", fold.thinkingText())
    }

    @Test
    fun thinkingDeltasKeepTheirOwnLeadingSpace() {
        val fold = RunStreamFold()
        fold.onThinking("1", "Look at the header.")
        fold.onThinking("2", " The value is still unbuilt.")
        assertEquals("Look at the header. The value is still unbuilt.", fold.thinkingText())
    }

    @Test
    fun toolCallsSeparateAssistantSegments() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "ship a release APK.")
        fold.onTool("c1")
        fold.onTool("c1-done")
        fold.onAssistant("2", "The compositor returns")
        fold.onTool("c2")
        fold.onAssistant("3", "Shipped.")
        assertEquals(
            "ship a release APK.\n\nThe compositor returns\n\nShipped.",
            fold.assistantText(),
        )
    }

    @Test
    fun cumulativeSnapshotAfterAToolDoesNotDuplicateEarlierText() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "ship a release APK.")
        fold.onTool("c1")
        fold.onAssistant("2", "ship a release APK.\n\nThe compositor returns")
        assertEquals("ship a release APK.\n\nThe compositor returns", fold.assistantText())
    }

    @Test
    fun cumulativeSnapshotsReplaceTheOpenSegment() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "The box hit a real")
        fold.onAssistant("2", "The box hit a real cold cache. The key, not the value")
        fold.onAssistant("3", "The box hit a real cold cache. The key, not the value, is still unbuilt.")
        assertEquals(
            "The box hit a real cold cache. The key, not the value, is still unbuilt.",
            fold.assistantText(),
        )
    }

    @Test
    fun revisedSnapshotReplacesTheSegment() {
        val fold = RunStreamFold()
        fold.onAssistant("1", oldSentence)
        fold.onAssistant("2", revised)
        assertEquals(revised, fold.assistantText())
        assertFalse(fold.assistantText().contains("whilekept"))
        assertFalse(fold.assistantText().contains("will be kept"))
    }

    @Test
    fun revisedTailIsAppliedAtTheOverlapInsteadOfAppended() {
        val fold = RunStreamFold()
        fold.onAssistant("1", oldSentence)
        fold.onAssistant("2", revisedTail)
        assertEquals(revised, fold.assistantText())
        assertFalse(fold.assistantText().contains("whilekept"))
    }

    @Test
    fun thinkingRevisionUsesTheSameRule() {
        val fold = RunStreamFold()
        fold.onThinking("1", oldSentence)
        fold.onThinking("2", revised)
        assertEquals(revised, fold.thinkingText())
    }

    @Test
    fun staleShorterSnapshotDoesNotTruncate() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "Hello, world")
        fold.onAssistant("2", "Hello")
        assertEquals("Hello, world", fold.assistantText())
    }

    @Test
    fun reconnectReplayDoesNotDuplicate() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "The box hit a ")
        fold.onAssistant("2", "real.")
        fold.onThinking("3", "check ")
        fold.onThinking("4", "the header")
        fold.onTool("5")
        fold.onAssistant("6", "Still unbuilt.")
        fold.onAssistant("1", "The box hit a ")
        fold.onAssistant("2", "real.")
        fold.onThinking("3", "check ")
        fold.onThinking("4", "the header")
        fold.onTool("5")
        fold.onAssistant("6", "Still unbuilt.")
        fold.onAssistant("7", " Done.")
        assertEquals("The box hit a real.\n\nStill unbuilt. Done.", fold.assistantText())
        assertEquals("check the header", fold.thinkingText())
    }

    @Test
    fun repeatedSnapshotWithoutANewIdIsIdempotent() {
        val fold = RunStreamFold()
        fold.onAssistant(null, "Hello")
        fold.onAssistant(null, "Hello world")
        fold.onAssistant(null, "Hello world")
        assertEquals("Hello world", fold.assistantText())
    }

    @Test
    fun markdownMarkersSurviveDeltas() {
        val fold = RunStreamFold()
        fold.onAssistant("1", "**Note.**")
        fold.onAssistant("2", " More.")
        fold.onAssistant("3", "\n\n```\n")
        fold.onAssistant("4", "code")
        fold.onAssistant("5", "\n```")
        assertEquals("**Note.** More.\n\n```\ncode\n```", fold.assistantText())
    }

    @Test
    fun parserKeepsLeadingSpacesInAssistantAndThinking() {
        val sse = SseStreamer(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val assistant = sse.parse("9", "assistant", """{"text":" Next"}""") as StreamEvent.Assistant
        val thinking = sse.parse("8", "thinking", """{"text":" still unbuilt."}""") as StreamEvent.Thinking
        assertEquals(" Next", assistant.text)
        assertEquals(" still unbuilt.", thinking.text)
    }

    @Test
    fun settledGarbledBubbleIsReplacedByServerBytes() {
        val server = " $revised"
        val garbled = oldSentence.replace(", while the", ", whilekept as finished samples, while the")
        val local = listOf(
            TranscriptLine("user-r1", "user", "status", "r1"),
            TranscriptLine("assistant-r1", "assistant", garbled, "r1"),
        )
        val messages = listOf(
            ConversationMessage("m1", "user_message", "status"),
            ConversationMessage("m2", "assistant_message", server),
        )
        val merged = mergeConversationTranscript(local, messages, setOf("r1"))
        val assistants = merged.filter { it.kind == "assistant" }
        assertEquals(listOf(server), assistants.map { it.text })
        assertTrue(dropSettledAssistant(garbled, listOf(TranscriptLine("assistant-m2", "assistant", server))))
    }

    @Test
    fun settledStreamKeepsATailTheServerHasNotListed() {
        val listed = "Shipped the APK."
        val extra = " One more thing the server has not listed."
        val local = listOf(TranscriptLine("assistant-r1", "assistant", listed + extra, "r1"))
        val messages = listOf(ConversationMessage("m2", "assistant_message", listed))
        val merged = mergeConversationTranscript(local, messages, setOf("r1"))
        assertEquals(listed + extra, merged.last().text)
    }
}
