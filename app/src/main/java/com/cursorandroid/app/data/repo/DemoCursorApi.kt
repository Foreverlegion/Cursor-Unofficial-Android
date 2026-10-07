package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentConversation
import com.cursorandroid.app.data.api.AgentDetail
import com.cursorandroid.app.data.api.AgentListResponse
import com.cursorandroid.app.data.api.AgentUsageResponse
import com.cursorandroid.app.data.api.ArtifactDownloadResponse
import com.cursorandroid.app.data.api.ArtifactListResponse
import com.cursorandroid.app.data.api.BranchListResponse
import com.cursorandroid.app.data.api.CloudEnvironment
import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.CreateAgentResponse
import com.cursorandroid.app.data.api.CreateEnvironmentRequest
import com.cursorandroid.app.data.api.CreateRunRequest
import com.cursorandroid.app.data.api.CreateRunResponse
import com.cursorandroid.app.data.api.CursorApi
import com.cursorandroid.app.data.api.EnvironmentActiveBuild
import com.cursorandroid.app.data.api.EnvironmentBuild
import com.cursorandroid.app.data.api.EnvironmentBuildList
import com.cursorandroid.app.data.api.MeResponse
import com.cursorandroid.app.data.api.ModelListResponse
import com.cursorandroid.app.data.api.PoolListResponse
import com.cursorandroid.app.data.api.RepositoryListResponse
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.RunListResponse
import com.cursorandroid.app.data.api.SteerRequest
import com.cursorandroid.app.data.api.Worker
import com.cursorandroid.app.data.api.WorkerCountSummary
import com.cursorandroid.app.data.api.WorkerListResponse
import com.cursorandroid.app.data.api.WorkersSummaryResponse
import com.cursorandroid.app.data.auth.ApiKeyStore
import java.util.UUID

