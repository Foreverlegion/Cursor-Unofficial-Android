package com.cursorandroid.app.data.api

import com.cursorandroid.app.data.repo.ClientOrigin
import com.cursorandroid.app.data.repo.ImmediateSend
import com.cursorandroid.app.data.repo.ProfileRule
import com.cursorandroid.app.data.repo.agentIdEnvVarsError
import com.cursorandroid.app.data.repo.assembleCreate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloudFeatureTest {
    private val json = Json { explicitNulls = false; ignoreUnknownKeys = true }

    @Test
    fun noRepoCreateOmitsReposAndEnv() {
        val text = json.encodeToString(CreateAgentRequest.serializer(), CreateAgentRequest(prompt = Prompt("hi")))
        assertTrue(text.contains("\"prompt\""))
        assertFalse(text.contains("\"repos\""))
        assertFalse(text.contains("\"env\""))
        assertFalse(text.contains("\"envVars\""))
        assertFalse(text.contains("\"agentId\""))
    }

    @Test
    fun profileRequestIsPromptOnlyAndExact() {
        val body = ProfileRule.request()
        assertEquals(ProfileRule.TEXT, body.prompt.text)
        assertNull(body.repos)
        assertNull(body.env)
        assertNull(body.envVars)
        assertNull(body.agentId)
        assertTrue(body.prompt.text.startsWith("You are a one-time setup agent."))
        assertTrue(body.prompt.text.contains("both `repos` and `env` omitted"))
        assertTrue(body.prompt.text.contains("com.cursorandroid.app"))
        assertFalse(body.prompt.text.contains("Thermal Nexus"))
        assertFalse(body.prompt.text.contains("Zenbook"))
        assertFalse(body.prompt.text.contains("HomeNet"))
        val text = json.encodeToString(CreateAgentRequest.serializer(), body)
        assertFalse(text.contains("\"repos\""))
        assertFalse(text.contains("\"env\""))
        assertTrue(text.contains(ProfileRule.AGENT_NAME))
    }

    @Test
    fun promptImageEncodesUrlOrData() {
        val url = json.encodeToString(PromptImage.serializer(), PromptImage(url = "https://example.com/a.png"))
        assertTrue(url.contains("\"url\":\"https://example.com/a.png\""))
        assertFalse(url.contains("mimeType"))
        assertFalse(url.contains("\"data\""))
        val data = json.encodeToString(PromptImage.serializer(), PromptImage(data = "abc", mimeType = "image/png"))
        assertTrue(data.contains("\"data\":\"abc\""))
        assertTrue(data.contains("\"mimeType\":\"image/png\""))
        assertFalse(data.contains("\"url\""))
    }

    @Test
    fun agentIdOmitsEnvVars() {
        val prepared = assembleCreate(
            CreateAgentRequest(prompt = Prompt("hi"), agentId = "bc-00000000-0000-0000-0000-000000000001"),
        )
        assertEquals("bc-00000000-0000-0000-0000-000000000001", prepared.agentId)
        assertNull(prepared.envVars)
        assertTrue(prepared.prompt.text.startsWith(ClientOrigin.PREFIX))
        val text = json.encodeToString(CreateAgentRequest.serializer(), prepared)
        assertTrue(text.contains("\"agentId\""))
        assertFalse(text.contains("envVars"))
    }

    @Test
    fun agentIdCannotCombineWithEnvVars() {
        val error = agentIdEnvVarsError("bc-1", mapOf("TOKEN" to "x"))
        assertEquals("agentId cannot be combined with env vars.", error)
        try {
            assembleCreate(CreateAgentRequest(prompt = Prompt("hi"), agentId = "bc-1", envVars = mapOf("TOKEN" to "x")))
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("env"))
        }
        assertEquals("Agent id must look like bc-…", agentIdEnvVarsError("nope", null))
    }

    @Test
    fun blankAgentIdStillSendsClientEnvVar() {
        val prepared = assembleCreate(CreateAgentRequest(prompt = Prompt("hi"), agentId = "  "))
        assertNull(prepared.agentId)
        assertEquals(ClientOrigin.ID, prepared.envVars?.get(ClientOrigin.KEY))
    }

    @Test
    fun sendImmediatelyCancelsThenCreates() = runBlocking {
        val order = ArrayList<String>()
        val run = ImmediateSend.cancelThenRun(
            agentId = "bc-1",
            activeRunId = "run-1",
            prompt = Prompt("go"),
            cancel = { _, runId ->
                order += "cancel:$runId"
            },
            createRun = { _, prompt ->
                order += "run:${prompt.text}"
                Run(id = "run-2")
            },
        )
        assertEquals("run-2", run.id)
        assertEquals(listOf("cancel:run-1", "run:go"), order)
    }

    @Test
    fun sendImmediatelySkipsCancelWhenIdle() = runBlocking {
        val order = ArrayList<String>()
        ImmediateSend.cancelThenRun(
            "bc-1",
            "  ",
            Prompt("go"),
            cancel = { _, _ -> order += "cancel" },
            createRun = { _, _ ->
                order += "run"
                Run(id = "run-2")
            },
        )
        assertEquals(listOf("run"), order)
    }

    @Test
    fun cancelFailureDoesNotCreateTheNextRun() = runBlocking {
        var created = false
        try {
            ImmediateSend.cancelThenRun(
                "bc-1",
                "run-1",
                Prompt("go"),
                cancel = { _, _ -> error("cancel failed") },
                createRun = { _, _ ->
                    created = true
                    Run(id = "run-2")
                },
            )
            fail("expected cancel failure")
        } catch (_: IllegalStateException) {
        }
        assertFalse(created)
    }

    @Test
    fun promoteMovesTheQueuedItemToTheFront() {
        assertEquals(listOf("b", "a", "c"), ImmediateSend.promote(listOf("a", "b", "c"), "b"))
        assertNull(ImmediateSend.promote(listOf("a"), "missing"))
    }

    @Test
    fun environmentListPatchAndHistoryModels() {
        val listed = json.decodeFromString<EnvironmentListResponse>(
            """{"items":[{"id":"8f14e45f-ceea-4e6b-9c3a-1d2e3f4a5b6c","name":"Web","owner":"team","repos":[{"url":"https://github.com/acme/app"}]}],"nextCursor":"MjA"}""",
        )
        assertEquals("Web", listed.items.single().name)
        assertEquals("MjA", listed.nextCursor)
        val patch = json.encodeToString(
            UpdateEnvironmentRequest.serializer(),
            UpdateEnvironmentRequest(name = "Web app", environmentJson = """{"install":"pnpm install"}"""),
        )
        assertTrue(patch.contains("\"name\":\"Web app\""))
        assertTrue(patch.contains("environmentJson"))
        val history = json.decodeFromString<EnvironmentHistoryResponse>(
            """{"items":[{"id":"e1","createdAt":"2026-09-29T21:05:00.000Z","kind":"updated","title":"Team environment updated","description":"Install script changed.","source":"api","current":true}]}""",
        )
        val event = history.items.single()
        assertEquals("updated", event.kind)
        assertEquals("api", event.source)
        assertEquals("Team environment updated · updated · api", event.line())
        assertEquals("Install script changed.", event.description)
    }

    @Test
    fun secretPutOmitsValueFromListModel() {
        val listed = json.decodeFromString<SecretListResponse>(
            """{"items":[{"id":"secv_a","name":"NPM_TOKEN","type":"runtime_secret","repos":["github.com/acme/api"],"createdAt":"2026-10-01T09:15:42.000Z"}],"nextCursor":null}""",
        )
        val item = listed.items.single()
        assertEquals("NPM_TOKEN", item.name)
        assertEquals("runtime_secret", item.type)
        assertFalse(json.encodeToString(SecretVersion.serializer(), item).contains("value"))
        val put = json.encodeToString(
            PutSecretRequest.serializer(),
            PutSecretRequest(value = "npm_example_token", type = "environment_variable", repos = listOf("github.com/acme/api")),
        )
        assertTrue(put.contains("\"value\":\"npm_example_token\""))
        assertTrue(put.contains("environment_variable"))
        val versions = secretVersionsFromBody(
            """{"error":{"code":"secret_name_ambiguous","versions":[{"id":"secv_a","repos":["github.com/acme/api"]},{"id":"secv_b","repos":[]}]}}""",
        )
        assertEquals(listOf("secv_a", "secv_b"), versions.map { it.id })
        assertEquals("That secret name has more than one version. Pick one.", ApiException(409, "conflict", "secret_name_ambiguous").displayMessage())
        assertEquals("That agent id is already in use.", ApiException(409, "taken", "agent_id_conflict").displayMessage())
        assertEquals("Name is required", secretNameError(" "))
        assertEquals("Value is required", secretValueError(""))
        assertNull(secretNameError("NPM_TOKEN"))
    }

    @Test
    fun conversationNullMessagesDoNotFail() {
        val missing = json.decodeFromString<AgentConversation>("""{"id":"bc-1","messages":null}""")
        assertTrue(missing.messages.isEmpty())
        val junk = json.decodeFromString<AgentConversation>(
            """{"id":"bc-1","messages":[null,{"type":"user_message","text":"hi"},{"type":99}]}""",
        )
        assertEquals(1, junk.messages.size)
        assertEquals("hi", junk.messages.single().text)
        assertEquals("user", junk.messages.single().transcriptKind())
    }
}
