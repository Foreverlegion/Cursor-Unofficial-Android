package com.cursorandroid.app.data.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ApiKeyMint {
    data class ListedKey(
        val name: String,
        val masked: String?,
        val secret: String?,
    )

    sealed interface Decision {
        data class Reuse(val key: String) : Decision
        data object Create : Decision
        data class Refuse(val message: String) : Decision
    }

    const val DASHBOARD = "https://cursor.com/dashboard/api"

    const val REFUSE =
        "Older installs of this unofficial app may have created one or more User API keys named cursor-android or Cursor Android. " +
            "Cursor only allows a new key after those leftovers are deleted, so this sign-in will not create another. " +
            "There may be more than one key to delete. " +
            "Open cursor.com/dashboard/api, delete every key with those names, then sign in again."

    fun explainsLeftoverKeys(message: String?): Boolean {
        if (message == REFUSE) return true
        val text = message?.lowercase().orEmpty()
        if (text.isEmpty()) return false
        val names = text.contains("cursor-android") || text.contains("cursor android")
        val blocked = text.contains("already") || text.contains("delete") || text.contains("leftover")
        return names && blocked
    }

    fun isExistingKeyFailure(body: String): Boolean {
        val text = body.lowercase()
        val mentionsKey = text.contains("api key") || text.contains("apikey") ||
            text.contains("cursor-android") || text.contains("cursor android")
        val blocked = text.contains("already") || text.contains("exist") || text.contains("duplicate")
        return mentionsKey && blocked
    }

    fun decide(stored: String?, listed: List<ListedKey>?): Decision {
        val saved = stored?.trim()?.takeIf { it.isNotEmpty() }
        if (saved != null) return Decision.Reuse(saved)
        if (listed == null) return Decision.Create
        val named = listed.filter { isAppKeyName(it.name) }
        val secret = named.firstOrNull { it.name.equals("cursor-android", ignoreCase = true) }?.secret
            ?: named.firstNotNullOfOrNull { it.secret?.takeIf { value -> value.isNotBlank() } }
        if (!secret.isNullOrBlank()) return Decision.Reuse(secret)
        if (named.isNotEmpty()) return Decision.Refuse(REFUSE)
        return Decision.Create
    }

    fun isAppKeyName(name: String?): Boolean {
        val normalized = name?.trim()?.lowercase()?.replace(Regex("\\s+"), " ").orEmpty()
        if (normalized.isEmpty()) return false
        return normalized == "cursor-android" ||
            normalized.startsWith("cursor-android") ||
            normalized == "cursor android" ||
            normalized.startsWith("cursor android")
    }

    fun parse(text: String): List<ListedKey>? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root.keyArray() ?: return emptyList()
            else -> return null
        }
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name").orEmpty()
            val masked = obj.string("maskedKey") ?: obj.string("masked_key")
            val secret = asSecret(obj.string("apiKey"))
                ?: asSecret(obj.string("api_key"))
                ?: asSecret(obj.string("key"))
            if (name.isBlank() && masked.isNullOrBlank() && secret.isNullOrBlank()) return@mapNotNull null
            ListedKey(name = name, masked = masked, secret = secret)
        }
    }

    private fun JsonObject.keyArray(): JsonArray? {
        listOf("apiKeys", "api_keys", "userApiKeys", "user_api_keys", "keys").forEach { key ->
            (this[key] as? JsonArray)?.let { return it }
        }
        return null
    }

    private fun JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun asSecret(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (text.length < 20 || text.contains("...")) return null
        return text
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
}
