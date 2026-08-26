package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

object GithubRepos {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val host = chain.request().url.host
            if (!SafeLinks.isGithubHost(host)) {
                throw IOException("Unexpected host $host")
            }
            chain.proceed(chain.request())
        }
        .build()

    fun sanitizeName(raw: String): String {
        return raw.trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^A-Za-z0-9._-]"), "")
            .trim('.')
            .take(100)
    }

    fun create(
        token: String,
        name: String,
        privateRepo: Boolean,
        description: String?,
    ): RepositoryItem {
        val key = token.trim()
        if (key.isEmpty()) error("Add a GitHub token in Settings > Connections.")
        val repoName = sanitizeName(name)
        if (repoName.isEmpty()) error("Repo name is empty")
        val request = Request.Builder()
            .url("https://api.github.com/user/repos")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $key")
            .header("User-Agent", ClientOrigin.ID)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(createBodyJson(repoName, privateRepo, description).toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(httpError(response.code, raw))
            val created = json.decodeFromString<CreatedRepo>(raw)
            val url = SafeLinks.githubHttps(created.html_url)?.toString()
                ?: error("GitHub did not return an HTTPS repo URL")
            val fullName = repoFullName(created.full_name)
                ?: repoFullNameFromUrl(url)
                ?: error("GitHub did not return a repo name")
            val wanted = created.default_branch?.takeIf { it.isNotBlank() } ?: "main"
            val branch = ensureDefaultBranch(key, fullName, wanted, repoName)
            return RepositoryItem(
                url = url,
                provider = "github",
                defaultBranch = branch,
                name = created.full_name?.takeIf { it.isNotBlank() } ?: fullName,
            )
        }
    }

    internal fun createBodyJson(
        name: String,
        privateRepo: Boolean,
        description: String?,
    ): String {
        return json.encodeToString(
            CreateBody(
                name = name,
                private = privateRepo,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                auto_init = true,
            ),
        )
    }

    internal fun repoFullName(raw: String?): String? {
        val parts = raw?.trim()?.trim('/')?.split('/').orEmpty()
        if (parts.size != 2) return null
        val owner = parts[0]
        val repo = parts[1]
        if (!OWNER.matches(owner) || !REPO.matches(repo)) return null
        return "$owner/$repo"
    }

    internal fun repoFullNameFromUrl(url: String): String? {
        val uri = SafeLinks.githubHttps(url) ?: return null
        val parts = uri.path.orEmpty().trim('/').removeSuffix(".git").split('/')
        if (parts.size < 2) return null
        return repoFullName("${parts[0]}/${parts[1]}")
    }

    private fun ensureDefaultBranch(
        token: String,
        fullName: String,
        wanted: String,
        repoName: String,
    ): String {
        waitForBranch(token, fullName, wanted, tries = 4)?.let { return it }
        seedReadme(token, fullName, wanted, repoName)
        return waitForBranch(token, fullName, wanted, tries = 8)
            ?: error("GitHub created the repo but branch $wanted does not exist")
    }

    private fun waitForBranch(
        token: String,
        fullName: String,
        wanted: String,
        tries: Int,
    ): String? {
        repeat(tries) { i ->
            if (i > 0) Thread.sleep(250L * i)
            peekBranch(token, fullName, wanted)?.let { return it }
            listBranchNames(token, fullName).firstOrNull()?.let { return it }
        }
        return null
    }

    private fun peekBranch(token: String, fullName: String, branch: String): String? {
        val encoded = URLEncoder.encode(branch, Charsets.UTF_8.name()).replace("+", "%20")
        val (code, raw) = gh(token, "GET", "/repos/$fullName/branches/$encoded")
        if (code !in 200..299) return null
        val parsed = runCatching { json.decodeFromString<GhBranch>(raw) }.getOrNull()
        return parsed?.name?.takeIf { it.isNotBlank() }
    }

    private fun listBranchNames(token: String, fullName: String): List<String> {
        val (code, raw) = gh(token, "GET", "/repos/$fullName/branches?per_page=20")
        if (code !in 200..299) return emptyList()
        val parsed = runCatching { json.decodeFromString<List<GhBranch>>(raw) }.getOrNull().orEmpty()
        return parsed.mapNotNull { it.name?.takeIf(String::isNotBlank) }
    }

    private fun seedReadme(
        token: String,
        fullName: String,
        branch: String,
        repoName: String,
    ) {
        val body = json.encodeToString(
            PutFile(
                message = "Initial commit",
                content = Base64.getEncoder().encodeToString("# $repoName\n".toByteArray()),
                branch = branch,
            ),
        )
        val (code, raw) = gh(token, "PUT", "/repos/$fullName/contents/README.md", body)
        if (code in 200..299) return
        if (code == 422) return
        error(httpError(code, raw))
    }

    private fun gh(
        token: String,
        method: String,
        path: String,
        body: String? = null,
    ): Pair<Int, String> {
        val builder = Request.Builder()
            .url("https://api.github.com$path")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", ClientOrigin.ID)
            .header("X-GitHub-Api-Version", "2022-11-28")
        val request = when (method) {
            "GET" -> builder.get().build()
            "PUT" -> builder.put((body ?: "{}").toRequestBody(JSON)).build()
            else -> error("Unsupported GitHub method")
        }
        http.newCall(request).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    private fun httpError(code: Int, raw: String): String {
        val parsed = runCatching { json.decodeFromString<GhError>(raw) }.getOrNull()
        val detail = parsed?.errors
            ?.mapNotNull { it.message?.takeIf(String::isNotBlank) }
            ?.firstOrNull()
            ?: parsed?.message?.takeIf { it.isNotBlank() }
        return when (code) {
            401, 403 -> detail ?: "GitHub token was rejected. It needs repo access."
            422 -> detail ?: "GitHub could not create that repo."
            else -> detail ?: "GitHub HTTP $code"
        }
    }

    @Serializable
    private data class CreateBody(
        val name: String,
        val private: Boolean,
        val description: String? = null,
        val auto_init: Boolean,
    )

    @Serializable
    private data class CreatedRepo(
        val html_url: String? = null,
        val full_name: String? = null,
        val default_branch: String? = null,
    )

    @Serializable
    private data class GhBranch(
        val name: String? = null,
    )

    @Serializable
    private data class PutFile(
        val message: String,
        val content: String,
        val branch: String,
    )

    @Serializable
    private data class GhError(
        val message: String? = null,
        val errors: List<GhField> = emptyList(),
    )

    @Serializable
    private data class GhField(
        val message: String? = null,
    )

    private val OWNER = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$")
    private val REPO = Regex("^[A-Za-z0-9._-]{1,100}$")
    private val JSON = "application/json; charset=utf-8".toMediaType()
}
