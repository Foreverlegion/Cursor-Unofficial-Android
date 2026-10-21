package com.cursorandroid.app.data.repo

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** One server found in an `mcp.json`. [problem] is set when it cannot be used as is. */
data class McpImportItem(
    val server: StoredMcpServer,
    val problem: String? = null,
    val notes: List<String> = emptyList(),
)

data class McpImportParse(
    val items: List<McpImportItem> = emptyList(),
    val error: String? = null,
)

private val PARSE_JSON = Json { isLenient = true }
@OptIn(ExperimentalSerializationApi::class)
private val PRETTY_JSON = Json { prettyPrint = true; prettyPrintIndent = "  " }

const val MAX_MCP_IMPORT = 200
const val MAX_MCP_SERVERS = 50

/**
 * Reads the format Cursor desktop uses in `~/.cursor/mcp.json` and `.cursor/mcp.json`:
 * `{"mcpServers": {name: {url | command, args, env, headers, type, auth}}}`.
 * `"disabled": true` or `"enabled": false` imports the server switched off.
 * A bare `{name: {...}}` map and the `servers` key are accepted too.
 */
fun parseMcpJson(raw: String): McpImportParse {
    val text = raw.trim().removePrefix("\uFEFF").trim()
    if (text.isEmpty()) return McpImportParse(error = "Nothing to import")
    val root = runCatching { PARSE_JSON.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: return McpImportParse(error = "Not valid JSON")
    val servers = (root["mcpServers"] ?: root["servers"]) as? JsonObject
        ?: root.takeIf { looksLikeServerMap(it) }
        ?: return McpImportParse(error = "No \"mcpServers\" object found")
    if (servers.isEmpty()) return McpImportParse(error = "\"mcpServers\" is empty")
    val items = servers.entries.take(MAX_MCP_IMPORT).mapNotNull { (name, value) ->
        (value as? JsonObject)?.let { parseEntry(name, it) }
    }
    if (items.isEmpty()) return McpImportParse(error = "No servers found")
    return McpImportParse(items)
}

private fun looksLikeServerMap(root: JsonObject): Boolean {
    if (root.isEmpty()) return false
    return root.values.all { v -> v is JsonObject && (v.containsKey("url") || v.containsKey("command") || v.containsKey("serverUrl")) }
}

private fun parseEntry(rawName: String, obj: JsonObject): McpImportItem {
    val name = rawName.trim()
    val url = (obj.str("url") ?: obj.str("serverUrl"))?.trim()?.ifEmpty { null }
    val command = obj.str("command")?.trim()?.ifEmpty { null }
    val declared = obj.str("type")?.trim()?.lowercase().orEmpty()
    val kind = when {
        declared == TYPE_STDIO -> TYPE_STDIO
        declared == TYPE_SSE -> TYPE_SSE
        url != null -> TYPE_HTTP
        command != null -> TYPE_STDIO
        else -> TYPE_HTTP
    }
    val headers = obj.strMap("headers")
    val env = obj.strMap("env")
    val args = (obj["args"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    val auth = (obj["auth"] as? JsonObject)?.let { a ->
        val id = (a.str("CLIENT_ID") ?: a.str("clientId")).orEmpty().trim()
        if (id.isEmpty()) {
            null
        } else {
            StoredMcpAuth(
                clientId = id,
                clientSecret = (a.str("CLIENT_SECRET") ?: a.str("clientSecret")).orEmpty().trim(),
                scopes = (a["scopes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            )
        }
    }
    val disabled = (obj["disabled"] as? JsonPrimitive)?.booleanOrNull == true ||
        (obj["enabled"] as? JsonPrimitive)?.booleanOrNull == false
    val server = StoredMcpServer(
        enabled = !disabled,
        name = name,
        type = kind,
        url = if (kind == TYPE_STDIO) null else url,
        headers = if (kind == TYPE_STDIO) emptyMap() else headers,
        command = if (kind == TYPE_STDIO) command else null,
        args = if (kind == TYPE_STDIO) args else emptyList(),
        env = if (kind == TYPE_STDIO) env else emptyMap(),
        auth = if (kind == TYPE_STDIO) null else auth,
    )
    val problem = when {
        name.isEmpty() -> "No name"
        kind == TYPE_STDIO && command == null -> "Stdio needs a command"
        kind != TYPE_STDIO && url == null -> "No url"
        kind != TYPE_STDIO && !SafeLinks.isHttps(url) -> "Needs an https URL"
        else -> null
    }
    val notes = buildList {
        if (kind == TYPE_SSE) add("SSE is not supported for cloud agents")
        if (kind == TYPE_STDIO) add("Runs inside the cloud VM")
        val values = headers.values + env.values + args + listOfNotNull(url, command)
        if (values.any { it.contains("\${") }) add("Has \${...} variables; they are not expanded here")
    }
    return McpImportItem(server, problem, notes)
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

private fun JsonObject.strMap(key: String): Map<String, String> {
    val obj = this[key] as? JsonObject ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    obj.forEach { (k, v) ->
        val value = (v as? JsonPrimitive)?.contentOrNull ?: return@forEach
        if (k.isNotBlank()) out[k.trim()] = value
    }
    return out
}

/**
 * Writes the same format. Without [includeSecrets] header and env values and the OAuth client secret
 * are written empty, so the file shows what to fill in without carrying the secret.
 */
fun exportMcpJson(items: List<StoredMcpServer>, includeSecrets: Boolean): String {
    val used = HashSet<String>()
    val servers = buildJsonObject {
        items.forEach { raw ->
            val item = if (includeSecrets) raw else raw.withoutSecrets()
            val base = item.name.trim().ifEmpty { "server" }
            var label = base
            var n = 2
            while (!used.add(label.lowercase())) label = "$base ($n)".also { n++ }
            put(label, serverJson(item))
        }
    }
    return PRETTY_JSON.encodeToString(JsonObject.serializer(), buildJsonObject { put("mcpServers", servers) })
}

private fun serverJson(item: StoredMcpServer): JsonObject = buildJsonObject {
    if (item.isStdio()) {
        put("command", item.command.orEmpty())
        if (item.args.isNotEmpty()) put("args", buildJsonArray { item.args.forEach { add(JsonPrimitive(it)) } })
        if (item.env.isNotEmpty()) put("env", item.env.toJson())
    } else {
        put("url", item.url.orEmpty())
        if (item.isSse()) put("type", TYPE_SSE)
        if (item.headers.isNotEmpty()) put("headers", item.headers.toJson())
        item.auth?.takeIf { it.clientId.isNotBlank() }?.let { a ->
            put(
                "auth",
                buildJsonObject {
                    put("CLIENT_ID", a.clientId)
                    if (a.clientSecret.isNotBlank()) put("CLIENT_SECRET", a.clientSecret)
                    if (a.scopes.isNotEmpty()) put("scopes", buildJsonArray { a.scopes.forEach { add(JsonPrimitive(it)) } })
                },
            )
        }
    }
    if (!item.enabled) put("disabled", true)
}

private fun Map<String, String>.toJson(): JsonElement = buildJsonObject { forEach { (k, v) -> put(k, v) } }

fun mcpNameKey(name: String): String = name.trim().lowercase()

/** Incoming servers whose name already exists on this phone. */
fun mcpConflicts(existing: List<StoredMcpServer>, incoming: List<StoredMcpServer>): List<StoredMcpServer> {
    val have = existing.map { mcpNameKey(it.name) }.toSet()
    return incoming.filter { mcpNameKey(it.name) in have }
}

data class McpMerge(
    val items: List<StoredMcpServer>,
    val added: Int,
    val replaced: Int,
    val skipped: Int,
)

/**
 * Merges by name. A name already saved is replaced only when it is in [replaceNames]; a replaced server
 * keeps its id (so per-repo selections still point at it) and, where the file has blank secret values,
 * its saved secrets.
 */
fun mergeMcpImport(
    existing: List<StoredMcpServer>,
    incoming: List<StoredMcpServer>,
    replaceNames: Set<String>,
): McpMerge {
    val out = existing.toMutableList()
    var added = 0
    var replaced = 0
    var skipped = 0
    for (item in incoming) {
        val key = mcpNameKey(item.name)
        val at = out.indexOfFirst { mcpNameKey(it.name) == key }
        if (at >= 0) {
            if (key in replaceNames) {
                val old = out[at]
                out[at] = item.copy(id = old.id).fillSecretsFrom(old)
                replaced++
            } else {
                skipped++
            }
        } else if (out.size >= MAX_MCP_SERVERS) {
            skipped++
        } else {
            out += item
            added++
        }
    }
    return McpMerge(out, added, replaced, skipped)
}
