package com.cursorandroid.app.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val SECRET_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,254}")
private val RESERVED_PROXY = setOf("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY")

fun secretNameError(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Name is required"
    if (!SECRET_NAME.matches(trimmed)) {
        return "Use letters, digits, and underscores, and don't start with a digit"
    }
    val upper = trimmed.uppercase()
    if (upper.contains("CURSOR_SANDBOX") || upper in RESERVED_PROXY) return "That name is reserved"
    return null
}

fun secretValueError(value: String): String? {
    if (value.isEmpty()) return "Value is required"
    if (value.toByteArray(Charsets.UTF_8).size > 4096) return "Value is over 4096 bytes"
    return null
}

/** Versions listed on a 409 secret_name_ambiguous body. Values are never read. */
fun secretVersionsFromBody(body: String?): List<SecretVersion> {
    val raw = body?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
    val found = ArrayList<SecretVersion>()
    collectSecretRefs(root, found)
    return found.distinctBy { it.id }
}

private fun collectSecretRefs(el: JsonElement, out: MutableList<SecretVersion>) {
    when (el) {
        is JsonObject -> {
            val id = (el["id"] as? JsonPrimitive)?.contentOrNull
            val reposEl = el["repos"] as? JsonArray
            if (!id.isNullOrBlank() && reposEl != null) {
                out += SecretVersion(
                    id = id,
                    name = (el["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    type = (el["type"] as? JsonPrimitive)?.contentOrNull,
                    repos = reposEl.mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                )
            }
            el.values.forEach { collectSecretRefs(it, out) }
        }
        is JsonArray -> el.forEach { collectSecretRefs(it, out) }
        else -> Unit
    }
}
