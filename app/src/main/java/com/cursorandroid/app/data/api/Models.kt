package com.cursorandroid.app.data.api

import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val apiKeyName: String? = null,
    val userId: Int? = null,
    val userEmail: String? = null,
    val userFirstName: String? = null,
    val userLastName: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class Env(
    val type: String? = null,
    val name: String? = null,
)

@Serializable
data class Repo(
    val url: String,
    val startingRef: String? = null,
    val prUrl: String? = null,
)

@Serializable
data class AgentSummary(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    val env: Env? = null,
    val url: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val latestRunId: String? = null,
    val archived: Boolean? = null,
)

@Serializable
data class AgentListResponse(
    val items: List<AgentSummary> = emptyList(),
    val agents: List<AgentSummary> = emptyList(),
    val nextCursor: String? = null,
) {
    fun entries(): List<AgentSummary> = (items + agents).distinctBy { it.id }
}

@Serializable
data class AgentDetail(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    val env: Env? = null,
    val repos: List<Repo>? = null,
    val workOnCurrentBranch: Boolean? = null,
    val autoCreatePR: Boolean? = null,
    val url: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val latestRunId: String? = null,
    val archived: Boolean? = null,
)

@Serializable
data class PromptImage(
    val data: String? = null,
    val mimeType: String? = null,
    val url: String? = null,
)

@Serializable
data class Prompt(
    val text: String,
    val images: List<PromptImage>? = null,
)

@Serializable
data class ModelParam(
    val id: String,
    val value: String,
)

@Serializable
data class ModelSelection(
    val id: String,
    val params: List<ModelParam>? = null,
)

@Serializable
data class McpServer(
    val name: String,
    val type: String? = null,
    val url: String? = null,
    val command: String? = null,
    val args: List<String>? = null,
)

@Serializable
data class CustomSubagent(
    val name: String,
    val description: String,
    val prompt: String,
    val model: String? = null,
)

@Serializable
data class CreateAgentRequest(
    val prompt: Prompt,
    val model: ModelSelection? = null,
    val name: String? = null,
    val env: Env? = null,
    val repos: List<Repo>? = null,
    val autoCreatePR: Boolean? = null,
    val mode: String? = null,
    val mcpServers: List<McpServer>? = null,
    val customSubagents: List<CustomSubagent>? = null,
    val envVars: Map<String, String>? = null,
)

@Serializable
data class CreateAgentResponse(
    val agent: AgentDetail,
    val run: Run,
)

@Serializable
data class CreateRunRequest(
    val prompt: Prompt,
    val mode: String? = null,
    val model: ModelSelection? = null,
    val mcpServers: List<McpServer>? = null,
)

@Serializable
data class CreateRunResponse(
    val run: Run,
)

@Serializable
data class GitBranch(
    val repoUrl: String? = null,
    val branch: String? = null,
    val prUrl: String? = null,
)

@Serializable
data class GitState(
    val branches: List<GitBranch> = emptyList(),
)

@Serializable
data class Run(
    val id: String,
    val agentId: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val durationMs: Long? = null,
    val result: String? = null,
    val git: GitState? = null,
)

