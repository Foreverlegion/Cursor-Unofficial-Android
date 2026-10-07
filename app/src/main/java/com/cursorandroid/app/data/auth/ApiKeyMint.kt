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

    const val REFUSE =
        "This account already has a Cursor Android API key, so sign-in will not create another. Paste that key, or delete it on cursor.com/dashboard/api and sign in again."

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
