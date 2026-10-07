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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object GithubAppIssues {
    const val REPO = "Foreverlegion/Cursor-Unofficial-Android"
    const val ASSIGNEE = "Foreverlegion"

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
        if (code == 422 && assignees.isNotEmpty()) {
            return createIssue(key, title, body, emptyList(), labels)
        }
        if (code == 422 && labels.isNotEmpty()) {
            return createIssue(key, title, body, emptyList(), emptyList())
        }
        if (code !in 200..299) error(httpError(code, raw))
        return json.decodeFromString<GhIssue>(raw)
    }

    fun ensureLabel(token: String, name: String, color: String) {
        val payload = json.encodeToString(CreateLabel(name = name, color = color))
        val (code, _) = call("POST", "/repos/$REPO/labels", payload, token)
        if (code in 200..299 || code == 422) return
    }

    fun addLabel(token: String, number: Int, name: String, color: String): Boolean {
        ensureLabel(token, name, color)
        val payload = json.encodeToString(LabelNames(labels = listOf(name)))
        val (code, _) = call("POST", "/repos/$REPO/issues/$number/labels", payload, token)
        return code in 200..299
    }

    fun getIssue(token: String, number: Int): GhIssue? {
        val (code, raw) = call("GET", "/repos/$REPO/issues/$number", null, token)
        if (code !in 200..299) return null
        return runCatching { json.decodeFromString<GhIssue>(raw) }.getOrNull()
    }

    fun createComment(token: String, number: Int, body: String): Boolean {
        val text = body.trim()
        if (text.isEmpty()) return false
        val payload = json.encodeToString(CreateComment(body = text))
        val (code, _) = call("POST", "/repos/$REPO/issues/$number/comments", payload, token)
        return code in 200..299
    }

    fun listComments(token: String, number: Int): List<GhComment>? {
        val (code, raw) = call("GET", "/repos/$REPO/issues/$number/comments?per_page=100", null, token)
        if (code !in 200..299) return null
        return runCatching { json.decodeFromString<List<GhComment>>(raw) }.getOrNull()
    }

    fun listLabeled(token: String, label: String): List<GhIssue> {
        val encoded = URLEncoder.encode(label, Charsets.UTF_8.name())
        val path = "/repos/$REPO/issues?labels=$encoded&state=all&per_page=30&sort=created&direction=desc"
        val (code, raw) = call("GET", path, null, token)
        if (code !in 200..299) return emptyList()
        val items = runCatching { json.decodeFromString<List<GhIssue>>(raw) }.getOrNull() ?: return emptyList()
        return items.filter { it.pull_request == null && it.number != null }
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
        val labels: List<GhLabel> = emptyList(),
        val pull_request: GhPull? = null,
    )

    @Serializable
    data class GhLabel(val name: String? = null)

    @Serializable
    data class GhPull(val url: String? = null)

    @Serializable
    data class GhComment(
        val id: Long = 0,
        val body: String? = null,
    )

    @Serializable
    private data class CreateComment(
        val body: String,
    )

    @Serializable
    private data class CreateIssue(
        val title: String,
        val body: String,
        val assignees: List<String> = emptyList(),
        val labels: List<String>? = null,
    )

    @Serializable
    private data class CreateLabel(
        val name: String,
        val color: String,
    )

    @Serializable
    private data class LabelNames(
        val labels: List<String>,
    )

    @Serializable
    private data class GhError(
        val message: String? = null,
    )

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
