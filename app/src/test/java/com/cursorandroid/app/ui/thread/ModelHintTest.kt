package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelSelection
import com.cursorandroid.app.data.repo.RunModelBook
import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelHintTest {
    private val catalog = listOf(ModelItem(id = "composer-2.5", displayName = "Composer 2.5"))

    @Test
    fun readsTheFirstLineCaseInsensitively() {
        assertEquals("Opus 5.5", promptModelHint("Model: Opus 5.5\nfix the widget"))
        assertEquals("Opus 5.5", promptModelHint("model:Opus 5.5"))
        assertEquals("Opus 5.5", promptModelHint("  MODEL :   Opus 5.5   \nrest"))
    }

    @Test
    fun mapsKnownClaudeIds() {
        assertEquals("Opus 5.5", promptModelHint("Model: claude-opus-5-5"))
        assertEquals("Sonnet 5.5", promptModelHint("Model: claude-sonnet-5-5"))
        assertEquals("Opus 5.5", promptModelHint("Model: CLAUDE-OPUS-5-5"))
    }

    @Test
    fun otherTextIsShownAsWritten() {
        assertEquals("gpt-5.6-sol", promptModelHint("Model: gpt-5.6-sol"))
        assertEquals("My Custom Model v2", promptModelHint("Model: My Custom Model v2"))
        assertEquals("claude-opus-5", promptModelHint("Model: claude-opus-5"))
    }

    @Test
    fun onlyTheFirstLineCounts() {
        assertNull(promptModelHint("fix the widget\nModel: Opus 5.5"))
        assertNull(promptModelHint("\nModel: Opus 5.5"))
        assertNull(promptModelHint("Models: Opus"))
        assertNull(promptModelHint("The model: is slow"))
        assertNull(promptModelHint("Model:"))
        assertNull(promptModelHint("Model:   "))
        assertNull(promptModelHint(""))
        assertNull(promptModelHint(null))
    }

    @Test
    fun hintsComeFromEachRunsFirstUserMessage() {
        val lines = listOf(
            TranscriptLine("user-r1", "user", "Model: Opus 5.5\nfirst", "r1"),
            TranscriptLine("assistant-r1", "assistant", "ok", "r1"),
            TranscriptLine("user-r1b", "user", "Model: Sonnet 5.5", "r1"),
            TranscriptLine("user-r2", "user", "no hint here", null),
            TranscriptLine("user-local-1", "user", "Model: Nope", null),
            TranscriptLine("user-r3", "user", "Model: claude-sonnet-5-5", null),
        )
        assertEquals(mapOf("r1" to "Opus 5.5", "r3" to "Sonnet 5.5"), runModelHints(lines))
    }

    @Test
    fun hintTextStaysInTheMessage() {
        val text = "Model: Opus 5.5\nfix the widget"
        val line = TranscriptLine("user-r1", "user", text, "r1")
        runModelHints(listOf(line))
        assertEquals(text, line.text)
    }

    @Test
    fun priorityApiThenRecordedThenHintThenAgentThenNone() {
        val hints = mapOf("r1" to "Opus 5.5")
        val api = ModelSelection("composer-2.5")
        val sent = ModelSelection("sent-model")
        val agent = ModelSelection("agent-model")

        val withApi = RunModelBook().withRun("r1", sent, explicit = true).withApi(mapOf("r1" to api), agent)
        assertEquals("Composer 2.5", runModelLabel(withApi, "r1", hints, catalog))

        val recorded = RunModelBook().withRun("r1", sent, explicit = true).withApi(emptyMap(), agent)
        assertEquals("sent-model", runModelLabel(recorded, "r1", hints, catalog))

        val unknownRun = RunModelBook().withApi(emptyMap(), agent)
        assertEquals("Opus 5.5", runModelLabel(unknownRun, "r1", hints, catalog))
        assertEquals("agent-model", runModelLabel(unknownRun, "r2", hints, catalog))

        val defaultRun = RunModelBook().withRun("r1", null, explicit = true).withApi(emptyMap(), agent)
        assertEquals("Opus 5.5", runModelLabel(defaultRun, "r1", hints, catalog))

        assertNull(runModelLabel(RunModelBook(), "r9", hints, catalog))
        assertEquals("Agent", senderLabel(runModelLabel(RunModelBook(), "r9", hints, catalog)))
    }

    @Test
    fun hintBeatsTheLatestRecordedGuess() {
        val book = RunModelBook().withRun("rOld", ModelSelection("sent-model"), explicit = true)
        assertEquals("Sonnet 5.5", runModelLabel(book, "r5", mapOf("r5" to "Sonnet 5.5"), catalog))
        assertEquals("sent-model", runModelLabel(book, "r6", emptyMap(), catalog))
    }
}
