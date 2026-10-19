package com.cursorandroid.app.data.api

import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.DraftSubagent
import com.cursorandroid.app.data.repo.toApi
import com.cursorandroid.app.ui.status.RunIndicator
import com.cursorandroid.app.ui.status.agentLifecycleLabel
import com.cursorandroid.app.ui.status.runIndicator
import com.cursorandroid.app.ui.status.runStatusLabel
import com.cursorandroid.app.ui.status.threadStatusLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun apiModelsKeepJsonFieldNames() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; explicitNulls = false }
        val me = json.encodeToString(
            MeResponse.serializer(),
            MeResponse(apiKeyName = "cursor-android", userEmail = "a@b.c"),
        )
        assertTrue(me.contains("\"apiKeyName\""))
        assertTrue(me.contains("\"userEmail\""))
        val worker = json.encodeToString(
            Worker.serializer(),
            Worker(workerId = "w1", repoUrl = "https://github.com/acme/app"),
        )
        assertTrue(worker.contains("\"workerId\""))
        assertTrue(worker.contains("\"repoUrl\""))
        val created = json.encodeToString(
            CreateAgentRequest.serializer(),
            CreateAgentRequest(prompt = Prompt("hi"), name = "chat"),
        )
        assertTrue(created.contains("\"prompt\""))
        assertTrue(created.contains("\"text\""))
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
    fun openAsCursorGithubAppAndRunScopedFieldsEncodeWhenSet() {
        val json = kotlinx.serialization.json.Json { explicitNulls = false }
        val body = CreateAgentRequest(
            prompt = Prompt("hi"),
            openAsCursorGithubApp = true,
            runEnvVars = mapOf("API_URL" to "https://example.com"),
            metadata = mapOf("source" to "phone"),
            envVars = mapOf("TOKEN" to "x"),
        )
        val text = json.encodeToString(CreateAgentRequest.serializer(), body)
        assertTrue(text.contains("\"openAsCursorGithubApp\":true"))
        assertTrue(text.contains("\"runEnvVars\""))
        assertTrue(text.contains("\"metadata\""))
        val omitted = json.encodeToString(CreateAgentRequest.serializer(), CreateAgentRequest(prompt = Prompt("hi")))
        assertFalse(omitted.contains("openAsCursorGithubApp"))
        assertFalse(omitted.contains("runEnvVars"))
        assertFalse(omitted.contains("metadata"))
        assertEquals(false, defaultOpenAsCursorGithubApp(MeResponse(userId = 7)))
        assertEquals(true, defaultOpenAsCursorGithubApp(MeResponse(userId = null, apiKeyName = "sa")))
        assertEquals(false, defaultOpenAsCursorGithubApp(null))
    }

    @Test
    fun envVarsRejectCursorPrefixAndAgentId() {
        assertEquals("Env var names cannot start with CURSOR_.", envVarsError(mapOf("CURSOR_API" to "x")))
        assertEquals("At most 50 env vars.", envVarsError((1..51).associate { "K$it" to "v" }))
        assertNull(envVarsError(mapOf("TOKEN" to "x")))
        val run = CreateRunRequest(
            prompt = Prompt("next"),
            model = ModelSelection("composer-2"),
            envVars = mapOf("TOKEN" to "x"),
        )
        val text = kotlinx.serialization.json.Json { explicitNulls = false }
            .encodeToString(CreateRunRequest.serializer(), run)
        assertTrue(text.contains("\"envVars\""))
        assertTrue(text.contains("\"model\""))
    }

    @Test
    fun promptImageSendsDimensionOrUrlWithoutInventingMime() {
        val json = kotlinx.serialization.json.Json { explicitNulls = false }
        val withSize = json.encodeToString(
            PromptImage.serializer(),
            PromptImage(data = "abc", mimeType = "image/png", dimension = ImageDimension(12, 8)),
        )
        assertTrue(withSize.contains("\"dimension\""))
        assertTrue(withSize.contains("\"width\":12"))
        val kept = sanitizePromptImages(
            listOf(
                PromptImage(url = "https://example.com/a.png"),
                PromptImage(data = "abc", mimeType = "image/bmp"),
                PromptImage(data = "abc", mimeType = "image/jpeg", dimension = ImageDimension(1, 1)),
            ),
        )
        assertEquals(2, kept!!.size)
        assertEquals("https://example.com/a.png", kept[0].url)
        assertNull(kept[0].mimeType)
        assertEquals("image/jpeg", kept[1].mimeType)
        assertEquals(1, kept[1].dimension?.width)
    }

    @Test
    fun usageCostIsShownOnlyWhenPresent() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val usage = json.decodeFromString<AgentUsageResponse>(
            """{"totalUsage":{"totalTokens":3},"cost":{"rawCostCents":12,"chargedCents":10},"runs":[{"id":"r1","cost":{"rawCostCents":12,"chargedCents":10}}]}""",
        )
        assertEquals("$0.10 charged · $0.12 raw", usage.costLine("r1"))
        val missing = json.decodeFromString<AgentUsageResponse>("""{"totalUsage":{"totalTokens":3},"runs":[{"id":"r1"}]}""")
        assertNull(missing.cost)
        assertNull(missing.costLine())
        val run = json.decodeFromString<Run>("""{"id":"r1","status":"ERROR","error":{"message":"boom"}}""")
        assertEquals("boom", run.error)
        assertEquals("ERROR", run.status)
    }

    @Test
    fun agentIdleIsNotARunFailure() {
        assertEquals(RunIndicator.Done, runIndicator("IDLE", "FINISHED"))
        assertEquals(RunIndicator.Failed, runIndicator("IDLE", "ERROR"))
        assertEquals(RunIndicator.Done, runIndicator("ACTIVE", "FINISHED"))
        assertEquals(RunIndicator.Running, runIndicator("ACTIVE", "RUNNING"))
        assertEquals(RunIndicator.Running, runIndicator("ACTIVE"))
        assertEquals("Idle", agentLifecycleLabel("IDLE"))
        assertEquals("Error", runStatusLabel("ERROR"))
        assertEquals("Idle · Error", threadStatusLine("IDLE", "ERROR", RunIndicator.Failed))
    }

    @Test
    fun startingRefShaRulesAndRepoUrlScheme() {
        assertEquals("main", resolveStartingRef("main", "abc1234def", true, null, "main"))
        assertEquals("main", resolveStartingRef("main", "abc1234def", true, null, "develop"))
        assertEquals("abc1234def", resolveStartingRef("main", "abc1234def", false, null, "main"))
        assertEquals("feature", resolveStartingRef("feature", "abc1234def", false, null, "main"))
        assertNull(resolveStartingRef("main", "abc1234def", false, "https://github.com/acme/app/pull/1", "main"))
        assertEquals(
            "A commit SHA cannot be used when committing on the current branch.",
            startingRefError("abc1234def", true, null),
        )
        assertNull(startingRefError("abc1234def", true, "https://github.com/acme/app/pull/1"))
        assertEquals("https://github.com/acme/app", normalizeRepoUrl("github.com/acme/app"))
        assertEquals("https://github.com/acme/app", normalizeRepoUrl("https://github.com/acme/app"))
    }

    @Test
    fun errorEnvelopeKeepsHelpProviderAndEnvironment() {
        val parsed = ApiException.fromBody(
            409,
            """{"error":{"code":"integration_not_connected","message":"GitHub is not connected","helpUrl":"https://cursor.com/help","provider":"github"}}""",
        )
        assertEquals("github", parsed.provider)
        assertEquals("https://cursor.com/help", parsed.helpUrl)
        assertTrue(parsed.displayMessage().contains("github"))
        assertTrue(parsed.displayMessage().contains("https://cursor.com/help"))
        val named = ApiException.fromBody(
            409,
            """{"error":{"code":"environment_name_conflict","message":"name taken","environmentId":"env-1"}}""",
        )
        assertEquals("env-1", named.environmentId)
        assertTrue(named.displayMessage().contains("env-1"))
        val archived = ApiException(409, "agent_archived", "agent_archived")
        assertTrue(archived.isArchivedAgent)
        assertTrue(archived.displayMessage().contains("Unarchive"))
        val cursor = ApiException(400, "invalid_last_event_id", "invalid_last_event_id")
        assertTrue(cursor.isInvalidLastEvent)
        assertTrue(queueInsteadOfFail(ApiException(409, "busy", "agent_busy")))
        assertEquals("bc-1", conflictAgentId("bc-1", null))
        assertEquals("bc-abcdef12", conflictAgentId(null, """{"error":{"message":"bc-abcdef12 exists"}}"""))
        assertEquals("prev", retainEventId("prev", null))
        assertEquals("prev", retainEventId("prev", ""))
        assertEquals("1713033000000-0", retainEventId("prev", "1713033000000-0"))
        assertEquals(900L, parseRetentionSeconds("900"))
        assertNull(parseRetentionSeconds(null))
        val secrets = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<SecretListResponse>("""{"items":[],"nextCursor":null}""")
        assertNull(secrets.nextCursor)
    }

    @Test
    fun subagentModelAcceptsStringInheritOrObject() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
        val inherit = json.decodeFromString<CustomSubagent>(
            """{"name":"reviewer","description":"d","prompt":"p","model":"inherit"}""",
        )
        assertEquals("inherit", inherit.model)
        val objectModel = json.decodeFromString<CustomSubagent>(
            """{"name":"reviewer","description":"d","prompt":"p","model":{"id":"composer-2","params":[{"id":"fast","value":"true"}]}}""",
        )
        assertTrue(objectModel.model!!.contains("composer-2"))
        val wire = json.encodeToString(CustomSubagent.serializer(), objectModel)
        assertTrue(wire.contains("\"model\":{"))
        assertTrue(json.encodeToString(CustomSubagent.serializer(), inherit).contains("\"inherit\""))
        assertTrue(CloudFieldGate.rejects(ApiException(400, "unknown field runEnvVars", body = "runEnvVars"), "runEnvVars"))
        assertFalse(CloudFieldGate.rejects(ApiException(400, "name is required"), "runEnvVars"))
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
