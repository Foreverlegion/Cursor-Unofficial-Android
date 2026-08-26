package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AccountOverview
import com.cursorandroid.app.data.api.AgentDetail
import com.cursorandroid.app.data.api.AgentListResponse
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.AgentUsageRow
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.TokenUsage
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.CreateAgentResponse
import com.cursorandroid.app.data.api.CreateRunRequest
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
import com.cursorandroid.app.data.api.markCloudArchived
import com.cursorandroid.app.data.api.sortKey
import com.cursorandroid.app.data.auth.ApiKeyStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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
) {
    fun apiKey(): String = store.apiKey.orEmpty()

    suspend fun me(): MeResponse = wrap { api.me() }

    suspend fun listAgents(includeArchived: Boolean = true, cursor: String? = null): List<AgentSummary> {
        return if (cursor == null) {
            listAllAgents(includeArchived).items
        } else {
            listAgentsPage(includeArchived, cursor).entries()
        }
    }

    suspend fun listAgentsPage(includeArchived: Boolean = true, cursor: String? = null): AgentListResponse {
        return wrap {
            api.listAgents(
                limit = PAGE_SIZE,
                includeArchived = includeArchived,
                cursor = cursor,
            )
        }
    }

    suspend fun listAllAgents(includeArchived: Boolean = true): AgentListResponse {
        val pages = ArrayList<AgentListResponse>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = listAgentsPage(includeArchived, cursor)
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

    suspend fun getAgent(id: String): AgentDetail = wrap { api.getAgent(id) }

    suspend fun getRun(agentId: String, runId: String): Run = wrap { api.getRun(agentId, runId) }

    suspend fun listRuns(agentId: String, limit: Int = 50): List<Run> {
        return wrap { api.listRuns(agentId, limit).items }
    }

    suspend fun archive(agentId: String) = wrap { api.archiveAgent(agentId) }

    suspend fun unarchive(agentId: String) = wrap { api.unarchiveAgent(agentId) }

    suspend fun deleteAgent(agentId: String) = wrap { api.deleteAgent(agentId) }

    suspend fun artifacts(agentId: String) = wrap { api.listArtifacts(agentId).items }

    suspend fun artifactUrl(agentId: String, path: String) = wrap { api.downloadArtifact(agentId, path).url }

    suspend fun usage(agentId: String) = runCatching { wrap { api.agentUsage(agentId) } }.getOrNull()

    suspend fun accountOverview(): AccountOverview {
        return coroutineScope {
            val me = async { runCatching { me() }.getOrNull() }
            val models = async { runCatching { models() }.getOrDefault(emptyList()) }
            val agents = async {
                catalog.agents().ifEmpty {
                    runCatching { listAgents() }.getOrDefault(emptyList())
                }
            }
            val computers = async {
                catalog.computers().ifEmpty {
                    runCatching { listComputers() }.getOrDefault(emptyList())
                }
            }
            val list = agents.await()
            val samples = list.take(20)
            val usages = samples.map { agent ->
                async {
                    val used = usage(agent.id)?.totalUsage ?: return@async null
                    agent to used
                }
            }.mapNotNull { it.await() }
            val total = usages.fold(TokenUsage()) { acc, row -> acc.plus(row.second) }
            val top = usages
                .map { (agent, used) ->
                    AgentUsageRow(
                        id = agent.id,
                        name = agent.name?.ifBlank { null } ?: agent.id,
                        tokens = used.totalTokens ?: 0L,
                    )
                }
                .sortedByDescending { it.tokens }
                .take(8)
            val machines = computers.await()
            AccountOverview(
                me = me.await(),
                agentCount = list.size,
                modelNames = models.await().map { it.displayName ?: it.id },
                repoCount = catalog.repos().size,
                computerCount = machines.size,
                computersOnline = machines.count { it.online },
                usage = total,
                sampledAgents = samples.size,
                top = top,
            )
        }
    }

    suspend fun refreshGitSnaps(agents: List<AgentSummary>) {
        val targets = agents.take(15)
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
                            repoUrl = git.repoUrl,
                        ),
                    )
                    git.repoUrl?.let { url ->
                        val full = if (url.startsWith("http")) url else "https://$url"
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

    suspend fun models(): List<ModelItem> = wrap { api.models().items }

    suspend fun repositories(force: Boolean = false): List<RepositoryItem> {
        if (!force && catalog.reposFresh()) return catalog.repos()
        val found = coroutineScope {
            val github = async { runCatching { wrap { api.repositories().items } }.getOrDefault(emptyList()) }
            val extras = listOf("gitlab", "bitbucket", "azure").map { provider ->
                async {
                    runCatching { wrap { api.repositories(provider = provider).items } }.getOrDefault(emptyList())
                }
            }
            (listOf(github) + extras).flatMap { it.await() }
        }
        val merged = found
            .distinctBy { it.url.trim().lowercase().removeSuffix(".git") }
            .sortedBy { it.displayName().lowercase() }
        if (merged.isNotEmpty()) catalog.saveRepos(merged)
        return merged.ifEmpty { catalog.repos() }
    }

    suspend fun branches(repoUrl: String, defaultBranch: String? = null): List<String> {
        if (catalog.branchesFresh(repoUrl)) {
            return catalog.branches(repoUrl)
        }
        val fromApi = runCatching { wrap { api.repositoryBranches(repoUrl).names() } }.getOrDefault(emptyList())
        val fromHost = if (fromApi.isEmpty()) publicBranches(repoUrl) else emptyList()
        val names = (fromApi + fromHost + listOfNotNull(defaultBranch) + DEFAULT_BRANCHES)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (names.isNotEmpty()) catalog.saveBranches(repoUrl, names)
        return names
    }

    suspend fun createAgent(body: CreateAgentRequest): CreateAgentResponse {
        return wrap {
            api.createAgent(
                body.copy(
                    prompt = ClientOrigin.stamp(body.prompt),
                    envVars = (body.envVars ?: emptyMap()) + ("CLIENT_APP" to ClientOrigin.ID),
                ),
            )
        }
    }

    suspend fun followUp(
        agentId: String,
        prompt: Prompt,
        mode: String? = null,
        model: ModelSelection? = null,
    ): Run {
        return wrap {
            api.createRun(
                agentId,
                CreateRunRequest(
                    prompt = ClientOrigin.stamp(prompt),
                    mode = mode,
                    model = model,
                    mcpServers = store.mcpServers(),
                ),
            ).run
        }
    }

    suspend fun cancel(agentId: String, runId: String) {
        wrap { api.cancelRun(agentId, runId) }
    }

    fun stream(agentId: String, runId: String, lastEventId: String? = null): Flow<StreamEvent> {
        return sse.stream(agentId, runId, apiKey(), lastEventId)
    }

    suspend fun listComputers(knownAgents: List<AgentSummary> = emptyList()): List<Computer> {
        val online = runCatching {
            wrap { api.listWorkers(status = "all", scope = "personal").workers }
        }.getOrElse {
            runCatching {
                wrap { api.listWorkers(status = "all", scope = "all").workers }
            }.getOrDefault(emptyList())
        }
        val fromWorkers = online
            .map { worker ->
                Computer(
                    name = worker.displayName(),
                    online = true,
                    inUse = worker.isInUse == true,
                    detail = worker.detail().ifBlank { null },
                    workerId = worker.workerId,
                )
            }
            .distinctBy { it.name.lowercase() }
        val seen = fromWorkers.map { it.name.lowercase() }.toHashSet()
        val agents = knownAgents.ifEmpty { emptyList() }
        val fromAgents = agents
            .mapNotNull { agent ->
                val env = agent.env ?: return@mapNotNull null
                if (env.type?.lowercase() != "machine") return@mapNotNull null
                val name = env.name?.trim().orEmpty()
                if (name.isEmpty() || name.lowercase() in seen) null
                else Computer(name = name, online = false, detail = "Seen on a previous agent")
            }
            .distinctBy { it.name.lowercase() }
        return fromWorkers + fromAgents
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

    private fun commitsBehind(repoUrl: String, base: String, head: String): Int? {
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

    private fun publicJson(url: String): kotlinx.serialization.json.JsonElement? {
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

    private fun publicBranches(repoUrl: String): List<String> {
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
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return emptyList()
        return root.mapNotNull { el ->
            (el as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
    }

    private suspend fun <T> wrap(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: ApiException) {
            throw e
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            throw ApiException.fromBody(e.code(), body.ifBlank { e.message() })
        }
    }

    companion object {
        private val DEFAULT_BRANCHES = listOf("main", "master", "develop")
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20
    }
}