@Serializable
data class RunListResponse(
    val items: List<Run> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class ModelValue(
    val value: String,
    val displayName: String? = null,
)

@Serializable
data class ModelParameter(
    val id: String,
    val displayName: String? = null,
    val values: List<ModelValue> = emptyList(),
)

@Serializable
data class ModelVariant(
    val params: List<ModelParam> = emptyList(),
    val displayName: String? = null,
    val description: String? = null,
    val isDefault: Boolean? = null,
)

@Serializable
data class ModelItem(
    val id: String,
    val displayName: String? = null,
    val description: String? = null,
    val aliases: List<String>? = null,
    val parameters: List<ModelParameter>? = null,
    val variants: List<ModelVariant>? = null,
)

@Serializable
data class ModelListResponse(
    val items: List<ModelItem> = emptyList(),
)

@Serializable
data class RepositoryItem(
    val url: String,
    val provider: String? = null,
    val defaultBranch: String? = null,
    val name: String? = null,
) {
    fun host(): String = gitHost(url)
    fun providerLabel(): String = provider?.takeIf { it.isNotBlank() }?.let { prettyProvider(it) } ?: prettyProvider(host())
    fun displayName(): String = name?.takeIf { it.isNotBlank() } ?: gitPath(url)
}

@Serializable
data class RepositoryListResponse(
    val items: List<RepositoryItem> = emptyList(),
)

@Serializable
data class BranchItem(
    val name: String? = null,
    val isDefault: Boolean? = null,
)

@Serializable
data class BranchListResponse(
    val items: List<BranchItem> = emptyList(),
    val branches: List<String> = emptyList(),
) {
    fun names(): List<String> {
        return (items.mapNotNull { it.name?.takeIf(String::isNotBlank) } + branches)
            .distinct()
    }
}

@Serializable
data class Worker(
    val workerId: String,
    val name: String? = null,
    val isInUse: Boolean? = null,
    val repoUrl: String? = null,
    val repoOwner: String? = null,
    val repoName: String? = null,
    val workspaceRootPath: String? = null,
    val userId: Int? = null,
    val connectedAtMs: Long? = null,
    val activeBcId: String? = null,
) {
    fun displayName(): String {
        return name?.takeIf { it.isNotBlank() }
            ?: workspaceRootPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: repoName?.takeIf { it.isNotBlank() }
            ?: workerId
    }

    fun detail(): String {
        return listOfNotNull(
            workspaceRootPath?.takeIf { it.isNotBlank() },
            repoUrl?.takeIf { it.isNotBlank() },
        ).firstOrNull().orEmpty()
    }
}

@Serializable
data class WorkerListResponse(
    val workers: List<Worker> = emptyList(),
    val totalCount: Int? = null,
    val nextPageToken: String? = null,
)

@Serializable
data class Computer(
    val name: String,
    val online: Boolean,
    val inUse: Boolean = false,
    val detail: String? = null,
    val workerId: String? = null,
)

fun gitHost(url: String): String {
    return url.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("ssh://")
        .removePrefix("git@")
        .substringBefore("/")
        .substringBefore(":")
        .lowercase()
}

fun gitPath(url: String): String {
    val raw = url.trim().removeSuffix(".git")
    val afterHost = raw
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("ssh://git@")
        .removePrefix("ssh://")
        .removePrefix("git@")
        .replaceFirst(Regex("^[^/:]+:"), "")
        .substringAfter("/", missingDelimiterValue = raw)
    return afterHost.trim('/').ifBlank { raw }
}

fun prettyProvider(hostOrId: String): String {
    val key = hostOrId.lowercase()
    return when {
        key.contains("github") -> "GitHub"
        key.contains("gitlab") -> "GitLab"
        key.contains("bitbucket") -> "Bitbucket"
        key.contains("dev.azure") || key.contains("visualstudio") -> "Azure DevOps"
        else -> hostOrId
    }
}

fun AgentSummary.sortKey(): String = updatedAt ?: createdAt ?: ""

fun AgentSummary.isArchived(): Boolean {
    if (archived == true) return true
    return status?.equals("ARCHIVED", ignoreCase = true) == true
}

fun List<AgentSummary>.visibleInbox(
    showArchived: Boolean,
    hiddenIds: Set<String> = emptySet(),
    showHidden: Boolean = false,
): List<AgentSummary> {
    return filter { agent ->
        val archived = agent.isArchived()
        val hidden = agent.id in hiddenIds
        when {
            showArchived && showHidden -> archived || hidden
            showArchived -> archived && !hidden
            showHidden -> hidden
            else -> !archived && !hidden
        }
    }
}

fun markCloudArchived(
    all: List<AgentSummary>,
    active: List<AgentSummary>,
): List<AgentSummary> {
    val activeIds = active.map { it.id }.toHashSet()
    return all.map { agent ->
        if (agent.isArchived() || agent.id !in activeIds) {
            agent.copy(archived = true)
        } else {
            agent.copy(archived = false)
        }
    }
}

fun reconcileAgent(existing: AgentSummary?, incoming: AgentSummary): AgentSummary {
    if (existing == null) return incoming
    val archived = incoming.isArchived() || existing.isArchived()
    return incoming.copy(archived = if (archived) true else incoming.archived ?: existing.archived)
}

fun mergeInboxAgents(
    existing: List<AgentSummary>,
    incoming: List<AgentSummary>,
): List<AgentSummary> {
    if (incoming.isEmpty()) return existing
    if (existing.isEmpty()) return incoming.distinctBy { it.id }.sortedByDescending { it.sortKey() }
    val prev = existing.associateBy { it.id }
    val seen = HashSet<String>(existing.size + incoming.size)
    val out = ArrayList<AgentSummary>(existing.size + incoming.size)
    for (agent in incoming) {
        if (!seen.add(agent.id)) continue
        out += reconcileAgent(prev[agent.id], agent)
    }
    for (agent in existing) {
        if (seen.add(agent.id)) out += agent
    }
    return out.sortedByDescending { it.sortKey() }
}

fun foldAgentPages(pages: List<AgentListResponse>, maxPages: Int = pages.size): AgentListResponse {
    val byId = LinkedHashMap<String, AgentSummary>()
    var next: String? = null
    for ((index, page) in pages.withIndex()) {
        if (index >= maxPages) break
        page.entries().forEach { byId[it.id] = it }
        val cursor = page.nextCursor?.takeIf { it.isNotBlank() }
        next = cursor
        if (cursor == null || page.entries().isEmpty()) {
            next = null
            break
        }
    }
    if (pages.size < maxPages) next = null
    return AgentListResponse(items = byId.values.toList(), nextCursor = next)
}

fun AgentSummary.isWorking(): Boolean = isLiveStatus(status)

fun AgentDetail.isWorking(): Boolean = isLiveStatus(status)

data class ActiveEnv(
    val type: String,
    val name: String,
    val working: Int,
    val chats: Int,
    val latestId: String?,
    val latestStatus: String?,
) {
    fun composeName(): String? {
        if (type == "cloud" && name.equals("Cloud", ignoreCase = true)) return null
        return name
    }

    fun typeLabel(): String = when (type) {
        "cloud" -> "Cloud"
        "machine" -> "Machine"
        "pool" -> "Pool"
        else -> type.replaceFirstChar { it.uppercase() }
    }
}

fun List<AgentSummary>.activeEnvs(): List<ActiveEnv> {
    return groupBy { agent ->
        val type = agent.env?.type?.trim()?.lowercase().orEmpty().ifBlank { "cloud" }
        val name = agent.env?.name?.trim().orEmpty().ifBlank {
            when (type) {
                "cloud" -> "Cloud"
                "machine" -> "Machine"
                "pool" -> "Pool"
                else -> type
            }
        }
        type to name
    }.map { (key, group) ->
        val (type, name) = key
        val latest = group.maxByOrNull { it.sortKey() }
        ActiveEnv(
            type = type,
            name = name,
            working = group.count { it.isWorking() },
            chats = group.size,
            latestId = latest?.id,
            latestStatus = latest?.status,
        )
    }.sortedWith(
        compareByDescending<ActiveEnv> { it.working }
            .thenByDescending { it.chats }
            .thenBy { it.name.lowercase() },
    )
}

@Serializable
data class ArtifactItem(
    val path: String,
    val sizeBytes: Long? = null,
    val updatedAt: String? = null,
) {
    fun fileName(): String = path.substringAfterLast('/')
}

@Serializable
data class ArtifactListResponse(
    val items: List<ArtifactItem> = emptyList(),
)

@Serializable
data class ArtifactDownloadResponse(
    val url: String,
    val expiresAt: String? = null,
)

@Serializable
data class TokenUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    val cacheReadTokens: Long? = null,
    val totalTokens: Long? = null,
) {
    fun plus(other: TokenUsage): TokenUsage {
        return TokenUsage(
            inputTokens = (inputTokens ?: 0L) + (other.inputTokens ?: 0L),
            outputTokens = (outputTokens ?: 0L) + (other.outputTokens ?: 0L),
            cacheWriteTokens = (cacheWriteTokens ?: 0L) + (other.cacheWriteTokens ?: 0L),
            cacheReadTokens = (cacheReadTokens ?: 0L) + (other.cacheReadTokens ?: 0L),
            totalTokens = (totalTokens ?: 0L) + (other.totalTokens ?: 0L),
        )
    }
}

