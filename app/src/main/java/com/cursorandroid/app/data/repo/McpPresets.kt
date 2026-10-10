package com.cursorandroid.app.data.repo

data class McpPreset(
    val label: String,
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** Login happens on cursor.com, not with a header here. */
    val oauthOnly: Boolean = false,
) {
    fun toServer(): StoredMcpServer = StoredMcpServer(
        name = name,
        type = TYPE_HTTP,
        url = url,
        headers = headers,
    )
}

const val CURSOR_MCP_SETTINGS_URL = "https://cursor.com/agents"

val MCP_PRESETS = listOf(
    McpPreset("GitHub", "github", "https://api.githubcopilot.com/mcp/", mapOf("Authorization" to "Bearer ")),
    McpPreset("Context7", "context7", "https://mcp.context7.com/mcp"),
    McpPreset("Cloudflare", "cloudflare", "https://mcp.cloudflare.com/mcp"),
    McpPreset("Cloudflare Docs", "cloudflare-docs", "https://docs.mcp.cloudflare.com/mcp"),
    McpPreset("Postman", "postman", "https://mcp.postman.com/minimal"),
    McpPreset("GitLab", "gitlab", "https://gitlab.com/api/v4/mcp", oauthOnly = true),
)

/** Hosts known to use OAuth login only, so the editor can point to where that login is completed. */
private val OAUTH_ONLY_HOSTS = setOf("gitlab.com", "mcp.slack.com", "mcp.sentry.dev")

fun isOAuthOnlyHost(url: String?): Boolean {
    val host = SafeLinks.httpsUri(url)?.host?.lowercase() ?: return false
    return OAUTH_ONLY_HOSTS.any { host == it || host.endsWith(".$it") }
}
