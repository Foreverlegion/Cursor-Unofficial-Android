package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentConversation
import com.cursorandroid.app.data.api.AgentDetail
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.AgentUsageResponse
import com.cursorandroid.app.data.api.ArtifactItem
import com.cursorandroid.app.data.api.CloudEnvironment
import com.cursorandroid.app.data.api.ConversationMessage
import com.cursorandroid.app.data.api.Env
import com.cursorandroid.app.data.api.EnvRepo
import com.cursorandroid.app.data.api.GitBranch
import com.cursorandroid.app.data.api.GitState
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Repo
import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.TokenUsage
import com.cursorandroid.app.data.api.Worker
import com.cursorandroid.app.data.api.WorkerPool
import java.util.UUID

class DemoSession {
    private val lock = Any()
    private val agents = mutableListOf<AgentDetail>()
    private val runs = mutableMapOf<String, MutableList<Run>>()
    private val messages = mutableMapOf<String, MutableList<ConversationMessage>>()
    private val environments = mutableListOf<CloudEnvironment>()

    init {
        seed()
    }

    fun summaries(includeArchived: Boolean): List<AgentSummary> = synchronized(lock) {
        agents.map { it.summary() }
            .filter { includeArchived || it.archived != true }
    }

    fun agent(id: String): AgentDetail? = synchronized(lock) {
        agents.firstOrNull { it.id == id }
    }

    fun conversation(id: String): AgentConversation = synchronized(lock) {
        AgentConversation(id = id, messages = messages[id].orEmpty().toList())
    }

    fun runs(id: String): List<Run> = synchronized(lock) {
        runs[id].orEmpty().toList()
    }

    fun run(agentId: String, runId: String): Run? = synchronized(lock) {
        runs[agentId]?.firstOrNull { it.id == runId }
    }

    fun environments(): List<CloudEnvironment> = synchronized(lock) { environments.toList() }

    fun environment(id: String): CloudEnvironment? = synchronized(lock) {
        environments.firstOrNull { it.id == id }
    }

    fun repos(): List<RepositoryItem> = REPOS

    fun models(): List<ModelItem> = MODELS

    fun workers(): List<Worker> = WORKERS

    fun pools(): List<WorkerPool> = POOLS

    fun artifacts(id: String): List<ArtifactItem> = synchronized(lock) {
        if (id == NOTES) {
            listOf(
                ArtifactItem(
                    path = "release-notes.md",
                    sizeBytes = 840,
                    updatedAt = "2026-10-06T18:20:00Z",
                ),
            )
        } else {
            emptyList()
        }
    }

    fun usage(id: String): AgentUsageResponse {
        val turns = runs(id).size.coerceAtLeast(1)
        return AgentUsageResponse(
            totalUsage = TokenUsage(inputTokens = 1200L * turns, outputTokens = 800L * turns, totalTokens = 2000L * turns),
        )
    }

    fun createAgent(name: String?, prompt: String, env: Env?, repos: List<Repo>?): Pair<AgentDetail, Run> = synchronized(lock) {
        val id = "demo-" + UUID.randomUUID().toString().take(8)
        val title = name?.trim()?.takeIf { it.isNotEmpty() } ?: prompt.lineSequence().firstOrNull()?.take(48) ?: "Demo chat"
        val now = "2026-10-07T09:00:00Z"
        val detail = AgentDetail(
            id = id,
            name = title,
            status = "FINISHED",
            env = env ?: Env(type = "cloud", name = "Android sample"),
            repos = repos,
            createdAt = now,
            updatedAt = now,
            latestRunId = "$id-run-1",
        )
        val run = finishedRun(id, "$id-run-1", prompt, REPLY, now)
        agents.add(0, detail)
        runs[id] = mutableListOf(run)
        messages[id] = mutableListOf(
            ConversationMessage(id = "$id-u1", type = "user", text = prompt),
            ConversationMessage(id = "$id-a1", type = "assistant", text = run.result),
        )
        detail to run
    }

    fun followUp(agentId: String, prompt: String): Run = synchronized(lock) {
        val now = "2026-10-07T09:05:00Z"
        val reply = REPLY
        val run = finishedRun(agentId, "$agentId-run-${(runs[agentId]?.size ?: 0) + 1}", prompt, reply, now)
        runs.getOrPut(agentId) { mutableListOf() }.add(run)
        messages.getOrPut(agentId) { mutableListOf() }.add(ConversationMessage(id = "${run.id}-u", type = "user", text = prompt))
        messages.getOrPut(agentId) { mutableListOf() }.add(ConversationMessage(id = "${run.id}-a", type = "assistant", text = reply))
        val index = agents.indexOfFirst { it.id == agentId }
        if (index >= 0) {
            agents[index] = agents[index].copy(status = "FINISHED", updatedAt = now, latestRunId = run.id)
        }
        run
    }

    fun setStatus(agentId: String, status: String, archived: Boolean? = null) = synchronized(lock) {
        val index = agents.indexOfFirst { it.id == agentId }
        if (index < 0) return
        val current = agents[index]
        agents[index] = current.copy(
            status = status,
            archived = archived ?: current.archived,
            updatedAt = "2026-10-07T09:10:00Z",
        )
    }

    fun remove(agentId: String) = synchronized(lock) {
        agents.removeAll { it.id == agentId }
        runs.remove(agentId)
        messages.remove(agentId)
    }

