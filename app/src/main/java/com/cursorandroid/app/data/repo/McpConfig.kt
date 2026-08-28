package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.McpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val MCP_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun encodeStoredMcps(items: List<StoredMcpServer>): String = MCP_JSON.encodeToString(items)

fun decodeStoredMcps(raw: String?): List<StoredMcpServer> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return emptyList()
    return runCatching { MCP_JSON.decodeFromString<List<StoredMcpServer>>(text) }.getOrDefault(emptyList())
}

@Serializable
data class StoredMcpServer(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val name: String = "",
    val type: String = TYPE_HTTP,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
) {
    fun kindLabel(): String = if (isStdio()) "Stdio" else "HTTP"

    fun isStdio(): Boolean = type.equals(TYPE_STDIO, ignoreCase = true)

    fun toApi(): McpServer? {
        val label = name.trim()
        if (label.isEmpty()) return null
        return if (isStdio()) {
            val cmd = command?.trim().orEmpty()
            if (cmd.isEmpty()) return null
            McpServer(
                name = label,
                type = TYPE_STDIO,
                command = cmd,
                args = args.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null },
                env = env.filterKeys { it.isNotBlank() }.ifEmpty { null },
            )
        } else {
            val href = url?.trim().orEmpty()
            if (!SafeLinks.isHttps(href)) return null
            McpServer(
                name = label,
                type = TYPE_HTTP,
                url = href,
                headers = headers.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }.ifEmpty { null },
            )
        }
    }
}

fun storedMcpsToApi(items: List<StoredMcpServer>, max: Int = 50): List<McpServer>? {
    val out = LinkedHashMap<String, McpServer>()
    for (item in items) {
        if (!item.enabled) continue
        val api = item.toApi() ?: continue
        val key = api.name.lowercase()
        if (key in out) continue
        out[key] = api
        if (out.size >= max) break
    }
    return out.values.toList().ifEmpty { null }
}

fun migrateLegacyMcp(name: String, url: String): StoredMcpServer? {
    val item = StoredMcpServer(name = name, type = TYPE_HTTP, url = url)
    return item.takeIf { it.toApi() != null }
}

fun parseHeaderLines(raw: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    raw.lineSequence().forEach { line ->
        val text = line.trim()
        if (text.isEmpty() || text.startsWith("#")) return@forEach
        val split = text.indexOf(':')
        if (split <= 0) return@forEach
        val key = text.substring(0, split).trim()
        val value = text.substring(split + 1).trim()
        if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
    }
    return out
}

fun parseEnvLines(raw: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    raw.lineSequence().forEach { line ->
        val text = line.trim()
        if (text.isEmpty() || text.startsWith("#")) return@forEach
        val split = text.indexOf('=')
        if (split <= 0) return@forEach
        val key = text.substring(0, split).trim()
        val value = text.substring(split + 1).trim()
        if (key.isNotEmpty()) out[key] = value
    }
    return out
}

fun parseArgLines(raw: String): List<String> {
    return raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
}

fun headerLines(headers: Map<String, String>): String {
    return headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
}

fun envLines(env: Map<String, String>): String {
    return env.entries.joinToString("\n") { "${it.key}=${it.value}" }
}

fun argLines(args: List<String>): String = args.joinToString("\n")

const val TYPE_HTTP = "http"
const val TYPE_STDIO = "stdio"
