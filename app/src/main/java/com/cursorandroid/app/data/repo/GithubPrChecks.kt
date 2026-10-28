package com.cursorandroid.app.data.repo

import com.cursorandroid.app.ui.status.PrCheckClassifier
import com.cursorandroid.app.ui.status.PrCheckChipKind
import com.cursorandroid.app.ui.status.PrCheckRunView
import com.cursorandroid.app.ui.status.PrCheckSnap
import com.cursorandroid.app.ui.status.PrCheckSummary
import com.cursorandroid.app.ui.status.PrCheckTestDetail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GithubPullRef(
    val owner: String,
    val repo: String,
    val number: Int,
)

object GithubPrChecks {
    private const val ANNOTATION_BUDGET = 4

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
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

    private val pullPath = Regex("""^/?([^/]+)/([^/]+)/pull/(\d+)""", RegexOption.IGNORE_CASE)

    fun parsePullUrl(url: String): GithubPullRef? {
        val uri = SafeLinks.githubHttps(url.trim()) ?: return null
        val path = uri.path.orEmpty().trim('/')
        val match = pullPath.matchEntire(path) ?: return null
        val number = match.groupValues[3].toIntOrNull() ?: return null
        return GithubPullRef(match.groupValues[1], match.groupValues[2], number)
    }

    fun fetchSnap(token: String, apiBase: String, prUrl: String): PrCheckSnap? {
        val ref = parsePullUrl(prUrl) ?: return null
        val key = token.trim()
        if (key.isEmpty()) return null
        val base = apiBase.trim().trimEnd('/').ifBlank { "https://api.github.com" }
        val pullPath = "/repos/${ref.owner}/${ref.repo}/pulls/${ref.number}"
        val (pullCode, pullRaw) = gh(key, base, "GET", pullPath)
        if (pullCode !in 200..299) return null
        val pull = runCatching { json.decodeFromString<GhPull>(pullRaw) }.getOrNull() ?: return null
        val headSha = pull.head?.sha?.trim().orEmpty()
        if (headSha.isEmpty()) return null
        val runsPath = "/repos/${ref.owner}/${ref.repo}/commits/$headSha/check-runs?per_page=100"
        val (runCode, runRaw) = gh(key, base, "GET", runsPath)
        if (runCode !in 200..299) {
            return PrCheckSnap(headSha = headSha, prUrl = prUrl.trim())
        }
        val rawRuns = runCatching { json.decodeFromString<GhCheckRuns>(runRaw) }.getOrNull()
            ?.check_runs
            .orEmpty()
        val runs = rawRuns.map { raw ->
            val outputText = listOfNotNull(raw.output?.title, raw.output?.summary, raw.output?.text)
                .joinToString("\n")
                .trim()
                .takeIf { it.isNotEmpty() }
            PrCheckRunView(
                name = raw.name.orEmpty(),
                status = raw.status.orEmpty(),
                conclusion = raw.conclusion,
                id = raw.id,
                htmlUrl = raw.html_url.orEmpty(),
                outputText = outputText,
            )
        }
        val enriched = enrichTestDetails(key, base, ref, runs)
        val display = PrCheckSummary.summarize(enriched)
        return if (display == null) {
            PrCheckSnap(headSha = headSha, prUrl = prUrl.trim())
        } else {
            display.toSnap(headSha, prUrl.trim())
        }
    }

    internal fun enrichTestDetails(
        token: String,
        apiBase: String,
        ref: GithubPullRef,
        runs: List<PrCheckRunView>,
    ): List<PrCheckRunView> {
        var budget = ANNOTATION_BUDGET
        return runs.map { run ->
            if (budget <= 0) return@map run
            val failed = run.status.equals("completed", ignoreCase = true) &&
                run.conclusion?.lowercase() in setOf("failure", "cancelled", "timed_out", "action_required")
            if (!failed || run.id <= 0L) return@map run
            if (PrCheckClassifier.category(run.name) != PrCheckChipKind.TestsFailed) return@map run
            budget--
            val path = "/repos/${ref.owner}/${ref.repo}/check-runs/${run.id}/annotations"
            val (code, raw) = gh(token, apiBase, "GET", path)
            if (code !in 200..299) return@map run
            val annotations = runCatching { json.decodeFromString<List<GhAnnotation>>(raw) }.getOrDefault(emptyList())
            val titles = annotations.mapNotNull { it.title?.trim()?.takeIf { t -> t.isNotEmpty() } }
            val messages = annotations.mapNotNull { it.message?.trim()?.takeIf { t -> t.isNotEmpty() } }
            val detail = PrCheckTestDetail.merge(run.name, run.outputText, titles, messages)
            if (detail.isNullOrBlank()) run else run.copy(testDetail = detail)
        }
    }

    private fun gh(token: String, apiBase: String, method: String, path: String): Pair<Int, String> {
        val builder = Request.Builder()
            .url("$apiBase$path")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", ClientOrigin.ID)
            .header("X-GitHub-Api-Version", "2022-11-28")
        val request = when (method) {
            "GET" -> builder.get().build()
            else -> error("Unsupported GitHub method")
        }
        http.newCall(request).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    @Serializable
    private data class GhPull(
        val head: GhHead? = null,
    )

    @Serializable
    private data class GhHead(
        val sha: String? = null,
    )

    @Serializable
    private data class GhCheckRuns(
        val check_runs: List<GhCheckRun> = emptyList(),
    )

    @Serializable
    private data class GhCheckRun(
        val id: Long = 0L,
        val name: String? = null,
        val html_url: String? = null,
        val status: String? = null,
        val conclusion: String? = null,
        val output: GhCheckOutput? = null,
    )

    @Serializable
    private data class GhCheckOutput(
        val title: String? = null,
        val summary: String? = null,
        val text: String? = null,
    )

    @Serializable
    private data class GhAnnotation(
        val title: String? = null,
        val message: String? = null,
    )
}
