package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentConversation
import com.cursorandroid.app.data.api.AgentDetail
import com.cursorandroid.app.data.api.AgentListResponse
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.McpServer
import com.cursorandroid.app.data.api.TokenUsage
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.CloudFieldGate
import com.cursorandroid.app.data.api.CloudEnvironment
import com.cursorandroid.app.data.api.conflictAgentId
import com.cursorandroid.app.data.api.envVarsError
import com.cursorandroid.app.data.api.normalizeRepoUrl
import com.cursorandroid.app.data.api.sanitizePromptImages
import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.CreateEnvironmentRequest
import com.cursorandroid.app.data.api.EnvRepo
import com.cursorandroid.app.data.api.EnvironmentActiveBuild
import com.cursorandroid.app.data.api.EnvironmentBuild
import com.cursorandroid.app.data.api.EnvironmentBuildList
import com.cursorandroid.app.data.api.EnvironmentHistoryEvent
import com.cursorandroid.app.data.api.PutSecretRequest
import com.cursorandroid.app.data.api.SecretVersion
import com.cursorandroid.app.data.api.UpdateEnvironmentRequest
import com.cursorandroid.app.data.api.secretVersionsFromBody
import com.cursorandroid.app.data.api.MAX_ENV_REPOS
import com.cursorandroid.app.data.api.environmentOwner
import com.cursorandroid.app.data.api.Worker
import com.cursorandroid.app.data.api.WorkerPool
import com.cursorandroid.app.data.api.WorkersSummaryResponse
import com.cursorandroid.app.data.api.CreateAgentResponse
import com.cursorandroid.app.data.api.CreateRunRequest
import com.cursorandroid.app.data.api.SteerRequest
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.MeResponse
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelSelection
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.SseStreamer
import com.cursorandroid.app.data.api.StreamEvent
import com.cursorandroid.app.data.api.foldAgentPages
import com.cursorandroid.app.data.api.gitHost
import com.cursorandroid.app.data.api.gitPath
import com.cursorandroid.app.data.api.isRemoteEnvType
import com.cursorandroid.app.data.api.markCloudArchived
import com.cursorandroid.app.data.api.sortKey
import com.cursorandroid.app.data.api.withDetail
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.auth.SessionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.net.URLEncoder