class DemoCursorApi(
    private val store: ApiKeyStore,
    private val session: DemoSession,
    private val live: CursorApi,
) : CursorApi {
    private fun demo(): Boolean = store.demoMode

    override suspend fun me(): MeResponse {
        if (!demo()) return live.me()
        return MeResponse(
            apiKeyName = "demo",
            userFirstName = "Review",
            userLastName = "Demo",
            createdAt = "2026-01-01T00:00:00Z",
        )
    }

    override suspend fun models(): ModelListResponse {
        if (!demo()) return live.models()
        return ModelListResponse(items = session.models())
    }

    override suspend fun repositories(provider: String?): RepositoryListResponse {
        if (!demo()) return live.repositories(provider)
        val items = session.repos().filter { provider == null || it.provider.equals(provider, ignoreCase = true) }
        return RepositoryListResponse(items = items)
    }

    override suspend fun repositoryBranches(url: String): BranchListResponse {
        if (!demo()) return live.repositoryBranches(url)
        return BranchListResponse(branches = listOf("main", "develop"))
    }

    override suspend fun listAgents(limit: Int, cursor: String?, includeArchived: Boolean): AgentListResponse {
        if (!demo()) return live.listAgents(limit, cursor, includeArchived)
        return AgentListResponse(items = session.summaries(includeArchived))
    }

    override suspend fun getAgent(id: String): AgentDetail {
        if (!demo()) return live.getAgent(id)
        return session.agent(id) ?: AgentDetail(id = id, name = "Demo chat", status = "FINISHED")
    }

    override suspend fun getConversation(id: String): AgentConversation {
        if (!demo()) return live.getConversation(id)
        return session.conversation(id)
    }

    override suspend fun createAgent(body: CreateAgentRequest): CreateAgentResponse {
        if (!demo()) return live.createAgent(body)
        val (agent, run) = session.createAgent(body.name, body.prompt.text, body.env, body.repos)
        return CreateAgentResponse(agent = agent, run = run)
    }

    override suspend fun createEnvironment(body: CreateEnvironmentRequest): CloudEnvironment {
        if (!demo()) return live.createEnvironment(body)
        val created = CloudEnvironment(
            id = "env-" + UUID.randomUUID().toString().take(8),
            name = body.name,
            owner = body.owner,
            repos = body.repos,
            environmentJson = body.environmentJson,
        )
        session.saveEnvironment(created)
        return created
    }

    override suspend fun getEnvironment(id: String): CloudEnvironment {
        if (!demo()) return live.getEnvironment(id)
        return session.environment(id) ?: session.environments().firstOrNull() ?: CloudEnvironment(id = id, name = "Android sample")
    }

    override suspend fun deleteEnvironment(id: String) {
        if (!demo()) return live.deleteEnvironment(id)
        session.deleteEnvironment(id)
    }

    override suspend fun listEnvironmentBuilds(id: String, cursor: String?): EnvironmentBuildList {
        if (!demo()) return live.listEnvironmentBuilds(id, cursor)
        return EnvironmentBuildList(
            items = listOf(
                EnvironmentBuild(id = "build-$id", environmentId = id, status = "FINISHED"),
            ),
        )
    }

    override suspend fun activeEnvironmentBuild(id: String): EnvironmentActiveBuild {
        if (!demo()) return live.activeEnvironmentBuild(id)
        return EnvironmentActiveBuild(type = "none")
    }

    override suspend fun getEnvironmentBuild(id: String, buildId: String): EnvironmentBuild {
        if (!demo()) return live.getEnvironmentBuild(id, buildId)
        return EnvironmentBuild(id = buildId, environmentId = id, status = "FINISHED")
    }

    override suspend fun listRuns(id: String, limit: Int, cursor: String?): RunListResponse {
        if (!demo()) return live.listRuns(id, limit, cursor)
        return RunListResponse(items = session.runs(id))
    }

    override suspend fun getRun(id: String, runId: String): Run {
        if (!demo()) return live.getRun(id, runId)
        return session.run(id, runId) ?: Run(id = runId, agentId = id, status = "FINISHED")
    }

    override suspend fun createRun(id: String, body: CreateRunRequest): CreateRunResponse {
        if (!demo()) return live.createRun(id, body)
        return CreateRunResponse(run = session.followUp(id, body.prompt.text))
    }

    override suspend fun cancelRun(id: String, runId: String) {
        if (!demo()) return live.cancelRun(id, runId)
        session.setStatus(id, "CANCELLED")
    }

    override suspend fun steerRun(id: String, runId: String, body: SteerRequest) {
        if (!demo()) return live.steerRun(id, runId, body)
        session.followUp(id, body.prompt.text)
    }

    override suspend fun steerAgent(id: String, body: SteerRequest) {
        if (!demo()) return live.steerAgent(id, body)
        session.followUp(id, body.prompt.text)
    }

    override suspend fun listArtifacts(id: String): ArtifactListResponse {
        if (!demo()) return live.listArtifacts(id)
        return ArtifactListResponse(items = session.artifacts(id))
    }

    override suspend fun downloadArtifact(id: String, path: String): ArtifactDownloadResponse {
        if (!demo()) return live.downloadArtifact(id, path)
        return ArtifactDownloadResponse(url = "https://example.com/demo-artifact.txt")
    }

    override suspend fun agentUsage(id: String, runId: String?): AgentUsageResponse {
        if (!demo()) return live.agentUsage(id, runId)
        return session.usage(id)
    }

    override suspend fun archiveAgent(id: String) {
        if (!demo()) return live.archiveAgent(id)
        session.setStatus(id, "FINISHED", archived = true)
    }

    override suspend fun unarchiveAgent(id: String) {
        if (!demo()) return live.unarchiveAgent(id)
        session.setStatus(id, "FINISHED", archived = false)
    }

    override suspend fun deleteAgent(id: String) {
        if (!demo()) return live.deleteAgent(id)
        session.remove(id)
    }

    override suspend fun listWorkers(status: String, scope: String, limit: Int, pageToken: String?): WorkerListResponse {
        if (!demo()) return live.listWorkers(status, scope, limit, pageToken)
        return WorkerListResponse(workers = session.workers(), totalCount = session.workers().size)
    }

    override suspend fun workersSummary(): WorkersSummaryResponse {
        if (!demo()) return live.workersSummary()
        return WorkersSummaryResponse(userSummary = WorkerCountSummary(totalConnected = 2, inUse = 1))
    }

    override suspend fun getWorker(id: String): Worker {
        if (!demo()) return live.getWorker(id)
        return session.workers().firstOrNull { it.workerId == id } ?: session.workers().first()
    }

    override suspend fun listPools(scope: String?, includeStale: Boolean): PoolListResponse {
        if (!demo()) return live.listPools(scope, includeStale)
        return PoolListResponse(pools = session.pools())
    }
}
