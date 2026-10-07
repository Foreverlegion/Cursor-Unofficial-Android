package com.cursorandroid.app.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CursorApi {
    @GET("v1/me")
    suspend fun me(): MeResponse

    @GET("v1/models")
    suspend fun models(): ModelListResponse

    @GET("v1/repositories")
    suspend fun repositories(
        @Query("provider") provider: String? = null,
    ): RepositoryListResponse

    @GET("v1/repositories/branches")
    suspend fun repositoryBranches(
        @Query("url") url: String,
    ): BranchListResponse

    @GET("v1/agents")
    suspend fun listAgents(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
        @Query("includeArchived") includeArchived: Boolean = true,
    ): AgentListResponse

    @GET("v1/agents/{id}")
    suspend fun getAgent(@Path("id") id: String): AgentDetail

    @GET("v0/agents/{id}/conversation")
    suspend fun getConversation(@Path("id") id: String): AgentConversation

    @POST("v1/agents")
    suspend fun createAgent(@Body body: CreateAgentRequest): CreateAgentResponse

    @POST("v1/environments")
    suspend fun createEnvironment(@Body body: CreateEnvironmentRequest): CloudEnvironment

    @GET("v1/environments/{id}")
    suspend fun getEnvironment(@Path("id") id: String): CloudEnvironment

    @HTTP(method = "DELETE", path = "v1/environments/{id}", hasBody = false)
    suspend fun deleteEnvironment(@Path("id") id: String)

    @GET("v1/environments/{id}/builds")
    suspend fun listEnvironmentBuilds(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
    ): EnvironmentBuildList

    @GET("v1/environments/{id}/builds/active")
    suspend fun activeEnvironmentBuild(@Path("id") id: String): EnvironmentActiveBuild

    @GET("v1/environments/{id}/builds/{buildId}")
    suspend fun getEnvironmentBuild(
        @Path("id") id: String,
        @Path("buildId") buildId: String,
    ): EnvironmentBuild

    @GET("v1/agents/{id}/runs")
    suspend fun listRuns(
        @Path("id") id: String,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
    ): RunListResponse

    @GET("v1/agents/{id}/runs/{runId}")
    suspend fun getRun(
        @Path("id") id: String,
        @Path("runId") runId: String,
    ): Run

    @POST("v1/agents/{id}/runs")
    suspend fun createRun(
        @Path("id") id: String,
        @Body body: CreateRunRequest,
    ): CreateRunResponse

    @POST("v1/agents/{id}/runs/{runId}/cancel")
    suspend fun cancelRun(
        @Path("id") id: String,
        @Path("runId") runId: String,
    )

    @POST("v1/agents/{id}/runs/{runId}/steer")
    suspend fun steerRun(
        @Path("id") id: String,
        @Path("runId") runId: String,
        @Body body: SteerRequest,
    )

    @POST("v1/agents/{id}/steer")
    suspend fun steerAgent(
        @Path("id") id: String,
        @Body body: SteerRequest,
    )

    @GET("v1/agents/{id}/artifacts")
    suspend fun listArtifacts(@Path("id") id: String): ArtifactListResponse

    @GET("v1/agents/{id}/artifacts/download")
    suspend fun downloadArtifact(
        @Path("id") id: String,
        @Query("path") path: String,
    ): ArtifactDownloadResponse

    @GET("v1/agents/{id}/usage")
    suspend fun agentUsage(
        @Path("id") id: String,
        @Query("runId") runId: String? = null,
    ): AgentUsageResponse

    @POST("v1/agents/{id}/archive")
    suspend fun archiveAgent(@Path("id") id: String)

    @POST("v1/agents/{id}/unarchive")
    suspend fun unarchiveAgent(@Path("id") id: String)

    @HTTP(method = "DELETE", path = "v1/agents/{id}", hasBody = false)
    suspend fun deleteAgent(@Path("id") id: String)

    @GET("v0/private-workers")
    suspend fun listWorkers(
        @Query("status") status: String = "all",
        @Query("scope") scope: String = "personal",
        @Query("limit") limit: Int = 50,
        @Query("pageToken") pageToken: String? = null,
    ): WorkerListResponse

    @GET("v0/private-workers/summary")
    suspend fun workersSummary(): WorkersSummaryResponse

    @GET("v0/private-workers/{id}")
    suspend fun getWorker(@Path("id") id: String): Worker

    @GET("v0/private-workers/pools")
    suspend fun listPools(
        @Query("scope") scope: String? = null,
        @Query("includeStale") includeStale: Boolean = false,
    ): PoolListResponse
}
