package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.gitHost
import com.cursorandroid.app.data.api.gitPath
import com.cursorandroid.app.data.api.repoKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

private val FORGE_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

enum class ForgeKind(
    val id: String,
    val label: String,
    val baseUrl: String,
    val apiUrl: String,
    val canCreate: Boolean,
    val needsUrls: Boolean,
) {
    GITHUB("github", "GitHub", "https://github.com", "https://api.github.com", true, false),
    GITHUB_ENTERPRISE("github_enterprise", "GitHub Enterprise", "", "", true, true),
    GITLAB("gitlab", "GitLab.com", "https://gitlab.com", "https://gitlab.com/api/v4", true, false),
    GITLAB_SELF("gitlab_self", "Self-hosted GitLab", "", "", true, true),
    BITBUCKET("bitbucket", "Bitbucket", "https://bitbucket.org", "https://api.bitbucket.org/2.0", true, false),
    GITEA("gitea", "Gitea / Forgejo", "", "", true, true),
    AZURE("azure", "Azure DevOps", "https://dev.azure.com", "https://dev.azure.com", false, false),
    ORIGIN("origin", "Cursor Origin", "", "", false, true),
    MANUAL("manual", "Manual add", "", "", false, true),
    ;

    companion object {
        fun fromId(id: String?): ForgeKind {
            return entries.firstOrNull { it.id == id } ?: MANUAL
        }
    }
}

@Serializable
data class ForgeConnection(
    val id: String = "",
    val provider: String = ForgeKind.GITHUB.id,
    val name: String = "",
    val baseUrl: String = "",
    val apiUrl: String = "",
    val authType: String = "token",
    val username: String = "",
    val token: String = "",
) {
    fun kind(): ForgeKind = ForgeKind.fromId(provider)

    fun displayName(): String = name.trim().ifBlank { kind().label }
}

@Serializable
data class RepoDefault(
    val repoUrl: String,
    val modelId: String = "",
    val modelParams: List<ModelParam> = emptyList(),
    val branch: String = "",
    val autoCreatePr: Boolean? = null,
    val skipReviewer: Boolean? = null,
    val mcpIds: List<String>? = null,
    val promptPrefix: String = "",
    val environmentId: String = "",
    val environmentName: String = "",
    val openFinishedPr: Boolean = false,
)

fun encodeForges(items: List<ForgeConnection>): String = FORGE_JSON.encodeToString(items)

fun decodeForges(raw: String?): List<ForgeConnection> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return emptyList()
    return runCatching { FORGE_JSON.decodeFromString<List<ForgeConnection>>(text) }.getOrDefault(emptyList())
}

fun encodeRepoDefaults(items: List<RepoDefault>): String = FORGE_JSON.encodeToString(items)

fun decodeRepoDefaults(raw: String?): List<RepoDefault> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return emptyList()
    return runCatching { FORGE_JSON.decodeFromString<List<RepoDefault>>(text) }.getOrDefault(emptyList())
}

fun publicGithubForge(token: String, id: String = "github"): ForgeConnection {
    return normalizedForge(
        ForgeConnection(
            id = id,
            provider = ForgeKind.GITHUB.id,
            name = ForgeKind.GITHUB.label,
            token = token,
        ),
    )
}

fun migrateForges(stored: List<ForgeConnection>, legacyToken: String?): List<ForgeConnection> {
    val token = legacyToken?.trim().orEmpty()
    if (token.isEmpty()) return stored
    if (stored.any { it.provider == ForgeKind.GITHUB.id && it.token.isNotBlank() }) return stored
    val filled = stored.map { forge ->
        if (forge.provider == ForgeKind.GITHUB.id && forge.token.isBlank()) forge.copy(token = token) else forge
    }
    if (filled.any { it.provider == ForgeKind.GITHUB.id }) return filled
    return listOf(publicGithubForge(token)) + stored
}

fun upsertForge(items: List<ForgeConnection>, forge: ForgeConnection): List<ForgeConnection> {
    val next = normalizedForge(forge).let { item ->
        item.copy(id = item.id.ifBlank { UUID.randomUUID().toString() })
    }
    val without = if (next.provider == ForgeKind.GITHUB.id) {
        items.filterNot { it.provider == ForgeKind.GITHUB.id || it.id == next.id }
    } else {
        items.filterNot { it.id == next.id }
    }
    return (without + next).take(MAX_FORGES)
}

fun upsertPublicGithub(items: List<ForgeConnection>, token: String?): List<ForgeConnection> {
    val key = token?.trim().orEmpty()
    if (key.isEmpty()) return items.filterNot { it.provider == ForgeKind.GITHUB.id }
    val existing = items.firstOrNull { it.provider == ForgeKind.GITHUB.id }
    return upsertForge(items, publicGithubForge(key, existing?.id ?: "github"))
}