class AgentRepository(
    private val api: CursorApi,
    private val sse: SseStreamer,
    private val store: ApiKeyStore,
    private val catalog: CatalogCache,
    private val publicHttp: OkHttpClient,
    private val json: Json,
    private val machines: MachineStore? = null,
    val statusGate: StatusGate = StatusGate(),
    val session: SessionGuard = SessionGuard { store.apiKey },
) {
    private val statusCache = StatusCache()

    fun apiKey(): String = store.apiKey.orEmpty()

    suspend fun me(): MeResponse = wrap { api.me() }

    suspend fun createCloudEnvironment(
        name: String,
        repoUrls: List<String>,
        environmentJson: String,
        owner: String = "personal",
    ): CloudEnvironment {
        val repos = repoUrls
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_ENV_REPOS)
            .map { EnvRepo(it) }
        val created = wrap {
            api.createEnvironment(
                CreateEnvironmentRequest(
                    owner = environmentOwner(owner),
                    name = name.trim(),
                    repos = repos,
                    environmentJson = environmentJson.trim(),
                ),
            )
        }
        catalog.rememberEnvironment(created)
        return created
    }

    suspend fun getCloudEnvironment(id: String): CloudEnvironment {
        val env = wrap { api.getEnvironment(id) }
        catalog.rememberEnvironment(env)
        return env
    }

    suspend fun deleteCloudEnvironment(id: String) {
        wrap { api.deleteEnvironment(id) }
        catalog.forgetEnvironment(id)
    }

    suspend fun listCloudEnvironments(): List<CloudEnvironment> {
        val out = LinkedHashMap<String, CloudEnvironment>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = wrap { api.listEnvironments(limit = 100, cursor = cursor) }
            page.items.forEach { env ->
                val key = env.id.ifBlank { env.name.lowercase() }
                if (key.isNotBlank()) out[key] = env
                catalog.rememberEnvironment(env)
            }
            val next = page.nextCursor?.takeIf { it.isNotBlank() && it != cursor }
            if (next == null || page.items.isEmpty()) return out.values.toList()
            cursor = next
        }
        return out.values.toList()
    }

    suspend fun updateCloudEnvironment(id: String, name: String?, environmentJson: String?): CloudEnvironment {
        val body = UpdateEnvironmentRequest(
            name = name?.trim()?.takeIf { it.isNotEmpty() },
            environmentJson = environmentJson?.trim()?.takeIf { it.isNotEmpty() },
        )
        if (body.name == null && body.environmentJson == null) {
            throw IllegalArgumentException("Name or environment.json is required")
        }
        wrap { api.updateEnvironment(id, body) }
        return getCloudEnvironment(id)
    }

    suspend fun environmentHistory(id: String): List<EnvironmentHistoryEvent> {
        val out = ArrayList<EnvironmentHistoryEvent>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = wrap { api.environmentHistory(id, limit = 50, cursor = cursor) }
            out += page.items
            val next = page.nextCursor?.takeIf { it.isNotBlank() && it != cursor }
            if (next == null || page.items.isEmpty()) return out
            cursor = next
        }
        return out
    }

    suspend fun listAccountSecrets(
        scope: String? = null,
        environmentId: String? = null,
        name: String? = null,
    ): List<SecretVersion> {
        val out = ArrayList<SecretVersion>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = wrap {
                api.listSecrets(
                    scope = scope?.takeIf { it.isNotBlank() },
                    environmentId = environmentId?.takeIf { it.isNotBlank() },
                    name = name?.takeIf { it.isNotBlank() },
                    limit = 100,
                    cursor = cursor,
                )
            }
            out += page.items
            val next = page.nextCursor?.takeIf { it.isNotBlank() && it != cursor }
            if (next == null) return out
            cursor = next
        }
        return out
    }

    suspend fun listEnvironmentSecrets(id: String): List<SecretVersion> {
        return wrap { api.listEnvironmentSecrets(id).items }
    }

    suspend fun listTeamSecrets(): List<SecretVersion> {
        return wrap { api.listTeamSecrets().items }
    }

    suspend fun putEnvironmentSecret(
        id: String,
        name: String,
        value: String,
        type: String?,
        repos: List<String>?,
        versionId: String? = null,
    ): SecretVersion {
        return wrapSecret {
            api.putEnvironmentSecret(
                id,
                name.trim(),
                versionId?.takeIf { it.isNotBlank() },
                PutSecretRequest(value = value, type = type, repos = repos),
            )
        }
    }

    suspend fun deleteEnvironmentSecret(id: String, name: String, versionId: String? = null) {
        wrapSecret {
            api.deleteEnvironmentSecret(id, name, versionId?.takeIf { it.isNotBlank() })
        }
    }

    suspend fun putTeamSecret(
        name: String,
        value: String,
        type: String?,
        repos: List<String>?,
        versionId: String? = null,
    ): SecretVersion {
        return wrapSecret {
            api.putTeamSecret(
                name.trim(),
                versionId?.takeIf { it.isNotBlank() },
                PutSecretRequest(value = value, type = type, repos = repos),
            )
        }
    }

    suspend fun deleteTeamSecret(name: String, versionId: String? = null) {
        wrapSecret { api.deleteTeamSecret(name, versionId?.takeIf { it.isNotBlank() }) }
    }

    suspend fun listEnvironmentBuilds(id: String, cursor: String? = null): EnvironmentBuildList {
        return wrap { api.listEnvironmentBuilds(id, cursor) }
    }

    suspend fun getEnvironmentBuild(id: String, buildId: String): EnvironmentBuild {
        return wrap { api.getEnvironmentBuild(id, buildId) }
    }

    suspend fun activeEnvironmentBuild(id: String): EnvironmentActiveBuild {
        return wrap { api.activeEnvironmentBuild(id) }
    }

    suspend fun listAgents(includeArchived: Boolean = true, cursor: String? = null): List<AgentSummary> {
        return if (cursor == null) {
            listAllAgents(includeArchived).items
        } else {
            listAgentsPage(includeArchived, cursor).entries()
        }
    }

    suspend fun listAgentsPage(
        includeArchived: Boolean = true,
        cursor: String? = null,
        prUrl: String? = null,
    ): AgentListResponse {
        return wrap {
            api.listAgents(
                limit = PAGE_SIZE,
                includeArchived = includeArchived,
                cursor = cursor,
                prUrl = prUrl?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    suspend fun listAllAgents(includeArchived: Boolean = true, prUrl: String? = null): AgentListResponse {
        val pages = ArrayList<AgentListResponse>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = listAgentsPage(includeArchived, cursor, prUrl)
            pages += page
            val next = page.nextCursor?.takeIf { it.isNotBlank() && it != cursor }
            if (next == null || page.entries().isEmpty()) {
                return foldAgentPages(pages, MAX_PAGES)
            }
            cursor = next
        }
        return foldAgentPages(pages, MAX_PAGES)
    }

    suspend fun listInboxAgents(): AgentListResponse {
        val all = listAllAgents(includeArchived = true)
        val active = listAllAgents(includeArchived = false)
        val marked = markCloudArchived(all.entries(), active.entries())
            .sortedByDescending { it.sortKey() }
        return AgentListResponse(items = marked, nextCursor = all.nextCursor)
    }

    suspend fun walkInboxPages(onPage: suspend (AgentListResponse) -> Unit, prUrl: String? = null) {
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = listAgentsPage(includeArchived = true, cursor = cursor, prUrl = prUrl)
            onPage(page)
            val next = page.nextCursor?.takeIf { it.isNotBlank() && it != cursor }
            if (next == null || page.entries().isEmpty()) return
            cursor = next
        }
    }

    /** First page only, or every page when [walkPages] is true (resume / manual refresh). */
    suspend fun listAgentsForInboxPoll(
        walkPages: Boolean,
        prUrl: String? = null,
    ): AgentListResponse {
        val first = listAgentsPage(includeArchived = true, prUrl = prUrl)
        if (!walkPages || first.nextCursor.isNullOrBlank()) return first
        val pages = ArrayList<AgentListResponse>(MAX_PAGES)
        pages += first
        var cursor = first.nextCursor
        for (i in 1 until MAX_PAGES) {
            if (cursor.isNullOrBlank()) break
            val page = listAgentsPage(includeArchived = true, cursor = cursor, prUrl = prUrl)
            pages += page
            if (page.entries().isEmpty()) break
            cursor = page.nextCursor?.takeIf { it.isNotBlank() && it != cursor }
        }
        return foldAgentPages(pages, MAX_PAGES)
    }

    suspend fun hydrateStatuses(
        agents: List<AgentSummary>,
        limit: Int = 15,
        only: Set<String>? = null,
    ): List<AgentSummary> {
        if (statusGate.isPaused) return agents
        val targets = hydrateTargets(agents, limit, only)
        if (targets.isEmpty()) return agents
        val foundRepos = HashMap<String, String>()
        val fresh = coroutineScope {
            targets.map { agent ->
                async {
                    val detail = runCatching { getAgent(agent.id) }.getOrNull() ?: return@async agent
                    detail.repos?.firstOrNull()?.url?.takeIf { it.isNotBlank() }?.let { url ->
                        synchronized(foundRepos) { foundRepos[agent.id] = url }
                    }
                    agent.withDetail(detail)
                }
            }.awaitAll()
        }
        catalog.saveAgentRepos(foundRepos)
        val byId = fresh.associateBy { it.id }
        return agents.map { byId[it.id] ?: it }
    }

    suspend fun resolveAgentRepos(agents: List<AgentSummary>, budget: Int = 15) {
        if (statusGate.isPaused) return
        val known = catalog.agentRepos()
        val snaps = catalog.gitSnaps()
        val missing = agents
            .filter { agent ->
                agent.id !in known && snaps[agent.id]?.repoUrl.isNullOrBlank()
            }
            .sortedByDescending { it.sortKey() }
            .take(budget)
        if (missing.isEmpty()) return
        val gate = Semaphore(2)
        val found = coroutineScope {
            missing.map { agent ->
                async {
                    gate.withPermit {
                        val detail = runCatching { getAgent(agent.id) }.getOrNull() ?: return@withPermit null
                        agent.id to detail.repos?.firstOrNull()?.url.orEmpty()
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        catalog.saveAgentRepos(found)
    }

    suspend fun getAgent(id: String): AgentDetail =
        statusCache.get("agent:$id", AGENT_TTL_MS) { statusGate.call("agent") { wrap { api.getAgent(id) } } }

    suspend fun getRun(agentId: String, runId: String): Run =
        statusCache.get("run:$agentId:$runId", RUN_TTL_MS) { statusGate.call("run") { wrap { api.getRun(agentId, runId) } } }

    /** Forget cached status for an agent after something we did changes it (a send, a stop). */
    fun statusChanged(agentId: String) {
        statusCache.invalidate("agent:$agentId")
        statusCache.invalidate("run:$agentId:")
    }

    suspend fun conversation(agentId: String): AgentConversation = wrap { api.getConversation(agentId) }

    suspend fun listRuns(agentId: String, limit: Int = 100): List<Run> {
        return wrap {
            val out = ArrayList<Run>()
            var cursor: String? = null
            repeat(5) {
                val page = api.listRuns(agentId, limit, cursor)
                out += page.items
                val next = page.nextCursor?.takeIf { it.isNotBlank() }
                if (next == null || page.items.isEmpty()) return@wrap out
                cursor = next
            }
            out
        }
    }

    suspend fun archive(agentId: String) = wrap { api.archiveAgent(agentId) }

    suspend fun unarchive(agentId: String) = wrap { api.unarchiveAgent(agentId) }

    suspend fun deleteAgent(agentId: String) = wrap { api.deleteAgent(agentId) }

    suspend fun artifacts(agentId: String) = wrap { api.listArtifacts(agentId).items }

    suspend fun artifactUrl(agentId: String, path: String) = wrap { api.downloadArtifact(agentId, path).url }

    suspend fun usage(agentId: String) = runCatching { wrap { api.agentUsage(agentId) } }.getOrNull()

    suspend fun recentUsage(
        days: Int = USAGE_WINDOW_DAYS,
        limit: Int = USAGE_CHAT_CAP,
    ): UsageSample {
        return withinBudget(USAGE_BUDGET_MS) {
            coroutineScope {
                val listed = runCatching { listAgentsPage(includeArchived = true).entries() }
                val agents = try {
                    pickFreshList(listed, catalog.agents())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw IllegalStateException("Couldn't load recent chats", e)
                }
                val window = usageWindowAgents(agents, System.currentTimeMillis(), days, limit)
                if (window.isEmpty()) {
                    return@coroutineScope UsageSample(TokenUsage(), 0, emptyList(), days)
                }
                val rows = window.map { agent ->
                    async {
                        val used = withTimeoutOrNull(USAGE_CALL_MS) {
                            usage(agent.id)?.totalUsage
                        } ?: return@async null
                        agent to used
                    }
                }.awaitAll().filterNotNull()
                if (rows.isEmpty()) throw IllegalStateException("Couldn't load token usage")
                combineTokenUsage(rows).copy(windowDays = days).also { catalog.saveUsage(it) }
            }
        }
    }

    suspend fun refreshMe(): MeResponse = withinBudget(ROW_BUDGET_MS) { me() }.also { catalog.saveMe(it) }

    suspend fun refreshAgents(): List<AgentSummary> {
        val listed = runCatching { listAgents() }
        val agents = pickFreshList(listed, catalog.agents())
        listed.onSuccess { catalog.saveAgents(it) }
        return agents
    }

    suspend fun refreshComputers(agents: List<AgentSummary>): List<Computer> {
        val listing = listMachines(agents)
        if (!listing.known || listing.computers.isEmpty()) return catalog.computers()
        catalog.saveComputers(listing.computers)
        return listing.computers
    }

    suspend fun refreshPools(): List<WorkerPool> {
        val listed = runCatching { listPools() }.getOrNull() ?: return catalog.pools()
        return listed.ifEmpty { catalog.pools() }
    }

    suspend fun refreshRepos(): List<RepositoryItem> {
        return runCatching { repositories() }.getOrElse { catalog.repos() }
    }

    suspend fun <T> withinBudget(millis: Long, block: suspend () -> T): T {
        return try {
            withTimeout(millis) { block() }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Timed out", e)
        }
    }

    suspend fun refreshGitSnaps(agents: List<AgentSummary>) {
        if (statusGate.isPaused) return
        val targets = gitSnapTargets(agents, catalog.gitSnaps().keys)
        if (targets.isEmpty()) return
        coroutineScope {
            targets.map { agent ->
                async {
                    val runId = agent.latestRunId ?: return@async
                    val run = runCatching { getRun(agent.id, runId) }.getOrNull() ?: return@async
                    val git = run.git?.branches?.firstOrNull() ?: return@async
                    catalog.saveGit(
                        com.cursorandroid.app.data.api.GitSnap(
                            agentId = agent.id,
                            branch = git.branch,
                            prUrl = git.prUrl,
                            repoUrl = normalizeRepoUrl(git.repoUrl),
                        ),
                    )
                    normalizeRepoUrl(git.repoUrl)?.let { url ->
                        val full = url
                        val existing = catalog.repos()
                        if (existing.none { it.url.contains(url, ignoreCase = true) }) {
                            catalog.saveRepos(
                                (existing + RepositoryItem(url = full)).distinctBy {
                                    it.url.trim().lowercase().removeSuffix(".git")
                                },
                            )
                        }
                    }
                }
            }.forEach { it.await() }
        }
    }

    suspend fun refreshPrChecks(
        agents: List<AgentSummary>,
        git: Map<String, com.cursorandroid.app.data.api.GitSnap>,
        limit: Int = InboxPollPolicy.PR_CHECKS_LIMIT,
    ) {
        if (statusGate.isPaused || store.demoMode) return
        val cached = catalog.prCheckSnaps()
        val targets = agents.mapNotNull { agent ->
            val prUrl = git[agent.id]?.prUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (GithubPrChecks.parsePullUrl(prUrl) == null) return@mapNotNull null
            agent.id to prUrl
        }.take(limit)
        if (targets.isEmpty()) return
        coroutineScope {
            targets.map { (agentId, prUrl) ->
                async {
                    val repoUrl = git[agentId]?.repoUrl
                    val forge = repoUrl?.let { store.forgeForRepo(it) }
                        ?: store.forges().firstOrNull { it.provider == ForgeKind.GITHUB.id && it.token.isNotBlank() }
                    val token = forge?.token?.trim().orEmpty().ifBlank { store.githubToken.orEmpty().trim() }
                    if (token.isEmpty()) return@async
                    val apiBase = forge?.apiUrl?.trim()?.trimEnd('/').takeUnless { it.isNullOrEmpty() }
                        ?: ForgeKind.GITHUB.apiUrl
                    val snap = runCatching { GithubPrChecks.fetchSnap(token, apiBase, prUrl) }.getOrNull()
                        ?: return@async
                    val prior = cached[agentId]
                    if (prior?.headSha == snap.headSha && prior.kind == snap.kind && prior.label == snap.label) return@async
                    catalog.savePrCheck(agentId, snap.takeIf { it.showChip })
                }
            }.forEach { it.await() }
        }
    }

    suspend fun refreshPrCheck(agentId: String, prUrl: String, repoUrl: String?) {
        if (statusGate.isPaused || store.demoMode || agentId.isBlank()) return
        val trimmed = prUrl.trim()
        if (trimmed.isEmpty() || GithubPrChecks.parsePullUrl(trimmed) == null) return
        val forge = repoUrl?.let { store.forgeForRepo(it) }
            ?: store.forges().firstOrNull { it.provider == ForgeKind.GITHUB.id && it.token.isNotBlank() }
        val token = forge?.token?.trim().orEmpty().ifBlank { store.githubToken.orEmpty().trim() }
        if (token.isEmpty()) return
        val apiBase = forge?.apiUrl?.trim()?.trimEnd('/').takeUnless { it.isNullOrEmpty() }
            ?: ForgeKind.GITHUB.apiUrl
        val snap = runCatching { GithubPrChecks.fetchSnap(token, apiBase, trimmed) }.getOrNull() ?: return
        catalog.savePrCheck(agentId, snap.takeIf { it.showChip })
    }

    suspend fun models(): List<ModelItem> {
        val now = System.currentTimeMillis()
        modelCache?.let { (at, items) -> if (now - at < MODELS_TTL_MS && items.isNotEmpty()) return items }
        val items = wrap { api.models().items }
        if (items.isNotEmpty()) modelCache = now to items
        return items
    }

    private val repoGate = Mutex()

    @Volatile
    private var reposAttemptAt = 0L

    // GET /v1/repositories: 1 / user / minute, 30 / user / hour. GitHub only.
    suspend fun repositories(force: Boolean = false): List<RepositoryItem> {
        if (!force && catalog.reposFresh()) return catalog.repos()
        return repoGate.withLock {
            if (!force && catalog.reposFresh()) return@withLock catalog.repos()
            if (!RepoRateGate.mayCall(reposAttemptAt, System.currentTimeMillis())) return@withLock catalog.repos()
            reposAttemptAt = System.currentTimeMillis()
            val found = runCatching { wrap { api.repositories().items } }.getOrDefault(emptyList())
            val merged = mergeListedRepos(found, catalog.repos())
            if (found.isNotEmpty()) catalog.saveRepos(merged)
            merged
        }
    }

    suspend fun createGithubRepo(
        name: String,
        privateRepo: Boolean,
        description: String?,
    ): RepositoryItem {
        if (store.demoMode) {
            val slug = name.trim().ifBlank { "demo-repo" }
            val created = RepositoryItem(
                url = "https://github.com/example/$slug",
                provider = "github",
                defaultBranch = "main",
                name = slug,
            )
            val next = (listOf(created) + catalog.repos())
                .distinctBy { it.url.trim().lowercase().removeSuffix(".git") }
                .sortedBy { it.displayName().lowercase() }
            catalog.saveRepos(next)
            catalog.saveBranches(created.url, listOf("main"))
            return created
        }
        val created = withContext(Dispatchers.IO) {
            GithubRepos.create(store.githubToken.orEmpty(), name, privateRepo, description)
        }
        val next = (listOf(created) + catalog.repos())
            .distinctBy { it.url.trim().lowercase().removeSuffix(".git") }
            .sortedBy { it.displayName().lowercase() }
        catalog.saveRepos(next)
        val branch = created.defaultBranch?.takeIf { it.isNotBlank() } ?: "main"
        catalog.saveBranches(created.url, listOf(branch))
        return created
    }

    suspend fun createOnForge(
        providerLabel: String,
        name: String,
        privateRepo: Boolean,
        description: String?,
    ): RepositoryItem {
        val forge = store.forgeForLabel(providerLabel)
            ?: error("Add a forge for $providerLabel in Settings > Connections.")
        if (forge.provider == com.cursorandroid.app.data.repo.ForgeKind.GITHUB.id) {
            return createGithubRepo(name, privateRepo, description)
        }
        val created = ForgeClient.createRepo(forge, name, privateRepo, description)
        val next = (listOf(created) + catalog.repos())
            .distinctBy { it.url.trim().lowercase().removeSuffix(".git") }
            .sortedBy { it.displayName().lowercase() }
        catalog.saveRepos(next)
        val branch = created.defaultBranch?.takeIf { it.isNotBlank() } ?: "main"
        catalog.saveBranches(created.url, listOf(branch))
        return created
    }

    suspend fun branches(repoUrl: String, defaultBranch: String? = null): List<String> {
        if (catalog.branchesFresh(repoUrl)) {
            return catalog.branches(repoUrl)
        }
        val fromApi = runCatching { wrap { api.repositoryBranches(repoUrl).names() } }.getOrDefault(emptyList())
        val fromForge = store.forgeForRepo(repoUrl)?.let { forge ->
            runCatching { ForgeClient.listBranches(forge, repoUrl) }.getOrDefault(emptyList())
        }.orEmpty()
        val fromHost = if (fromApi.isEmpty() && fromForge.isEmpty()) publicBranches(repoUrl) else emptyList()
        val names = (fromForge + fromApi + fromHost + listOfNotNull(defaultBranch) + DEFAULT_BRANCHES)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (names.isNotEmpty()) catalog.saveBranches(repoUrl, names)
        return names
    }

    suspend fun createAgent(
        body: CreateAgentRequest,
        idempotencyKey: String? = null,
    ): CreateAgentResponse {
        val prepared = assembleCreate(body)
        val key = idempotencyKey?.trim()?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()
        return try {
            postCreate(key, prepared)
        } catch (e: ApiException) {
            if (!e.isAgentIdConflict) throw e
            recoverCreate(prepared, e)
        }
    }

    /** Prompt only. repos, env, and envVars are omitted, and the prompt is not stamped. */
    suspend fun createProfileAgent(): CreateAgentResponse {
        val key = java.util.UUID.randomUUID().toString()
        return wrap { api.createAgent(key, ProfileRule.request()) }
    }

    /**
     * Repo-backed agent on an Origin clone URL. Does not create the repo:
     * the saved API key is not an Origin user access token.
     */
    suspend fun createUserRuleAgent(cloneUrl: String): CreateAgentResponse {
        val key = java.util.UUID.randomUUID().toString()
        return wrap { api.createAgent(key, UserRuleSync.agentRequest(cloneUrl)) }
    }

    private suspend fun postCreate(key: String, body: CreateAgentRequest): CreateAgentResponse {
        val send = if (CloudFieldGate.createRunScoped) {
            body
        } else {
            body.copy(runEnvVars = null, metadata = null)
        }
        return try {
            wrap { api.createAgent(key, send) }
        } catch (e: ApiException) {
            val dropRun = CloudFieldGate.rejects(e, "runEnvVars")
            val dropMeta = CloudFieldGate.rejects(e, "metadata")
            if (!dropRun && !dropMeta) throw e
            if (dropRun) CloudFieldGate.createRunScoped = false
            if (dropMeta) CloudFieldGate.createRunScoped = false
            wrap {
                api.createAgent(
                    key,
                    send.copy(
                        runEnvVars = if (dropRun) null else send.runEnvVars,
                        metadata = if (dropMeta) null else send.metadata,
                    ),
                )
            }
        }
    }

    private suspend fun recoverCreate(body: CreateAgentRequest, error: ApiException): CreateAgentResponse {
        val id = conflictAgentId(body.agentId, error.body) ?: throw error
        val agent = wrap { api.getAgent(id) }
        val runId = agent.latestRunId ?: throw error
        val run = wrap { api.getRun(agent.id, runId) }
        return CreateAgentResponse(agent, run)
    }

    suspend fun followUp(
        agentId: String,
        prompt: Prompt,
        mode: String? = null,
        model: ModelSelection? = null,
        mcpServers: List<McpServer>? = null,
        envVars: Map<String, String>? = null,
        idempotencyKey: String? = null,
    ): Run {
        val vars = envVars?.takeIf { it.isNotEmpty() }
        envVarsError(vars)?.let { throw IllegalArgumentException(it) }
        val stamped = ClientOrigin.stamp(prompt).let { it.copy(images = sanitizePromptImages(it.images)) }
        val key = idempotencyKey?.trim()?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()
        val created = postRun(agentId, key, stamped, mode, model, mcpServers, vars)
        statusChanged(agentId)
        return created
    }

    private suspend fun postRun(
        agentId: String,
        key: String,
        prompt: Prompt,
        mode: String?,
        model: ModelSelection?,
        mcpServers: List<McpServer>?,
        envVars: Map<String, String>?,
    ): Run {
        val vars = if (CloudFieldGate.followUpEnv) envVars else null
        return try {
            createRunOnce(agentId, key, prompt, mode, model, mcpServers, vars)
        } catch (e: ApiException) {
            when {
                e.isArchivedAgent -> {
                    unarchive(agentId)
                    createRunOnce(agentId, key, prompt, mode, model, mcpServers, vars)
                }
                CloudFieldGate.rejects(e, "envVars") && vars != null -> {
                    CloudFieldGate.followUpEnv = false
                    createRunOnce(agentId, key, prompt, mode, model, mcpServers, null)
                }
                else -> throw e
            }
        }
    }

    private suspend fun createRunOnce(
        agentId: String,
        key: String,
        prompt: Prompt,
        mode: String?,
        model: ModelSelection?,
        mcpServers: List<McpServer>?,
        envVars: Map<String, String>?,
    ): Run {
        // Create Run replaces the servers the agent was created with, so they are left out unless this chat's
        // selection was changed on purpose; the agent then keeps the set it started with.
        return wrap {
            api.createRun(
                agentId,
                key,
                CreateRunRequest(
                    prompt = prompt,
                    mode = mode,
                    model = model,
                    mcpServers = mcpServers,
                    envVars = envVars,
                ),
            ).run
        }
    }

    suspend fun cancel(agentId: String, runId: String) {
        wrap { api.cancelRun(agentId, runId) }
        statusChanged(agentId)
    }

    /**
     * Cancel the active run, then send [prompt] as the next run.
     * A run that already finished (not cancellable) still continues to the new run.
     */
    suspend fun sendQueuedNow(
        agentId: String,
        activeRunId: String?,
        prompt: Prompt,
        mode: String? = null,
        model: ModelSelection? = null,
        mcpServers: List<McpServer>? = null,
        envVars: Map<String, String>? = null,
        idempotencyKey: String? = null,
    ): Run {
        return ImmediateSend.cancelThenRun(
            agentId = agentId,
            activeRunId = activeRunId,
            prompt = prompt,
            cancel = { id, runId ->
                try {
                    cancel(id, runId)
                } catch (e: ApiException) {
                    if (!e.isNotCancellable) throw e
                }
            },
            createRun = { id, body ->
                followUp(id, body, mode, model, mcpServers, envVars, idempotencyKey)
            },
        )
    }

    suspend fun steer(agentId: String, runId: String, prompt: Prompt): Boolean {
        val body = SteerRequest(ClientOrigin.stamp(prompt))
        if (runCatching { wrap { api.steerRun(agentId, runId, body) } }.isSuccess) return true
        return runCatching { wrap { api.steerAgent(agentId, body) } }.isSuccess
    }

    fun stream(agentId: String, runId: String, lastEventId: String? = null): Flow<StreamEvent> {
        if (store.demoMode) {
            return flow { emit(StreamEvent.Done) }
        }
        if (session.expired()) return flow { throw ApiException.sessionExpired() }
        return sse.stream(agentId, runId, apiKey(), lastEventId)
    }

    private class WorkerPages(val workers: List<Worker>, val complete: Boolean, val failure: Throwable?)

    /** Pages gathered so far survive a later failure; [WorkerPages.complete] says whether the whole list was read. */
    private suspend fun listWorkerPages(scope: String): WorkerPages {
        val out = ArrayList<Worker>()
        var token: String? = null
        repeat(WORKER_PAGES) {
            val page = try {
                wrap { api.listWorkers(status = "all", scope = scope, limit = WORKER_PAGE_SIZE, pageToken = token) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return WorkerPages(out, complete = false, failure = e)
            }
            out += page.workers
            token = page.nextPageToken?.takeIf { it.isNotBlank() && it != token }
            if (token == null) return WorkerPages(out, complete = true, failure = null)
        }
        return WorkerPages(out, complete = true, failure = null)
    }

    suspend fun listComputers(knownAgents: List<AgentSummary> = emptyList()): List<Computer> =
        listMachines(knownAgents).computers

    /**
     * Online state comes only from a fresh workers call. When that call fails (rate limit, expired sign-in, network),
     * machines that were not listed are returned as [Computer.statusUnknown] instead of offline, and nothing is
     * learned from the listing.
     */
    suspend fun listMachines(knownAgents: List<AgentSummary> = emptyList()): MachineListing {
        var pages = listWorkerPages("personal")
        if (pages.workers.isEmpty() && pages.failure != null) {
            val all = listWorkerPages("all")
            if (all.workers.isNotEmpty() || all.failure == null) pages = all
        }
        val known = pages.complete
        val fromWorkers = pages.workers
            .map { worker ->
                Computer(
                    name = worker.displayName(),
                    online = true,
                    inUse = worker.isInUse == true,
                    detail = worker.detail().ifBlank { null },
                    workerId = worker.workerId,
                    repoUrl = worker.boundRepo(),
                    workspaceRootPath = worker.workspaceRootPath?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .distinctBy { it.workerId?.takeIf(String::isNotBlank) ?: it.name.lowercase() }
        val seen = fromWorkers.map { it.name.lowercase() }.toHashSet()
        val agents = knownAgents.ifEmpty { emptyList() }
        val fromAgents = agents
            .mapNotNull { agent ->
                val env = agent.env ?: return@mapNotNull null
                if (!isRemoteEnvType(env.type)) return@mapNotNull null
                val name = env.name?.trim().orEmpty()
                if (name.isEmpty() || name.lowercase() in seen) null
                else Computer(name = name, online = false, detail = SEEN_ON_AGENT)
            }
            .distinctBy { it.name.lowercase() }
        val live = fromWorkers + fromAgents
        val book = machines?.takeIf { !store.demoMode }
        val all = if (book == null) {
            live
        } else {
            runCatching {
                book.seed(catalog.computers().filter { !it.statusUnknown })
                if (known) book.observe(live, agents)
                live + book.remembered(live)
            }.getOrDefault(live)
        }
        val computers = if (known) all else all.map { if (it.online) it else it.copy(statusUnknown = true) }
        return MachineListing(computers, known, if (known) null else pages.failure?.let(::machineFailureReason))
    }

    private fun machineFailureReason(e: Throwable): String = when {
        e is ApiException && e.isInvalidKey -> "sign-in expired"
        e is ApiException && e.isRateLimited -> "rate limited"
        else -> "no response"
    }

    suspend fun listPools(): List<WorkerPool> {
        val listed = runCatching {
            wrap { api.listPools(scope = "all", includeStale = false) }.pools
        }.getOrElse {
            runCatching { wrap { api.listPools(includeStale = false) }.pools }.getOrDefault(emptyList())
        }
        val named = listed.filter { it.poolName.isNotBlank() }
            .distinctBy { "${it.scope}:${it.poolName.lowercase()}" }
        if (named.isNotEmpty()) catalog.savePools(named)
        return named
    }

    suspend fun workersSummary(): WorkersSummaryResponse? {
        return runCatching { wrap { api.workersSummary() } }.getOrNull()
    }

    suspend fun branchTip(repoUrl: String, ref: String): GitCommitTip? {
        val name = ref.trim()
        if (name.isEmpty()) return null
        val host = gitHost(repoUrl)
        val path = gitPath(repoUrl)
        if (path.isBlank()) return null
        val encoded = URLEncoder.encode(name, Charsets.UTF_8.name())
        val url = when {
            host.contains("github") ->
                "https://api.github.com/repos/$path/commits/$encoded"
            host.contains("gitlab") -> {
                val project = URLEncoder.encode(path, Charsets.UTF_8.name())
                "https://$host/api/v4/projects/$project/repository/commits/$encoded"
            }
            else -> return null
        }
        val body = publicJson(url) ?: return null
        val obj = body as? JsonObject ?: return null
        val sha = obj.string("sha") ?: obj.string("id") ?: return null
        val title = obj.obj("commit")?.string("message")
            ?: obj.string("title")
            ?: obj.string("message")
            ?: sha
        return GitCommitTip(sha = sha, title = title.substringBefore('\n').trim(), ref = name)
    }

    suspend fun repoBehind(
        repoUrl: String,
        baseBranch: String,
        agentBranch: String?,
        startSha: String?,
    ): RepoBehind? {
        val latest = branchTip(repoUrl, baseBranch) ?: return null
        val headRef = agentBranch?.takeIf { it.isNotBlank() }
        val chat = when {
            headRef != null -> branchTip(repoUrl, headRef) ?: startSha?.let { sha ->
                GitCommitTip(sha = sha, title = sha.take(7), ref = headRef)
            }
            !startSha.isNullOrBlank() -> GitCommitTip(sha = startSha, title = startSha.take(7), ref = baseBranch)
            else -> null
        } ?: return null
        if (chat.sha.equals(latest.sha, ignoreCase = true) ||
            latest.sha.startsWith(chat.sha, ignoreCase = true) ||
            chat.sha.startsWith(latest.sha, ignoreCase = true)
        ) {
            return null
        }
        val behindBy = commitsBehind(repoUrl, base = baseBranch, head = headRef ?: chat.sha) ?: 1
        if (behindBy <= 0) return null
        return RepoBehind(
            repoUrl = repoUrl,
            branch = baseBranch,
            chatSha = chat.sha,
            chatTitle = chat.title,
            remoteSha = latest.sha,
            remoteTitle = latest.title,
            behindBy = behindBy,
        )
    }

    private suspend fun commitsBehind(repoUrl: String, base: String, head: String): Int? {
        val host = gitHost(repoUrl)
        val path = gitPath(repoUrl)
        if (path.isBlank()) return null
        val url = when {
            host.contains("github") -> {
                val left = URLEncoder.encode(base, Charsets.UTF_8.name())
                val right = URLEncoder.encode(head, Charsets.UTF_8.name())
                "https://api.github.com/repos/$path/compare/$left...$right"
            }
            host.contains("gitlab") -> {
                val project = URLEncoder.encode(path, Charsets.UTF_8.name())
                val from = URLEncoder.encode(head, Charsets.UTF_8.name())
                val to = URLEncoder.encode(base, Charsets.UTF_8.name())
                "https://$host/api/v4/projects/$project/repository/compare?from=$from&to=$to"
            }
            else -> return null
        }
        val body = publicJson(url) as? JsonObject ?: return null
        body.int("behind_by")?.let { return it }
        val commits = body["commits"] as? JsonArray
        return commits?.size
    }

    private suspend fun publicJson(url: String): kotlinx.serialization.json.JsonElement? = withContext(Dispatchers.IO) {
        publicJsonBlocking(url)
    }

    private fun publicJsonBlocking(url: String): kotlinx.serialization.json.JsonElement? {
        if (store.demoMode) return null
        if (!SafeLinks.isHttps(url)) return null
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", ClientOrigin.ID)
            .build()
        val text = runCatching {
            publicHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        }.getOrNull().orEmpty()
        if (text.isBlank()) return null
        return runCatching { json.parseToJsonElement(text) }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? {
        return get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.int(key: String): Int? {
        return runCatching { get(key)?.jsonPrimitive?.content?.toInt() }.getOrNull()
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private suspend fun publicBranches(repoUrl: String): List<String> = withContext(Dispatchers.IO) {
        publicBranchesBlocking(repoUrl)
    }

    private fun publicBranchesBlocking(repoUrl: String): List<String> {
        val host = gitHost(repoUrl)
        val path = gitPath(repoUrl)
        if (path.isBlank()) return emptyList()
        val url = when {
            host.contains("github") ->
                "https://api.github.com/repos/$path/branches?per_page=100"
            host.contains("gitlab") -> {
                val project = URLEncoder.encode(path, Charsets.UTF_8.name())
                "https://$host/api/v4/projects/$project/repository/branches?per_page=100"
            }
            host.contains("bitbucket") ->
                "https://api.bitbucket.org/2.0/repositories/$path/refs/branches?pagelen=100"
            else -> return emptyList()
        }
        if (!SafeLinks.isHttps(url)) return emptyList()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", ClientOrigin.ID)
            .build()
        val body = runCatching {
            publicHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string().orEmpty()
            }
        }.getOrNull().orEmpty()
        if (body.isBlank()) return emptyList()
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val root = when (parsed) {
            is JsonArray -> parsed
            is JsonObject -> parsed["values"] as? JsonArray ?: parsed["items"] as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return root.mapNotNull { el ->
            (el as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
    }

    private suspend fun <T> wrapSecret(block: suspend () -> T): T {
        try {
            return wrap(block)
        } catch (e: ApiException) {
            if (e.isSecretNameAmbiguous) {
                throw SecretAmbiguousException(secretVersionsFromBody(e.body), e)
            }
            throw e
        }
    }

    private suspend fun <T> wrap(block: suspend () -> T): T {
        val key = if (store.demoMode) null else store.apiKey
        if (session.expiredFor(key)) throw ApiException.sessionExpired()
        return try {
            block()
        } catch (e: ApiException) {
            if (e.isInvalidKey) session.markExpired(key)
            throw e
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val api = ApiException.fromBody(
                e.code(),
                body.ifBlank { e.message() },
                e.response()?.headers()?.get("Retry-After"),
            )
            if (api.isInvalidKey) session.markExpired(key)
            throw api
        }
    }

    companion object {
        private val DEFAULT_BRANCHES = listOf("main", "master", "develop")
        private const val MODELS_TTL_MS = 10L * 60L * 1000L
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20
        private const val AGENT_TTL_MS = 1_500L
        private const val RUN_TTL_MS = 1_000L
        private const val WORKER_PAGE_SIZE = 50
        private const val WORKER_PAGES = 10
        private const val USAGE_BUDGET_MS = 25_000L
        private const val USAGE_CALL_MS = 10_000L
        private const val ROW_BUDGET_MS = 20_000L
    }
}

internal object RepoRateGate {
    const val MIN_GAP_MS = 65_000L

    fun mayCall(lastAt: Long, now: Long, minGapMs: Long = MIN_GAP_MS): Boolean =
        lastAt == 0L || now < lastAt || now - lastAt >= minGapMs
}

/** The API lists GitHub repos only; cached repos from other hosts (forges, created repos) are kept. */
internal fun mergeListedRepos(found: List<RepositoryItem>, cached: List<RepositoryItem>): List<RepositoryItem> {
    if (found.isEmpty()) return cached
    val others = cached.filterNot { it.host().equals("github.com", ignoreCase = true) }
    return (found + others)
        .distinctBy { com.cursorandroid.app.data.api.repoKey(it.url) }
        .sortedBy { it.displayName().lowercase() }
}
