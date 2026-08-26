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
import java.util.concurrent.TimeUnit

object GithubRepos {
    private val json = Json { ignoreUnknownKeys = true }
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
        val body = json.encodeToString(
            CreateBody(
                name = repoName,
                private = privateRepo,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                auto_init = true,
            ),
        )
        val request = Request.Builder()
            .url("https://api.github.com/user/repos")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $key")
            .header("User-Agent", ClientOrigin.ID)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(httpError(response.code, raw))
            val created = json.decodeFromString<CreatedRepo>(raw)
            val url = SafeLinks.githubHttps(created.html_url)?.toString()
                ?: error("GitHub did not return an HTTPS repo URL")
            return RepositoryItem(
                url = url,
                provider = "github",
                defaultBranch = created.default_branch?.takeIf { it.isNotBlank() } ?: "main",
                name = created.full_name?.takeIf { it.isNotBlank() } ?: repoName,
            )
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
        val private: Boolean = true,
        val description: String? = null,
        val auto_init: Boolean = true,
    )

    @Serializable
    private data class CreatedRepo(
        val html_url: String? = null,
        val full_name: String? = null,
        val default_branch: String? = null,
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

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