fun normalizedForge(raw: ForgeConnection): ForgeConnection {
    val kind = raw.kind()
    val base = raw.baseUrl.trim().trimEnd('/').ifBlank { kind.baseUrl }
    val api = raw.apiUrl.trim().trimEnd('/').ifBlank {
        when (kind) {
            ForgeKind.GITHUB_ENTERPRISE -> if (base.isNotBlank()) "$base/api/v3" else ""
            ForgeKind.GITLAB_SELF -> if (base.isNotBlank()) "$base/api/v4" else ""
            ForgeKind.GITEA -> if (base.isNotBlank()) "$base/api/v1" else ""
            else -> kind.apiUrl
        }
    }
    return raw.copy(
        provider = kind.id,
        name = raw.name.trim().ifBlank { kind.label },
        baseUrl = base,
        apiUrl = api.trimEnd('/'),
        authType = "token",
        username = raw.username.trim(),
        token = raw.token.trim(),
    )
}

fun forgeForRepo(forges: List<ForgeConnection>, repoUrl: String): ForgeConnection? {
    val host = gitHost(repoUrl)
    if (host.isBlank()) return null
    return forges.map { normalizedForge(it) }
        .filter { it.token.isNotBlank() }
        .firstOrNull { forge ->
            val baseHost = gitHost(forge.baseUrl)
            when (forge.provider) {
                ForgeKind.GITHUB.id -> host == "github.com" || host == "www.github.com"
                ForgeKind.GITLAB.id -> host == "gitlab.com" || host == "www.gitlab.com"
                ForgeKind.BITBUCKET.id -> host == "bitbucket.org" || host == "www.bitbucket.org"
                else -> baseHost.isNotBlank() && (host == baseHost || host.endsWith(".$baseHost"))
            }
        }
}

fun ForgeKind.matchesProviderLabel(label: String): Boolean {
    return when (this) {
        ForgeKind.GITHUB, ForgeKind.GITHUB_ENTERPRISE -> label.equals("GitHub", ignoreCase = true)
        ForgeKind.GITLAB, ForgeKind.GITLAB_SELF -> label.equals("GitLab", ignoreCase = true)
        ForgeKind.BITBUCKET -> label.equals("Bitbucket", ignoreCase = true)
        ForgeKind.AZURE -> label.equals("Azure DevOps", ignoreCase = true)
        ForgeKind.ORIGIN -> label.equals("Origin", ignoreCase = true)
        ForgeKind.GITEA, ForgeKind.MANUAL -> false
    }
}

fun forgeForLabel(forges: List<ForgeConnection>, label: String): ForgeConnection? {
    val ready = forges.map { normalizedForge(it) }.filter { it.token.isNotBlank() }
    val byKind = ready.filter { it.kind().matchesProviderLabel(label) }
    val preferred = byKind.firstOrNull {
        it.provider == ForgeKind.GITHUB.id ||
            it.provider == ForgeKind.GITLAB.id ||
            it.provider == ForgeKind.BITBUCKET.id ||
            it.provider == ForgeKind.AZURE.id ||
            it.provider == ForgeKind.ORIGIN.id
    } ?: byKind.firstOrNull()
    if (preferred != null) return preferred
    return ready.firstOrNull { gitHost(it.baseUrl).equals(label, ignoreCase = true) }
}

fun findRepoDefault(items: List<RepoDefault>, url: String): RepoDefault? {
    val key = repoKey(url)
    if (key.isEmpty()) return null
    return items.firstOrNull { repoKey(it.repoUrl) == key }
}

fun upsertRepoDefault(items: List<RepoDefault>, item: RepoDefault): List<RepoDefault> {
    val url = item.repoUrl.trim()
    val key = repoKey(url)
    if (key.isEmpty()) return items
    val next = item.copy(
        repoUrl = url,
        promptPrefix = item.promptPrefix.trim().take(MAX_PREFIX),
        branch = item.branch.trim(),
        modelId = item.modelId.trim(),
        environmentId = item.environmentId.trim(),
        environmentName = item.environmentName.trim(),
    )
    return (items.filterNot { repoKey(it.repoUrl) == key } + next).take(MAX_REPO_DEFAULTS)
}

fun prefixPrompt(prefix: String, prompt: String): String {
    val head = prefix.trim()
    if (head.isEmpty()) return prompt
    val body = prompt.trim()
    if (body.isEmpty()) return head
    if (body.startsWith(head)) return prompt
    return "$head\n\n$body"
}

fun mcpServersFor(items: List<StoredMcpServer>, ids: List<String>?): List<com.cursorandroid.app.data.api.McpServer>? {
    if (ids == null) return storedMcpsToApi(items)
    val wanted = ids.toSet()
    return storedMcpsToApi(items.filter { it.id in wanted })
}

