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

    @POST("v1/agents")
    suspend fun createAgent(@Body body: CreateAgentRequest): CreateAgentResponse

    @GET("v1/agents/{id}/runs")
    suspend fun listRuns(
        @Path("id") id: String,
        @Query("limit") limit: Int = 20,
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
    ): WorkerListResponse
}
