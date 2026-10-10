package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ForgeClient {
    private val jsonMedia = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun test(forge: ForgeConnection): String = withContext(Dispatchers.IO) {
        val item = normalizedForge(forge)
        if (item.token.isBlank()) return@withContext "Add a token."
        val call = testCall(item) ?: return@withContext "Add an API URL for this forge."
        val (code, body) = execute(call)
        when (code) {
            in 200..299 -> {
                val login = parseForgeLogin(body)
                if (login.isNullOrBlank()) "Connected." else "Connected as $login."
            }
            401, 403 -> "The token was rejected ($code)."
            0 -> "Couldn't reach this forge."
            else -> "The forge returned $code."
        }
    }

    suspend fun listBranches(forge: ForgeConnection, repoUrl: String): List<String> = withContext(Dispatchers.IO) {
        val call = branchCall(forge, repoUrl) ?: return@withContext emptyList()
        val (code, body) = execute(call)
        if (code !in 200..299) return@withContext emptyList()
        parseBranchNames(forge.provider, body)
    }

    suspend fun createRepo(
        forge: ForgeConnection,
        name: String,
        privateRepo: Boolean,
        description: String?,
    ): RepositoryItem = withContext(Dispatchers.IO) {
        val item = normalizedForge(forge)
        if (!item.kind().canCreate) error("Repo creation isn't available for ${item.kind().label}.")
        if (item.provider == ForgeKind.BITBUCKET.id && item.username.isBlank()) {
            error("Set the Bitbucket workspace username on this forge.")
        }
        val call = createCall(item, name, privateRepo, description)
            ?: error("This forge needs a token and an API URL before it can create a repo.")
        val (code, body) = execute(call)
        if (code !in 200..299) error(if (code == 0) "Couldn't reach this forge." else "The forge returned $code.")
        parseCreatedRepo(item.provider, body, GithubRepos.sanitizeName(name))
            ?: error("The forge did not return an HTTPS repo URL.")
    }

    private fun execute(call: ForgeCall): Pair<Int, String> {
        val builder = Request.Builder().url(call.url)
        call.headers.forEach { (key, value) -> builder.header(key, value) }
        val request = when (call.method) {
            "POST" -> builder.post((call.body ?: "{}").toRequestBody(jsonMedia)).build()
            else -> builder.get().build()
        }
        return runCatching {
            http.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        }.getOrDefault(0 to "")
    }
}
