package com.cursorandroid.app.data.api

import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.DraftSubagent
import com.cursorandroid.app.data.repo.toApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudApiGapsTest {
    @Test
    fun cloudCreateTargetUsesPrUrlAndDropsStartingRef() {
        val (_, repos) = cloudCreateTarget(
            fromSavedEnv = false,
            envName = "",
            repoUrl = "https://github.com/acme/app",
            startingRef = "main",
            prUrl = "https://github.com/acme/app/pull/12",
        )
        val repo = repos!!.single()
        assertEquals("https://github.com/acme/app", repo.url)
        assertEquals("https://github.com/acme/app/pull/12", repo.prUrl)
        assertNull(repo.startingRef)
    }

    @Test
    fun cloudCreateTargetKeepsBranchWithoutPr() {
        val (_, repos) = cloudCreateTarget(false, "", "https://github.com/acme/app", "develop")
        val repo = repos!!.single()
        assertEquals("develop", repo.startingRef)
        assertNull(repo.prUrl)
    }

    @Test
    fun machineCreateTargetSendsRepoWithMachineEnv() {
        val (env, repos) = machineCreateTarget("zenbook", "https://github.com/acme/app", "main")!!
        assertEquals("machine", env.type)
        assertEquals("zenbook", env.name)
        assertEquals("https://github.com/acme/app", repos!!.single().url)
        assertEquals("main", repos.single().startingRef)
    }

    @Test
    fun machineCreateTargetWithoutRepoStaysRepoLess() {
        val (env, repos) = machineCreateTarget("zenbook", "", null)!!
        assertEquals("zenbook", env.name)
        assertNull(repos)
    }

    @Test
    fun gitPathShowsWorkerRepoWhenNotInCatalog() {
        assertEquals("acme/app", gitPath("https://github.com/acme/app"))
    }

    @Test
    fun workerDecodesBoundRepoAndWorkspace() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val worker = json.decodeFromString<Worker>(
            """{"workerId":"w1","name":"zenbook","repoUrl":"https://github.com/acme/app","workspaceRootPath":"/home/mike/app","labels":[{"key":"name","value":"zenbook"}]}""",
        )
        assertEquals("https://github.com/acme/app", worker.boundRepo())
        assertEquals("/home/mike/app", worker.workspaceRootPath)
        assertEquals("zenbook", worker.displayName())
    }

    @Test
    fun modelDefaultParamsPreferDefaultVariant() {
        val model = ModelItem(
            id = "composer-2",
            variants = listOf(
                ModelVariant(params = listOf(ModelParam("fast", "false")), displayName = "Slow"),
                ModelVariant(params = listOf(ModelParam("fast", "true")), displayName = "Fast", isDefault = true),
            ),
        )
        assertEquals(listOf(ModelParam("fast", "true")), model.defaultParams())
        val picked = model.selection(listOf(ModelParam("fast", "true")))
        assertEquals("composer-2", picked.id)
        assertEquals("true", picked.params!!.single().value)
    }

    @Test
    fun draftSubagentsMigrateLegacyFields() {
        val draft = ChatDraft(subName = "reviewer", subDesc = "Review diffs", subPrompt = "Be strict")
        val items = draft.resolvedSubagents()
        assertEquals(1, items.size)
        assertEquals("reviewer", items.single().name)
        val api = items.toApi()!!
        assertEquals("reviewer", api.single().name)
        assertNull(api.single().model)
    }

    @Test
    fun draftSubagentsHonorExplicitModel() {
        val api = listOf(
            DraftSubagent("reviewer", "Review diffs", "Be strict", "inherit"),
        ).toApi()!!
        assertEquals("inherit", api.single().model)
    }

    @Test
    fun steerRequestKeepsPromptText() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val body = json.encodeToString(SteerRequest.serializer(), SteerRequest(Prompt("stay on the branch")))
        assertTrue(body.contains("stay on the branch"))
    }

    @Test
    fun conversationDecodesUserAndAssistant() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val convo = json.decodeFromString<AgentConversation>(
            """{"id":"bc-1","messages":[{"id":"m1","type":"user_message","text":"hi"},{"type":"assistant_message","text":"yo"}]}""",
        )
        assertEquals("user", convo.messages[0].transcriptKind())
        assertEquals("hi", convo.messages[0].text)
        assertEquals("assistant", convo.messages[1].transcriptKind())
    }

    @Test
    fun runDecodesObjectOrStringPrompt() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val objectPrompt = json.decodeFromString<Run>(
            """{"id":"r1","prompt":{"text":"from pc"}}""",
        )
        assertEquals("from pc", objectPrompt.prompt?.text)
        val stringPrompt = json.decodeFromString<Run>(
            """{"id":"r2","prompt":"plain"}""",
        )
        assertEquals("plain", stringPrompt.prompt?.text)
        val missing = json.decodeFromString<Run>("""{"id":"r3","result":"ok"}""")
        assertNull(missing.prompt)
    }

    @Test
    fun workerPoolLineShowsLoad() {
        val pool = WorkerPool(
            poolName = "gpu",
            connectedWorkerCount = 2,
            inUseWorkerCount = 1,
            repoOwner = "acme",
            repoName = "app",
        )
        assertEquals(1, pool.idle())
        assertTrue(pool.line().contains("gpu"))
        assertTrue(pool.line().contains("1/2 busy"))
    }
}
