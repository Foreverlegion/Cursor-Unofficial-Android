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
    fun cloudCreateTargetSendsSeveralReposUntilANamedEnv() {
        val (env, repos) = cloudCreateTarget(
            fromSavedEnv = false,
            envName = "",
            repoUrl = "https://github.com/acme/app",
            startingRef = "main",
            extraRepoUrls = listOf(
                "https://github.com/acme/api",
                "https://github.com/acme/app",
            ),
        )
        assertNull(env)
        assertEquals(2, repos!!.size)
        assertEquals("https://github.com/acme/app", repos[0].url)
        assertEquals("main", repos[0].startingRef)
        assertEquals("https://github.com/acme/api", repos[1].url)
        assertNull(repos[1].startingRef)
        val named = cloudCreateTarget(true, "web", "https://github.com/acme/app", "main", extraRepoUrls = listOf("https://github.com/acme/api"))
        assertEquals("web", named.first?.name)
        assertNull(named.second)
    }

    @Test
    fun anyRepoPoolTakesManyAndDefaultTakesOne() {
        val any = WorkerPool(poolName = "sandbox")
        val bound = WorkerPool(poolName = "gpu", repoUrl = "https://github.com/acme/app")
        val fallback = WorkerPool(poolName = "default")
        assertTrue(any.acceptsManyRepos())
        assertTrue(!bound.acceptsManyRepos())
        assertTrue(!fallback.acceptsManyRepos())
        assertEquals(2, poolAgentRepos(any, listOf("https://github.com/acme/app", "https://github.com/acme/api"))!!.size)
        assertEquals(1, poolAgentRepos(fallback, listOf("https://github.com/acme/app", "https://github.com/acme/api"))!!.size)
        assertEquals("""{"install":"pnpm install"}""", environmentConfigJson("pnpm install"))
        assertEquals("""{"install":"true"}""", environmentConfigJson(" "))
        assertEquals("team", environmentOwner("Team"))
        assertEquals("personal", environmentOwner("nope"))
        assertEquals("""{"install":"true"}""", resolvedEnvironmentJson(" "))
        assertEquals("""{"install":"pnpm install"}""", resolvedEnvironmentJson("""{"install":"pnpm install"}"""))
    }

    @Test
    fun environmentChoicesPreferSavedIdsOverScrapedNames() {
        val saved = CloudEnvironment(
            id = "8f14e45f-ceea-4e6b-9c3a-1d2e3f4a5b6c",
            name = "Web",
            owner = "team",
            repos = listOf(EnvRepo("https://github.com/acme/app")),
        )
        val choices = environmentChoices(listOf(saved), listOf("Web", "api"))
        assertEquals(2, choices.size)
        assertEquals("Web", choices[0].name)
        assertEquals(saved.id, choices[0].id)
        assertTrue(choices[0].fromApi)
        assertEquals("Web · team · 1 repo", choices[0].menuLabel())
        assertNull(choices[1].id)
        assertEquals("api · past chat", choices[1].menuLabel())
    }

    @Test
    fun activeBuildSummaryAndBuildJson() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val listed = json.decodeFromString<EnvironmentBuildList>(
            """{"items":[{"id":"bld-20260930-3c59dc04-8a1d-4b6e-9f2a-7e5d1c0b9a8f","environmentId":"8f14e45f-ceea-4e6b-9c3a-1d2e3f4a5b6c","status":"FAILED","trigger":"MANUAL","draft":true,"failure":{"type":"INSTALL_FAILED","code":"environment_json_invalid"}}],"nextCursor":"abc"}""",
        )
        val active = json.decodeFromString<EnvironmentActiveBuild>(
            """{"type":"build","buildId":"bld-20260930-3c59dc04-8a1d-4b6e-9f2a-7e5d1c0b9a8f"}""",
        )
        val image = json.decodeFromString<EnvironmentActiveBuild>("""{"type":"universal_image"}""")
        val env = json.decodeFromString<CloudEnvironment>(
            """{"id":"8f14e45f-ceea-4e6b-9c3a-1d2e3f4a5b6c","name":"Web","owner":"personal","repos":[],"repoFile":{"url":"https://github.com/acme/app","path":".cursor/environment.json"},"environmentJson":"{\"install\":\"true\"}","versionId":"c9f0f895-fb98-4b91-8f3e-2a1b0c9d8e7f"}""",
        )
        assertEquals("INSTALL_FAILED", listed.items.single().failure?.type)
        assertEquals("abc", listed.nextCursor)
        assertEquals(".cursor/environment.json", env.repoFile?.path)
        assertEquals("c9f0f895-fb98-4b91-8f3e-2a1b0c9d8e7f", env.versionId)
        assertEquals(
            "Boots from bld-20260930-3c59dc04-8a1d-4b6e-9f2a-7e5d1c0b9a8f · Latest bld-20260930-3c59dc04-8a1d-4b6e-9f2a-7e5d1c0b9a8f FAILED draft (environment_json_invalid)",
            activeBuildSummary(active, listed.items.single()),
        )
        assertEquals("Boots from the default image", activeBuildSummary(image, null))
        assertNull(image.buildId)
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
    fun listedProvidersComeFromTheCatalogNotGithubOnly() {
        val repos = listOf(
            RepositoryItem(url = "https://gitlab.com/acme/app", provider = "gitlab"),
            RepositoryItem(url = "https://origin.cursor.com/acme/app.git", provider = "origin"),
            RepositoryItem(url = "https://github.com/acme/app", provider = "github"),
        )
        assertEquals(listOf("GitHub", "GitLab", "Origin"), listedProviders(repos))
        assertEquals(repos[1], matchRepo(repos, "https://origin.cursor.com/acme/app"))
        assertEquals("Origin", prettyProvider("origin.cursor.com"))
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
