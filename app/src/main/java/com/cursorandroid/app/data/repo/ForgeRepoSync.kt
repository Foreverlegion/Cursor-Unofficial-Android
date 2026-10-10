package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.api.repoKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest

const val FORGE_SYNC_MAX_PAGES = 50
const val FORGE_SYNC_BACKGROUND_MS = 6 * 60 * 60 * 1000L
const val FORGE_SYNC_START_MS = 10 * 60 * 1000L

@Serializable
data class ForgeSyncStatus(
    val forgeId: String,
    val attemptedAt: Long,
    val syncedAt: Long = 0,
    val repoCount: Int = 0,
    val error: String? = null,
    val authFailed: Boolean = false,
    val truncated: Boolean = false,
    val fingerprint: String = "",
)

data class ForgeFetch(
    val repos: List<RepositoryItem>,
    val error: String? = null,
    val authFailed: Boolean = false,
    val truncated: Boolean = false,
)

data class RepoGroup(val name: String, val repos: List<RepositoryItem>)

/** Identifies the credentials a sync ran with without storing the token itself. */
fun forgeFingerprint(forge: ForgeConnection): String {
    val item = normalizedForge(forge)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${item.provider}\n${item.apiUrl}\n${item.token}".toByteArray(Charsets.UTF_8))
    return digest.take(8).joinToString("") { "%02x".format(it) }
}

fun forgeSyncSupported(forge: ForgeConnection): Boolean = forgeSupportsRepoList(forge)

fun forgeSyncError(forge: ForgeConnection, code: Int, headers: Map<String, String>): String {
    val name = normalizedForge(forge).displayName()
    return when {
        code == 0 -> "Couldn't reach $name."
        code == 401 -> "$name rejected the token (401). Check it in Settings > Forges."
        code == 429 || (code == 403 && headers["x-ratelimit-remaining"] == "0") ->
            "$name rate limit reached ($code). It will retry later."
        code == 403 && headers.containsKey("x-github-sso") ->
            "$name needs SSO authorization for this token (403)."
        code == 403 -> "$name denied access (403). Check the token's scopes in Settings > Forges."
        else -> "$name returned $code."
    }
}

fun forgeHasNextPage(headers: Map<String, String>, rows: Int, pageSize: Int): Boolean {
    headers["x-next-page"]?.let { return it.isNotBlank() }
    headers["link"]?.let { return it.contains("rel=\"next\"") }
    return rows >= pageSize
}

fun fetchForgeRepos(
    forge: ForgeConnection,
    transport: ForgeTransport,
    maxPages: Int = FORGE_SYNC_MAX_PAGES,
): ForgeFetch {
    val item = normalizedForge(forge)
    if (item.token.isBlank()) return ForgeFetch(emptyList(), error = "Add a token.")
    if (!forgeSyncSupported(item)) {
        return ForgeFetch(emptyList(), error = "Repo sync isn't available for ${item.kind().label}.")
    }
    val size = repoPageSize(item.provider)
    val found = ArrayList<RepositoryItem>()
    var page = 1
    while (page <= maxPages) {
        val call = repoListCall(item, "", page)
            ?: return ForgeFetch(found, error = "Add an API URL for this forge.")
        val response = transport.execute(call)
        if (response.code !in 200..299) {
            return ForgeFetch(
                found,
                error = forgeSyncError(item, response.code, response.headers),
                authFailed = response.code == 401 || (response.code == 403 && !isRateLimited(response.headers)),
            )
        }
        val rows = parseForgeRepos(item, response.body)
        found += rows
        val more = forgeHasNextPage(response.headers, rowCount(response.body, rows.size), size)
        if (!more) return ForgeFetch(mergeRepos(found))
        page++
    }
    return ForgeFetch(mergeRepos(found), truncated = true)
}

private fun isRateLimited(headers: Map<String, String>): Boolean = headers["x-ratelimit-remaining"] == "0"

// Rows the page really carried, so repos dropped by the parser (no HTTPS URL) do not end paging early.
private fun rowCount(body: String, parsed: Int): Int {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return parsed
    val rows = when (root) {
        is JsonArray -> root
        is JsonObject ->
            (root["values"] as? JsonArray)
                ?: (root["data"] as? JsonArray)
        else -> null
    }
    return rows?.size ?: parsed
}

fun ForgeSyncStatus?.syncLine(nowMs: Long): String {
    if (this == null) return "Not synced yet"
    val repos = if (repoCount == 1) "1 repo" else "$repoCount repos"
    val head = when {
        syncedAt > 0 -> "Last synced ${relativeSeen(syncedAt, nowMs)} · $repos" +
            if (truncated) " (first ${FORGE_SYNC_MAX_PAGES * FORGE_REPO_PAGE_SIZE})" else ""
        repoCount > 0 -> "Partly synced · $repos"
        else -> "Not synced yet"
    }
    return if (error.isNullOrBlank()) head else "$head · $error"
}

/** Repos grouped by the forge that listed them. Repos no forge listed (Cursor's own list) come last. */
fun groupReposByForge(
    repos: List<RepositoryItem>,
    forges: List<ForgeConnection>,
    byForge: Map<String, List<RepositoryItem>>,
): List<RepoGroup> {
    val owner = HashMap<String, String>()
    forges.forEach { forge ->
        byForge[forge.id].orEmpty().forEach { owner.putIfAbsent(repoKey(it.url), forge.id) }
    }
    val names = forges.associate { it.id to normalizedForge(it).displayName() }
    val grouped = repos.groupBy { owner[repoKey(it.url)] }
    val out = ArrayList<RepoGroup>()
    forges.forEach { forge ->
        grouped[forge.id]?.takeIf { it.isNotEmpty() }?.let { out += RepoGroup(names.getValue(forge.id), it) }
    }
    grouped[null]?.takeIf { it.isNotEmpty() }?.let { out += RepoGroup("Cursor", it) }
    return out
}
