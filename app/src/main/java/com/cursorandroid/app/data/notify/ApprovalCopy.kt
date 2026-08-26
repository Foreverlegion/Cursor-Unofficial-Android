package com.cursorandroid.app.data.notify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ApprovalAsk(
    val action: String,
    val body: String,
    val kind: String,
)

object ApprovalCopy {
    fun ask(name: String?, args: String?, status: String? = null): ApprovalAsk? {
        if (!isApproval(name, status)) return null
        val tool = name?.trim().orEmpty()
        val fields = parseArgs(args)
        val kind = kindOf(tool)
        val action = actionOf(tool, fields)
        return ApprovalAsk(
            action = action,
            body = shadeBody(action),
            kind = kind,
        )
    }

    fun shadeBody(action: String): String {
        val clean = action.replace("`", "").trim()
        return "Cursor is requesting approval to $clean. Approve this on your PC."
    }

    @Suppress("UNUSED_PARAMETER")
    fun isApproval(name: String?, status: String?): Boolean {
        return isPending(status)
    }

    fun isPending(status: String?): Boolean {
        val s = status?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_').orEmpty()
        if (s.isEmpty() || s in ACTIVE_OR_DONE) return false
        return s in PENDING
    }

    fun kindOf(name: String?): String {
        val key = normalize(name)
        return when {
            key.isEmpty() -> KIND_OTHER
            key in READ -> KIND_READ
            key in FOLDERS || looksLike(key, "mkdir", "create_dir", "create_folder", "create_directory") ->
                KIND_FOLDER
            key in DELETES || looksLike(key, "delete", "remove_file", "unlink") -> KIND_DELETE
            key in MOVES || looksLike(key, "move", "rename", "mv") -> KIND_MOVE
            key in WRITES || looksLike(key, "write", "edit", "patch", "str_replace", "search_replace") ->
                KIND_WRITE
            key in SHELLS || looksLike(key, "shell", "terminal", "bash", "cmd") -> KIND_SHELL
            key in MCPS || looksLike(key, "mcp") -> KIND_MCP
            key in FETCHES || looksLike(key, "fetch", "http") -> KIND_FETCH
            key in IMAGES || looksLike(key, "image") -> KIND_IMAGE
            else -> KIND_OTHER
        }
    }

    internal fun actionOf(name: String, fields: Map<String, String>): String {
        val path = first(fields, "path", "target_directory", "directory", "dir", "dest", "destination", "file")
        val command = first(fields, "command", "cmd", "script")
        val url = first(fields, "url", "uri", "href")
        val mcp = first(fields, "toolName", "tool", "name")
        val quoted = { value: String -> "`${clip(value)}`" }
        return when (kindOf(name)) {
            KIND_FOLDER -> if (path != null) "create a folder ${quoted(path)}" else "create a folder"
            KIND_DELETE -> if (path != null) "delete ${quoted(path)}" else "delete a file"
            KIND_MOVE -> {
                val dest = first(fields, "dest", "destination", "new_path", "to")
                when {
                    path != null && dest != null -> "move ${quoted(path)} to ${quoted(dest)}"
                    path != null -> "move ${quoted(path)}"
                    else -> "move a file"
                }
            }
            KIND_WRITE -> if (path != null) "write ${quoted(path)}" else "write a file"
            KIND_SHELL -> if (command != null) "run ${quoted(command)}" else "run a command"
            KIND_MCP -> if (mcp != null) "use MCP tool ${quoted(mcp)}" else "use an MCP tool"
            KIND_FETCH -> if (url != null) "fetch ${quoted(url)}" else "make a network request"
            KIND_IMAGE -> "generate an image"
            else -> {
                val label = name.trim().ifBlank { "a tool" }
                if (path != null) "use $label on ${quoted(path)}" else "use $label"
            }
        }
    }

    internal fun parseArgs(raw: String?): Map<String, String> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return emptyMap()
        val element = runCatching { JSON.parseToJsonElement(unwrap(text)) }.getOrNull()
        val obj = element as? JsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        obj.forEach { (key, value) ->
            val content = jsonText(value)
            if (content.isNotEmpty()) out[key] = content
        }
        return out
    }

    private fun jsonText(value: JsonElement): String {
        return when (value) {
            is JsonNull -> ""
            is JsonPrimitive -> value.contentOrNull?.trim().orEmpty()
            is JsonArray -> {
                val parts = value.mapNotNull { el ->
                    (el as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                }
                if (parts.isNotEmpty()) parts.joinToString(", ") else value.toString()
            }
            else -> value.toString()
        }
    }

    private fun first(fields: Map<String, String>, vararg keys: String): String? {
        keys.forEach { key ->
            fields[key]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val lower = fields.mapKeys { it.key.lowercase() }
        keys.forEach { key ->
            lower[key.lowercase()]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun unwrap(text: String): String {
        if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) {
            return runCatching { JSON.parseToJsonElement(text).jsonPrimitive.content }.getOrDefault(text)
        }
        return text
    }

    private fun clip(value: String): String {
        val one = value.trim().replace('\n', ' ')
        return if (one.length <= 80) one else one.take(77) + "..."
    }

    private fun normalize(name: String?): String {
        return name.orEmpty()
            .trim()
            .lowercase()
            .replace('-', '_')
            .replace(' ', '_')
    }

    private fun looksLike(key: String, vararg parts: String): Boolean {
        return parts.any { part -> key == part || key.endsWith("_$part") || key.contains(part) }
    }

    const val KIND_FOLDER = "folder"
    const val KIND_WRITE = "write"
    const val KIND_DELETE = "delete"
    const val KIND_MOVE = "move"
    const val KIND_SHELL = "shell"
    const val KIND_MCP = "mcp"
    const val KIND_FETCH = "fetch"
    const val KIND_IMAGE = "image"
    const val KIND_OTHER = "other"
    const val KIND_READ = "read"

    private val JSON = Json { ignoreUnknownKeys = true }
    private val ACTIVE_OR_DONE = setOf(
        "running", "started", "in_progress", "executing",
        "completed", "complete", "success", "succeeded",
        "finished", "done", "error", "failed", "cancelled", "canceled",
    )
    private val PENDING = setOf(
        "pending", "waiting", "requested", "ask", "approval",
        "awaiting", "awaiting_approval", "needs_approval",
        "user_approval", "requires_approval",
    )
    private val READ = setOf(
        "read", "read_file", "readfile", "grep", "grep_search", "glob", "glob_file_search",
        "list_dir", "ls", "codebase_search", "semantic_search", "file_search",
        "web_search", "websearch", "read_lints", "readlints", "await", "sleep",
        "todo_write", "todowrite", "switch_mode", "switchmode",
    )
    private val FOLDERS = setOf(
        "mkdir", "create_dir", "create_directory", "create_folder", "createfolder",
    )
    private val DELETES = setOf("delete", "delete_file", "deletefile", "remove_file")
    private val MOVES = setOf("move", "rename", "mv")
    private val WRITES = setOf(
        "write", "write_file", "writefile", "strreplace", "str_replace",
        "search_replace", "apply_patch", "applypatch", "edit_notebook", "editnotebook",
        "edit",
    )
    private val SHELLS = setOf(
        "shell", "run_terminal_cmd", "run_terminal_command", "terminal", "bash", "cmd",
    )
    private val MCPS = setOf("mcp", "call_mcp_tool", "callmcptool")
    private val FETCHES = setOf("web_fetch", "webfetch", "fetch")
    private val IMAGES = setOf("generate_image", "generateimage")
}