fun knownRepoUrls(catalog: List<String>, snaps: List<String>, agents: List<String>): List<String> {
    return (catalog + snaps + agents)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { repoKey(it) }
        .sortedBy { gitPath(it).lowercase() }
}

data class ForgeCall(
    val url: String,
    val headers: Map<String, String>,
    val method: String = "GET",
    val body: String? = null,
)

fun authHeaders(forge: ForgeConnection): Map<String, String> {
    val token = forge.token.trim()
    if (token.isEmpty()) return emptyMap()
    val headers = linkedMapOf("Accept" to "application/json", "User-Agent" to ClientOrigin.ID)
    when (forge.provider) {
        ForgeKind.GITLAB.id, ForgeKind.GITLAB_SELF.id -> headers["PRIVATE-TOKEN"] = token
        ForgeKind.BITBUCKET.id -> {
            headers["Authorization"] = if (forge.username.isNotBlank()) basic(forge.username, token) else "Bearer $token"
        }
        ForgeKind.GITEA.id -> headers["Authorization"] = "token $token"
        ForgeKind.AZURE.id -> headers["Authorization"] = basic("", token)
        else -> headers["Authorization"] = "Bearer $token"
    }
    return headers
}

fun testCall(forge: ForgeConnection): ForgeCall? {
    val item = normalizedForge(forge)
    if (item.token.isBlank()) return null
    val url = when (item.provider) {
        ForgeKind.AZURE.id -> {
            val org = item.username.ifBlank { gitPath(item.baseUrl).substringBefore('/') }
            if (org.isBlank()) return null
            "https://dev.azure.com/$org/_apis/projects?api-version=7.1&\$top=1"
        }
        ForgeKind.ORIGIN.id, ForgeKind.MANUAL.id -> item.apiUrl.takeIf { it.isNotBlank() } ?: return null
        else -> {
            val api = item.apiUrl.takeIf { it.isNotBlank() } ?: return null
            "$api/user"
        }
    }
    if (!callAllowed(item, url)) return null
    return ForgeCall(url, authHeaders(item))
}

fun branchCall(forge: ForgeConnection, repoUrl: String): ForgeCall? {
    val item = normalizedForge(forge)
    if (item.token.isBlank()) return null
    val path = gitPath(repoUrl)
    if (path.isBlank()) return null
    val url = when (item.provider) {
        ForgeKind.GITHUB.id, ForgeKind.GITHUB_ENTERPRISE.id ->
            "${item.apiUrl}/repos/$path/branches?per_page=100"
        ForgeKind.GITLAB.id, ForgeKind.GITLAB_SELF.id -> {
            val project = URLEncoder.encode(path, Charsets.UTF_8.name())
            "${item.apiUrl}/projects/$project/repository/branches?per_page=100"
        }
        ForgeKind.BITBUCKET.id ->
            "${item.apiUrl}/repositories/$path/refs/branches?pagelen=100"
        ForgeKind.GITEA.id ->
            "${item.apiUrl}/repos/$path/branches?limit=100"
        ForgeKind.AZURE.id -> azureRefsUrl(repoUrl)
        else -> return null
    } ?: return null
    if (!callAllowed(item, url)) return null
    return ForgeCall(url, authHeaders(item))
}

fun createCall(
    forge: ForgeConnection,
    name: String,
    privateRepo: Boolean,
    description: String?,
): ForgeCall? {
    val item = normalizedForge(forge)
    if (!item.kind().canCreate || item.token.isBlank()) return null
    val repoName = GithubRepos.sanitizeName(name)
    if (repoName.isEmpty()) return null
    val (url, body) = when (item.provider) {
        ForgeKind.GITHUB.id, ForgeKind.GITHUB_ENTERPRISE.id ->
            "${item.apiUrl}/user/repos" to githubCreateBody(repoName, privateRepo, description)
        ForgeKind.GITLAB.id, ForgeKind.GITLAB_SELF.id ->
            "${item.apiUrl}/projects" to gitlabCreateBody(repoName, privateRepo, description)
        ForgeKind.GITEA.id ->
            "${item.apiUrl}/user/repos" to giteaCreateBody(repoName, privateRepo, description)
        ForgeKind.BITBUCKET.id -> {
            val workspace = item.username
            if (workspace.isBlank()) return null
            "${item.apiUrl}/repositories/$workspace/$repoName" to bitbucketCreateBody(repoName, privateRepo, description)
        }
        else -> return null
    }
    if (!callAllowed(item, url)) return null
    return ForgeCall(url, authHeaders(item), method = "POST", body = body)
}

