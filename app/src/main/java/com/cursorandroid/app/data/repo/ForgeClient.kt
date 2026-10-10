package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ForgeClient {
    @Volatile
    var transport: ForgeTransport = OkHttpForgeTransport

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
        val response = transport.execute(call)
        return response.code to response.body
    }
}
