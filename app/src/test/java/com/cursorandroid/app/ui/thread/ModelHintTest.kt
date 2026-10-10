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
        assertNull(promptModelHint("Note: Model: Opus 5.5"))
        assertNull(promptModelHint("Please use Model: Opus 5.5"))
        assertNull(promptModelHint("Models: Opus"))
        assertNull(promptModelHint("The model: is slow"))
        assertNull(promptModelHint("Model:"))
        assertNull(promptModelHint("Model:   "))
        assertNull(promptModelHint(""))
        assertNull(promptModelHint(null))
    }

    @Test
    fun toleratesWhitespaceCrlfMarkdownAndLeadingBlankLines() {
        assertEquals("Sonnet 5.5", promptModelHint("Model: Sonnet 5.5\r\n\r\nfix it"))
        assertEquals("Sonnet 5.5", promptModelHint("\r\n\r\n   Model: Sonnet 5.5   \r\nrest"))
        assertEquals("Sonnet 5.5", promptModelHint("**Model:** Sonnet 5.5"))
        assertEquals("Sonnet 5.5", promptModelHint("**Model: Sonnet 5.5**"))
        assertEquals("Sonnet 5.5", promptModelHint("> Model: Sonnet 5.5"))
        assertEquals("Sonnet 5.5", promptModelHint("- Model: Sonnet 5.5"))
        assertEquals("Sonnet 5.5", promptModelHint("`Model: Sonnet 5.5`"))
        assertEquals("Sonnet 5.5", promptModelHint("Model: Sonnet 5.5\u00a0"))
    }

    @Test
    fun priorityApiThenRecordedThenHintThenAgentThenNone() {
        val hint = "Opus 5.5"
        val api = ModelSelection("composer-2.5")
        val sent = ModelSelection("sent-model")
        val agent = ModelSelection("agent-model")

        val withApi = RunModelBook().withRun("r1", sent, explicit = true).withApi(mapOf("r1" to api), agent)
        assertEquals("Composer 2.5", runModelLabel(withApi, "r1", hint, catalog))

        val recorded = RunModelBook().withRun("r1", sent, explicit = true).withApi(emptyMap(), agent)
        assertEquals("sent-model", runModelLabel(recorded, "r1", hint, catalog))

        val unknownRun = RunModelBook().withApi(emptyMap(), agent)
        assertEquals("Opus 5.5", runModelLabel(unknownRun, "r1", hint, catalog))
        assertEquals("agent-model", runModelLabel(unknownRun, "r2", null, catalog))

        val defaultRun = RunModelBook().withRun("r1", null, explicit = true).withApi(emptyMap(), agent)
        assertEquals("Opus 5.5", runModelLabel(defaultRun, "r1", hint, catalog))

        assertNull(runModelLabel(RunModelBook(), "r9", null, catalog))
        assertEquals("Agent", senderLabel(runModelLabel(RunModelBook(), "r9", null, catalog)))
    }

    @Test
    fun hintBeatsTheLatestRecordedGuess() {
        val book = RunModelBook().withRun("rOld", ModelSelection("sent-model"), explicit = true)
        assertEquals("Sonnet 5.5", runModelLabel(book, "r5", "Sonnet 5.5", catalog))
        assertEquals("sent-model", runModelLabel(book, "r6", null, catalog))
    }

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    // List Runs returns the prompt as an object, Get Run can return it as a plain string.
    private val runListPayload = """
        {"items":[
          {"id":"run-3","agentId":"bc-1","status":"RUNNING","createdAt":"2026-10-10T11:00:00Z",
           "prompt":{"text":"Model: Sonnet 5.5\n\nFix the ghosting in the widget."}},
          {"id":"run-2","agentId":"bc-1","status":"FINISHED","createdAt":"2026-10-09T11:00:00Z",
           "prompt":{"text":"Model: Opus 5.5\r\n\r\nRender the previews."}},
          {"id":"run-1","agentId":"bc-1","status":"FINISHED","createdAt":"2026-10-08T11:00:00Z",
           "prompt":{"text":"Build the widget."}}
        ],"nextCursor":null}
    """.trimIndent()

    private val getRunPayload = """
        {"id":"run-3","agentId":"bc-1","status":"RUNNING","createdAt":"2026-10-10T11:00:00Z",
         "prompt":"Model: Sonnet 5.5\n\nFix the ghosting in the widget."}
    """.trimIndent()

    private val conversationPayload = """
        {"id":"bc-1","messages":[
          {"id":"m1","type":"user_message","text":"Build the widget."},
          {"id":"m2","type":"assistant_message","text":"Built it."},
          {"id":"m3","type":"user_message","text":"Model: Opus 5.5\r\n\r\nRender the previews."},
          {"id":"m4","type":"assistant_message","text":"Rendered."},
          {"id":"m5","type":"user_message","text":"Model: Sonnet 5.5\n\nFix the ghosting in the widget."},
          {"id":"m6","type":"assistant_message","text":"I'll start with the ghosting."}
        ]}
    """.trimIndent()

    @Test
    fun runPromptsFromListAndGetRunCarryTheHint() {
        val runs = json.decodeFromString<com.cursorandroid.app.data.api.RunListResponse>(runListPayload).items
        val byId = runs.associate { it.id to promptModelHint(it.prompt?.text) }
        assertEquals(mapOf("run-3" to "Sonnet 5.5", "run-2" to "Opus 5.5", "run-1" to null), byId)
        val full = json.decodeFromString<com.cursorandroid.app.data.api.Run>(getRunPayload)
        assertEquals("Sonnet 5.5", promptModelHint(full.prompt?.text))
    }

    @Test
    fun conversationMessagesFromAnotherClientGetTheirTurnsHint() {
        val convo = json.decodeFromString<com.cursorandroid.app.data.api.AgentConversation>(conversationPayload)
        val lines = com.cursorandroid.app.data.repo.mergeConversationTranscript(emptyList(), convo.messages)
        val hints = lineModelHints(lines, emptyMap())
        assertNull(hints["assistant-m2"])
        assertEquals("Opus 5.5", hints["assistant-m4"])
        assertEquals("Sonnet 5.5", hints["assistant-m6"])
    }

    @Test
    fun aRunWithoutAHintUsesTheMostRecentEarlierOne() {
        val convo = json.decodeFromString<com.cursorandroid.app.data.api.AgentConversation>(
            """{"messages":[
              {"id":"a","type":"user_message","text":"Model: Opus 5.5\nfirst"},
              {"id":"b","type":"assistant_message","text":"one"},
              {"id":"c","type":"user_message","text":"second, no hint"},
              {"id":"d","type":"assistant_message","text":"two"}
            ]}""",
        )
        val lines = com.cursorandroid.app.data.repo.mergeConversationTranscript(emptyList(), convo.messages)
        val hints = lineModelHints(lines, emptyMap())
        assertEquals("Opus 5.5", hints["assistant-b"])
        assertEquals("Opus 5.5", hints["assistant-d"])
    }

    @Test
    fun aRunsOwnPromptBeatsTheTranscriptPosition() {
        val lines = listOf(
            TranscriptLine("user-m1", "user", "Model: Opus 5.5", null),
            TranscriptLine("assistant-run-3", "assistant", "text", "run-3"),
        )
        assertEquals("Sonnet 5.5", lineModelHints(lines, mapOf("run-3" to "Sonnet 5.5"))["assistant-run-3"])
        assertEquals("Opus 5.5", lineModelHints(lines, emptyMap())["assistant-run-3"])
    }

    @Test
    fun theWorkingPillUsesTheLiveRunsOwnPromptThenTheLatestHint() {
        val lines = listOf(
            TranscriptLine("user-m1", "user", "Model: Opus 5.5\nfirst", null),
            TranscriptLine("user-m2", "user", "later, no hint", null),
        )
        assertEquals("Sonnet 5.5", currentModelHint(lines, mapOf("run-3" to "Sonnet 5.5"), "run-3"))
        assertEquals("Opus 5.5", currentModelHint(lines, emptyMap(), "run-3"))
        assertNull(currentModelHint(emptyList(), emptyMap(), "run-3"))
    }

    @Test
    fun hintTextStaysInTheMessage() {
        val text = "Model: Opus 5.5\nfix the widget"
        val convo = json.decodeFromString<com.cursorandroid.app.data.api.AgentConversation>(
            """{"messages":[{"id":"a","type":"user_message","text":"Model: Opus 5.5\nfix the widget"}]}""",
        )
        val lines = com.cursorandroid.app.data.repo.mergeConversationTranscript(emptyList(), convo.messages)
        assertEquals(text, lines.single().text)
    }
}
