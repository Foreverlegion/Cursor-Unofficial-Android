package com.cursorandroid.app.data.repo

import com.cursorandroid.app.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object GithubAppIssues {
    const val REPO = "Foreverlegion/Cursor-Unofficial-Android"
    const val ASSIGNEE = "Foreverlegion"
    const val LEDGER_GIST_DESC = "cursor-android-install-ledger"
    const val LEDGER_GIST_ID = "fb8d79307fba4ba60aff5327a835abe3"
    const val LEDGER_FILE = "ledger.json"

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

    fun bakedToken(): String? = BuildConfig.APP_ISSUES_TOKEN.trim().takeIf { it.isNotEmpty() }

    fun findLedgerGist(token: String): GhGist? {
        val key = token.trim()
        if (key.isEmpty()) error("Missing GitHub token")
        val (code, raw) = call("GET", "/gists?per_page=50", token = key)
        if (code !in 200..299) error(httpError(code, raw))
        return json.decodeFromString<List<GhGist>>(raw)
            .filter { it.description == LEDGER_GIST_DESC && !it.id.isNullOrBlank() }
            .minByOrNull { it.created_at.orEmpty() }
    }

    fun getGist(token: String, id: String): GhGist {
        val key = token.trim()
        if (key.isEmpty()) error("Missing GitHub token")
        val (code, raw) = call("GET", "/gists/$id", token = key)
        if (code !in 200..299) error(httpError(code, raw))
        return json.decodeFromString<GhGist>(raw)
    }

    fun createLedgerGist(token: String, content: String): GhGist {
        val key = token.trim()
        if (key.isEmpty()) error("Missing GitHub token")
        val payload = json.encodeToString(
            CreateGist(
                description = LEDGER_GIST_DESC,
                public = false,
                files = mapOf(LEDGER_FILE to GistFile(content)),
            ),
        )
        val (code, raw) = call("POST", "/gists", payload, key)
        if (code !in 200..299) error(httpError(code, raw))
        return json.decodeFromString<GhGist>(raw)
    }

    fun updateLedgerGist(token: String, id: String, content: String): GhGist {
        val key = token.trim()
        if (key.isEmpty()) error("Missing GitHub token")
        val payload = json.encodeToString(
            PatchGist(files = mapOf(LEDGER_FILE to GistFile(content))),
        )
        val (code, raw) = call("PATCH", "/gists/$id", payload, key)
        if (code !in 200..299) error(httpError(code, raw))
        return json.decodeFromString<GhGist>(raw)
    }

    fun gistLedgerContent(gist: GhGist): String {
        return gist.files[LEDGER_FILE]?.content.orEmpty()
    }

    fun createIssue(
        token: String,
        title: String,
        body: String,
        assignees: List<String> = listOf(ASSIGNEE),
        labels: List<String> = emptyList(),
    ): GhIssue {
        val key = token.trim()
        if (key.isEmpty()) error("Missing GitHub token")
        val payload = json.encodeToString(
            CreateIssue(
                title = title,
                body = body,
                assignees = assignees,
                labels = labels.takeIf { it.isNotEmpty() },
            ),
        )
        val (code, raw) = call("POST", "/repos/$REPO/issues", payload, key)
        if (code == 422 && labels.isNotEmpty()) {
            return createIssue(key, title, body, assignees, emptyList())
        }
        if (code == 422 && assignees.isNotEmpty()) {
            return createIssue(key, title, body, emptyList(), emptyList())
        }
        if (code !in 200..299) error(httpError(code, raw))
        return json.decodeFromString<GhIssue>(raw)
    }

    internal fun httpError(code: Int, raw: String): String {
        val parsed = runCatching { json.decodeFromString<GhError>(raw) }.getOrNull()
        val detail = parsed?.message?.takeIf { it.isNotBlank() }
        return when (code) {
            401, 403 -> detail ?: "GitHub token was rejected."
            404 -> detail ?: "GitHub resource not found."
            422 -> detail ?: "GitHub could not create that issue."
            else -> detail ?: "GitHub HTTP $code"
        }
    }

    private fun call(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
    ): Pair<Int, String> {
        val builder = Request.Builder()
            .url("https://api.github.com$path")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", ClientOrigin.ID)
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        val request = when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post((body ?: "{}").toRequestBody(JSON)).build()
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(JSON)).build()
            else -> error("Unsupported GitHub method")
        }
        http.newCall(request).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    @Serializable
    data class GhIssue(
        val number: Int? = null,
        val title: String? = null,
        val body: String? = null,
        val html_url: String? = null,
    )

    @Serializable
    data class GhGist(
        val id: String? = null,
        val description: String? = null,
        val created_at: String? = null,
        val files: Map<String, GistFile> = emptyMap(),
    )

    @Serializable
    data class GistFile(
        val content: String? = null,
    )

    @Serializable
    private data class CreateGist(
        val description: String,
        val public: Boolean,
        val files: Map<String, GistFile>,
    )

    @Serializable
    private data class PatchGist(
        val files: Map<String, GistFile>,
    )

    @Serializable
    private data class CreateIssue(
        val title: String,
        val body: String,
        val assignees: List<String> = emptyList(),
        val labels: List<String>? = null,
    )

    @Serializable
    private data class GhError(
        val message: String? = null,
    )

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