    fun saveEnvironment(env: CloudEnvironment) = synchronized(lock) {
        environments.removeAll { it.id == env.id || it.name.equals(env.name, ignoreCase = true) }
        environments.add(env)
    }

    fun deleteEnvironment(id: String) = synchronized(lock) {
        environments.removeAll { it.id == id }
    }

    private fun seed() {
        val android = Repo(url = ANDROID_REPO, startingRef = "main")
        val docs = Repo(url = DOCS_REPO, startingRef = "main")
        environments += CloudEnvironment(
            id = "env-android",
            name = "Android sample",
            owner = "personal",
            repos = listOf(EnvRepo(ANDROID_REPO)),
            createdAt = "2026-09-01T12:00:00Z",
            updatedAt = "2026-10-01T12:00:00Z",
        )
        environments += CloudEnvironment(
            id = "env-docs",
            name = "Docs site",
            owner = "personal",
            repos = listOf(EnvRepo(DOCS_REPO)),
            createdAt = "2026-09-02T12:00:00Z",
            updatedAt = "2026-10-02T12:00:00Z",
        )
        add(
            id = SPACING,
            name = "Fix the login spacing",
            status = "FINISHED",
            env = Env(type = "cloud", name = "Android sample"),
            repo = android,
            updated = "2026-10-07T08:50:00Z",
            turns = listOf(
                "The login button sits too close to the password field." to
                    "I'll add 16dp between the field and the button.",
                "Keep the button full width." to
                    "Done. The button stays full width, with the extra space above it.",
            ),
        )
        add(
            id = NOTES,
            name = "Write the release notes",
            status = "ERROR",
            env = Env(type = "cloud", name = "Docs site"),
            repo = docs,
            updated = "2026-10-06T18:20:00Z",
            turns = listOf(
                "Write the release notes for the inbox filters." to
                    "Drafted notes for the hidden filter, thinking scroll, and inbox refresh.",
            ),
        )
    }

    private fun add(
        id: String,
        name: String,
        status: String,
        env: Env,
        repo: Repo,
        updated: String,
        archived: Boolean = false,
        turns: List<Pair<String, String>>,
    ) {
        val runList = mutableListOf<Run>()
        val lines = mutableListOf<ConversationMessage>()
        turns.forEachIndexed { index, (user, assistant) ->
            val runId = "$id-run-${index + 1}"
            val runStatus = if (index == turns.lastIndex) status else "FINISHED"
            runList += finishedRun(id, runId, user, assistant, updated).copy(status = runStatus)
            lines += ConversationMessage(id = "$id-u${index + 1}", type = "user", text = user)
            lines += ConversationMessage(id = "$id-a${index + 1}", type = "assistant", text = assistant)
        }
        agents += AgentDetail(
            id = id,
            name = name,
            status = status,
            env = env,
            repos = listOf(repo),
            createdAt = updated,
            updatedAt = updated,
            latestRunId = runList.lastOrNull()?.id,
            archived = archived,
        )
        runs[id] = runList
        messages[id] = lines
    }

    private fun finishedRun(agentId: String, runId: String, prompt: String, result: String, at: String): Run {
        return Run(
            id = runId,
            agentId = agentId,
            status = "FINISHED",
            createdAt = at,
            updatedAt = at,
            result = result,
            prompt = Prompt(text = prompt),
            git = GitState(
                branches = listOf(
                    GitBranch(
                        repoUrl = ANDROID_REPO,
                        branch = "demo/$agentId",
                    ),
                ),
            ),
        )
    }

    private fun AgentDetail.summary(): AgentSummary {
        return AgentSummary(
            id = id,
            name = name,
            status = status,
            env = env,
            url = url,
            createdAt = createdAt,
            updatedAt = updatedAt,
            latestRunId = latestRunId,
            archived = archived,
        )
    }

    companion object {
        const val REPLY =
            "this is a demo only account. Please log in to a real cursor account to use the full app."
        const val SPACING = "demo-spacing"
        const val NOTES = "demo-notes"
        const val ANDROID_REPO = "https://github.com/example/demo-android"
        const val DOCS_REPO = "https://github.com/example/demo-docs"

        val REPOS = listOf(
            RepositoryItem(url = ANDROID_REPO, provider = "github", defaultBranch = "main", name = "demo-android"),
            RepositoryItem(url = DOCS_REPO, provider = "github", defaultBranch = "main", name = "demo-docs"),
        )
        val MODELS = listOf(
            ModelItem(id = "demo-composer", displayName = "Composer", description = "Sample model"),
            ModelItem(id = "demo-fast", displayName = "Fast", description = "Sample model"),
        )
        val WORKERS = listOf(
            Worker(
                workerId = "worker-laptop",
                name = "demo-laptop",
                isInUse = false,
                repoUrl = ANDROID_REPO,
                repoOwner = "example",
                repoName = "demo-android",
                workspaceRootPath = "/home/demo/demo-android",
            ),
            Worker(
                workerId = "worker-build",
                name = "demo-build",
                isInUse = true,
                repoUrl = DOCS_REPO,
                repoOwner = "example",
                repoName = "demo-docs",
                workspaceRootPath = "/home/demo/demo-docs",
            ),
        )
        val POOLS = listOf(
            WorkerPool(
                scope = "personal",
                poolName = "android-pool",
                connectedWorkerCount = 2,
                inUseWorkerCount = 1,
                repoOwner = "example",
                repoName = "demo-android",
                repoUrl = ANDROID_REPO,
            ),
        )
    }
}