data class AgentUsageRow(
    val id: String,
    val name: String,
    val tokens: Long,
)

data class AccountOverview(
    val me: MeResponse? = null,
    val agentCount: Int = 0,
    val modelNames: List<String> = emptyList(),
    val repoCount: Int = 0,
    val computerCount: Int = 0,
    val computersOnline: Int = 0,
    val usage: TokenUsage = TokenUsage(),
    val sampledAgents: Int = 0,
    val top: List<AgentUsageRow> = emptyList(),
)

@Serializable
data class UsageRun(
    val id: String? = null,
    val usage: TokenUsage? = null,
)

@Serializable
data class AgentUsageResponse(
    val totalUsage: TokenUsage? = null,
    val runs: List<UsageRun> = emptyList(),
)

@Serializable
data class GitSnap(
    val agentId: String,
    val branch: String? = null,
    val prUrl: String? = null,
    val repoUrl: String? = null,
) {
    fun line(): String {
        return listOfNotNull(branch, prUrl?.let { "PR" }).joinToString(" · ")
    }
}

fun isTerminalStatus(status: String?): Boolean {
    val s = status?.uppercase().orEmpty()
    return s == "FINISHED" || s == "ERROR" || s == "CANCELLED" || s == "EXPIRED" || s == "ARCHIVED"
}

fun isLiveStatus(status: String?): Boolean {
    val s = status?.uppercase().orEmpty()
    return s.isNotEmpty() && !isTerminalStatus(s)
}

fun Run.isTerminal(): Boolean = isTerminalStatus(status)

fun Run.isActive(): Boolean = isLiveStatus(status)
