package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Repo
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * Account User Rules live in Customize → Rules. The Cloud Agents API the phone
 * calls has no read or write for that store. Origin repo create is a different
 * credential than the saved API key, so this phone does not call it.
 * [agentRequest] is the repo-backed create the saved key can send.
 */
object UserRuleSync {
    const val REPO_NAME = "cursor-unofficial-android"
    const val SCOPE = "namespace:repositories:create"
    const val DIRECT_WRITE = false

    const val BLOCK =
        "The saved key is a Cloud Agents API key for api.cursor.com. That API has no User Rules write. " +
            "Browser login on api2.cursor.sh is not stored. It only calls DashboardService ListUserApiKeys and CreateUserApiKey. " +
            "A new Origin repo is POST https://api.cursor.com/v1/origin/repos/{ownerSlug} " +
            "with {\"name\",\"defaultBranch\":\"main\"} and a user access token scoped namespace:repositories:create. " +
            "The Cloud Agents key is not that token."

    @Serializable
    data class OriginCreateBody(
        val name: String,
        val defaultBranch: String = "main",
    )

    data class OriginCreate(
        val method: String,
        val url: String,
        val scope: String,
        val body: OriginCreateBody,
    )

    /** Shape of the Origin call. Not sent with the Cloud Agents key. */
    fun originCreate(ownerSlug: String, name: String = REPO_NAME): OriginCreate {
        val slug = ownerSlug.trim()
        require(slug(slug)) { "owner slug" }
        require(slug(name)) { "repo name" }
        return OriginCreate(
            method = "POST",
            url = "https://api.cursor.com/v1/origin/repos/$slug",
            scope = SCOPE,
            body = OriginCreateBody(name = name),
        )
    }

    /** The saved API key is not an Origin user access token. */
    fun originCreateAuthorized(): Boolean = false

    fun originCloneUrl(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.equals("origin.cursor.com", ignoreCase = true)) return null
        if (!uri.userInfo.isNullOrBlank() || !uri.query.isNullOrBlank() || !uri.rawFragment.isNullOrBlank()) return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
        val names = when {
            segments.size == 3 && segments[0] == "git" -> listOf(segments[1], segments[2])
            segments.size == 2 -> segments
            else -> return null
        }
        val owner = names[0].removeSuffix(".git")
        val repo = names[1].removeSuffix(".git")
        if (!slug(owner) || !slug(repo)) return null
        return "https://origin.cursor.com/git/$owner/$repo.git"
    }

    fun agentRequest(cloneUrl: String): CreateAgentRequest {
        val url = originCloneUrl(cloneUrl) ?: error("Origin clone URL required")
        return CreateAgentRequest(
            prompt = Prompt(text = prompt(url)),
            name = ProfileRule.AGENT_NAME,
            repos = listOf(Repo(url = url)),
            autoCreatePR = false,
        )
    }

    fun prompt(cloneUrl: String): String {
        return """
You are a one-time setup agent on an Origin repository ($cloneUrl). `repos` is set to that repository. `env` is omitted. Do not write app code and do not open a pull request.

Your only job: create or update the account-wide Cursor User Rule (Customize → Rules → User Rules) titled **${ProfileRule.AGENT_NAME}**, with Always Apply on. If that title already exists, replace its text. Do not add a second copy. Do not save a markdown artifact instead, and do not stop after writing a repo `.cursor/rules` file. The account User Rule is the result.

After it is saved, reply with the exact rule text and confirm Always Apply is on. Then stop.

### Rule text
${ruleBody()}
        """.trim()
    }

    /** Sections that belong in the account rule, without the no-repo setup preamble. */
    fun ruleBody(): String {
        val marker = "### What this app is"
        val text = ProfileRule.TEXT
        val at = text.indexOf(marker)
        return if (at >= 0) text.substring(at).trim() else text
    }

    private fun slug(value: String): Boolean {
        if (value.length !in 1..100) return false
        if (value == "." || value == "..") return false
        return value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }
}
