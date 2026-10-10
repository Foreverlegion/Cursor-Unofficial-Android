package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.ModelParameter
import com.cursorandroid.app.data.api.ModelSelection
import com.cursorandroid.app.data.api.ModelValue
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.repo.RunModelBook
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLabelTest {
    private val catalog = listOf(
        ModelItem(
            id = "claude-opus-5-5",
            displayName = "Claude Opus 5.5",
            aliases = listOf("opus"),
            parameters = listOf(
                ModelParameter(
                    id = "effort",
                    displayName = "Effort",
                    values = listOf(ModelValue("high", "High"), ModelValue("low", "Low")),
                ),
            ),
        ),
        ModelItem(id = "composer-2.5", displayName = "Composer 2.5"),
    )

    @Test
    fun usesFriendlyNameAndEffort() {
        val sel = ModelSelection("claude-opus-5-5", listOf(ModelParam("effort", "high")))
        assertEquals("Claude Opus 5.5 · High", modelLabel(sel, catalog))
    }

    @Test
    fun matchesAliasAndSkipsNonEffortParams() {
        val sel = ModelSelection("opus", listOf(ModelParam("fast", "true")))
        assertEquals("Claude Opus 5.5", modelLabel(sel, catalog))
    }

    @Test
    fun unknownIdFallsBackToRawId() {
        assertEquals("mystery", modelLabel(ModelSelection("mystery"), catalog))
        assertEquals("mystery · max", modelLabel(ModelSelection("mystery", listOf(ModelParam("effort", "max"))), catalog))
    }

    @Test
    fun blankOrMissingIsOmitted() {
        assertNull(modelLabel(null, catalog))
        assertNull(modelLabel(ModelSelection(" "), catalog))
        assertEquals("Agent", senderLabel(null))
        assertEquals("Agent · Composer 2.5", senderLabel("Composer 2.5"))
    }

    @Test
    fun perRunModelWinsOverAgentFallback() {
        val opus = ModelSelection("claude-opus-5-5")
        val composer = ModelSelection("composer-2.5")
        val book = RunModelBook()
            .withRun("r1", opus, explicit = true)
            .withRun("r2", composer, explicit = false)
        assertEquals(opus, book.resolve("r1"))
        assertEquals(composer, book.resolve("r2"))
        assertEquals(composer, book.resolve("unknown"))
        assertEquals(composer, book.resolve(null))
    }

    @Test
    fun runWithoutModelDoesNotInheritNewerChoice() {
        val book = RunModelBook()
            .withRun("r1", null, explicit = true)
            .withRun("r2", ModelSelection("composer-2.5"), explicit = false)
        assertNull(book.resolve("r1"))
        assertEquals("composer-2.5", book.resolve("r2")?.id)
    }

    @Test
    fun followUpWithoutModelRecordsNothing() {
        val book = RunModelBook().withRun("r1", null, explicit = false)
        assertTrue(book.runs.isEmpty())
        assertNull(book.resolve("r1"))
    }

    @Test
    fun apiModelsOverrideLocalAndAgentModelIsFallback() {
        val local = RunModelBook().withRun("r1", ModelSelection("composer-2.5"), explicit = true)
        val api = local.withApi(
            mapOf("r1" to ModelSelection("claude-opus-5-5")),
            ModelSelection("claude-opus-5-5"),
        )
        assertEquals("claude-opus-5-5", api.resolve("r1")?.id)
        assertEquals("claude-opus-5-5", api.resolve("other")?.id)
        assertEquals("composer-2.5", api.latest?.id)
    }

    @Test
    fun bookIsCapped() {
        var book = RunModelBook()
        repeat(RunModelBook.MAX_RUNS + 20) { book = book.withRun("r$it", ModelSelection("m"), explicit = true) }
        assertEquals(RunModelBook.MAX_RUNS, book.runs.size)
        assertTrue("r0" !in book.runs)
        assertTrue("r${RunModelBook.MAX_RUNS + 19}" in book.runs)
    }

    @Test
    fun runModelParsesFromObjectStringOrJunk() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
        val obj = json.decodeFromString<Run>("""{"id":"a","model":{"id":"m1","params":[{"id":"effort","value":"high"}]}}""")
        assertEquals("m1", obj.model?.id)
        assertEquals("high", obj.model?.params?.first()?.value)
        assertEquals("m2", json.decodeFromString<Run>("""{"id":"a","model":"m2"}""").model?.id)
        assertNull(json.decodeFromString<Run>("""{"id":"a","model":null}""").model)
        assertNull(json.decodeFromString<Run>("""{"id":"a"}""").model)
        assertEquals("", json.decodeFromString<Run>("""{"id":"a","model":42}""").model?.id)
    }
}
