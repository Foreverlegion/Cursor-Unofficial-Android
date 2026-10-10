package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.api.gitHost
import com.cursorandroid.app.data.api.listedProviders
import com.cursorandroid.app.data.api.prettyProvider
import com.cursorandroid.app.data.api.providerRank
import com.cursorandroid.app.data.api.repoKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

const val FORGE_REPO_PAGE_SIZE = 100
private const val GITEA_PAGE_SIZE = 50

fun repoPageSize(provider: String): Int = if (provider == ForgeKind.GITEA.id) GITEA_PAGE_SIZE else FORGE_REPO_PAGE_SIZE

data class ForgeRepoPage(val repos: List<RepositoryItem>, val hasMore: Boolean)

private val REPO_JSON = Json { ignoreUnknownKeys = true }

fun ForgeConnection.sourceLabel(): String? {
    val item = normalizedForge(this)
    if (item.token.isBlank()) return null
    return when (item.kind()) {
        ForgeKind.GITHUB, ForgeKind.GITHUB_ENTERPRISE -> "GitHub"
        ForgeKind.GITLAB, ForgeKind.GITLAB_SELF -> "GitLab"
        ForgeKind.BITBUCKET -> "Bitbucket"
        ForgeKind.AZURE -> "Azure DevOps"
        ForgeKind.ORIGIN -> "Origin"
        ForgeKind.GITEA, ForgeKind.MANUAL ->
            gitHost(item.baseUrl).takeIf { it.isNotBlank() }?.let { prettyProvider(it) }
    }
}

fun sourceLabels(repos: List<RepositoryItem>, forges: List<ForgeConnection>): List<String> {
    return (listedProviders(repos) + forges.mapNotNull { it.sourceLabel() })
        .distinct()
        .sortedWith(compareBy<String> { providerRank(it) }.thenBy { it.lowercase() })
}

fun mergeRepos(vararg lists: List<RepositoryItem>): List<RepositoryItem> {
    return lists.flatMap { it }.distinctBy { repoKey(it.url) }
}

fun repoListCall(forge: ForgeConnection, query: String, page: Int): ForgeCall? {
    val item = normalizedForge(forge)
    if (item.token.isBlank() || item.apiUrl.isBlank()) return null
    val safePage = page.coerceAtLeast(1)
    val search = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    val url = when (item.provider) {
        ForgeKind.GITLAB.id, ForgeKind.GITLAB_SELF.id -> buildString {
            append("${item.apiUrl}/projects?membership=true&simple=true&order_by=last_activity_at")
            append("&per_page=$FORGE_REPO_PAGE_SIZE&page=$safePage")
            if (search.isNotEmpty()) append("&search=$search")
        }
        ForgeKind.GITHUB.id, ForgeKind.GITHUB_ENTERPRISE.id ->
            "${item.apiUrl}/user/repos?sort=pushed&per_page=$FORGE_REPO_PAGE_SIZE&page=$safePage"
        ForgeKind.BITBUCKET.id ->
            "${item.apiUrl}/repositories?role=member&pagelen=$FORGE_REPO_PAGE_SIZE&page=$safePage"
        ForgeKind.GITEA.id -> buildString {
            append("${item.apiUrl}/repos/search?limit=$GITEA_PAGE_SIZE&page=$safePage")
            if (search.isNotEmpty()) append("&q=$search")
        }
        else -> return null
    }
    if (!callAllowed(item, url)) return null
    return ForgeCall(url, authHeaders(item))
}

fun forgeSupportsRepoList(forge: ForgeConnection): Boolean = repoListCall(forge, "", 1) != null

fun parseForgeRepos(forge: ForgeConnection, body: String): List<RepositoryItem> {
    val item = normalizedForge(forge)
    val root = runCatching { REPO_JSON.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
    val rows = when (root) {
        is JsonArray -> root
        is JsonObject -> (root["values"] as? JsonArray) ?: (root["data"] as? JsonArray) ?: return emptyList()
        else -> return emptyList()
    }
    val providerId = when (item.provider) {
        ForgeKind.GITHUB.id, ForgeKind.GITHUB_ENTERPRISE.id -> "github"
        ForgeKind.GITLAB.id, ForgeKind.GITLAB_SELF.id -> "gitlab"
        ForgeKind.BITBUCKET.id -> "bitbucket"
        else -> gitHost(item.baseUrl)
    }
    return rows.mapNotNull { row ->
        val obj = row as? JsonObject ?: return@mapNotNull null
        val url = obj.text("http_url_to_repo")
            ?: obj.text("clone_url")
            ?: obj.text("html_url")
            ?: obj.text("web_url")
            ?: (obj["links"] as? JsonObject)?.let { links ->
                (links["html"] as? JsonObject)?.text("href")
            }
            ?: return@mapNotNull null
        if (!url.startsWith("https://")) return@mapNotNull null
        val name = obj.text("path_with_namespace") ?: obj.text("full_name") ?: obj.text("name")
        val branch = obj.text("default_branch")
            ?: (obj["mainbranch"] as? JsonObject)?.text("name")
        RepositoryItem(url = url, provider = providerId, defaultBranch = branch, name = name)
    }.distinctBy { repoKey(it.url) }
}

fun filterRepos(repos: List<RepositoryItem>, query: String): List<RepositoryItem> {
    val needle = query.trim()
    if (needle.isEmpty()) return repos
    return repos.filter {
        it.displayName().contains(needle, ignoreCase = true) || it.url.contains(needle, ignoreCase = true)
    }
}

private fun JsonObject.text(key: String): String? {
    return (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