fun callAllowed(forge: ForgeConnection, url: String): Boolean {
    val item = normalizedForge(forge)
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank()) return false
    val allowed = listOf(gitHost(item.apiUrl), gitHost(item.baseUrl))
        .filter { it.isNotBlank() }
        .toMutableSet()
    when (item.provider) {
        ForgeKind.GITHUB.id -> allowed += "api.github.com"
        ForgeKind.GITLAB.id -> allowed += "gitlab.com"
        ForgeKind.BITBUCKET.id -> allowed += "api.bitbucket.org"
        ForgeKind.AZURE.id -> allowed += "dev.azure.com"
    }
    return host in allowed
}

fun parseBranchNames(provider: String, body: String): List<String> {
    val root = runCatching { FORGE_JSON.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
    val rows = when (root) {
        is JsonArray -> root
        is JsonObject -> (root["values"] as? JsonArray) ?: (root["value"] as? JsonArray) ?: return emptyList()
        else -> return emptyList()
    }
    return rows.mapNotNull { row ->
        val obj = row as? JsonObject ?: return@mapNotNull null
        val name = obj.string("name") ?: return@mapNotNull null
        name.removePrefix("refs/heads/").takeIf { it.isNotBlank() }
    }.distinct()
}

fun parseForgeLogin(body: String): String? {
    val obj = runCatching { FORGE_JSON.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    return obj.string("login")
        ?: obj.string("username")
        ?: obj.string("name")
        ?: obj.string("displayName")
}

fun parseCreatedRepo(provider: String, body: String, fallbackName: String): com.cursorandroid.app.data.api.RepositoryItem? {
    val obj = runCatching { FORGE_JSON.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    val url = obj.string("html_url")
        ?: obj.string("http_url_to_repo")
        ?: obj.obj("links")?.obj("html")?.string("href")
        ?: return null
    if (!url.startsWith("https://")) return null
    val branch = obj.string("default_branch")
        ?: obj.obj("mainbranch")?.string("name")
        ?: "main"
    val name = obj.string("full_name")
        ?: obj.string("path_with_namespace")
        ?: fallbackName
    val providerId = when (provider) {
        ForgeKind.GITHUB.id, ForgeKind.GITHUB_ENTERPRISE.id -> "github"
        ForgeKind.GITLAB.id, ForgeKind.GITLAB_SELF.id -> "gitlab"
        ForgeKind.BITBUCKET.id -> "bitbucket"
        ForgeKind.GITEA.id -> "gitea"
        else -> provider
    }
    return com.cursorandroid.app.data.api.RepositoryItem(
        url = url,
        provider = providerId,
        defaultBranch = branch.removePrefix("refs/heads/"),
        name = name,
    )
}

private fun azureRefsUrl(repoUrl: String): String? {
    val path = gitPath(repoUrl)
    val parts = path.split('/').filter { it.isNotBlank() }
    val git = parts.indexOf("_git")
    if (git < 2 || git + 1 >= parts.size) return null
    val org = parts[0]
    val project = parts[1]
    val repo = parts[git + 1]
    return "https://dev.azure.com/$org/$project/_apis/git/repositories/$repo/refs?filter=heads/&api-version=7.1"
}

private fun githubCreateBody(name: String, privateRepo: Boolean, description: String?): String {
    return jsonBody(name, privateRepo, description, privateKey = "private", initKey = "auto_init")
}

private fun gitlabCreateBody(name: String, privateRepo: Boolean, description: String?): String {
    return buildJsonObject {
        put("name", name)
        put("visibility", if (privateRepo) "private" else "public")
        put("initialize_with_readme", true)
        description?.trim()?.takeIf { it.isNotEmpty() }?.let { put("description", it) }
    }.toString()
}

private fun giteaCreateBody(name: String, privateRepo: Boolean, description: String?): String {
    return jsonBody(name, privateRepo, description, privateKey = "private", initKey = "auto_init")
}

private fun bitbucketCreateBody(name: String, privateRepo: Boolean, description: String?): String {
    return buildJsonObject {
        put("scm", "git")
        put("name", name)
        put("is_private", privateRepo)
        description?.trim()?.takeIf { it.isNotEmpty() }?.let { put("description", it) }
    }.toString()
}

private fun jsonBody(
    name: String,
    privateRepo: Boolean,
    description: String?,
    privateKey: String,
    initKey: String,
): String {
    return buildJsonObject {
        put("name", name)
        put(privateKey, privateRepo)
        put(initKey, true)
        description?.trim()?.takeIf { it.isNotEmpty() }?.let { put("description", it) }
    }.toString()
}

private fun basic(user: String, token: String): String {
    val raw = "$user:$token".toByteArray(Charsets.UTF_8)
    return "Basic " + Base64.getEncoder().encodeToString(raw)
}

private fun JsonObject.string(key: String): String? {
    return get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

private const val MAX_FORGES = 20
private const val MAX_REPO_DEFAULTS = 50
private const val MAX_PREFIX = 2000
