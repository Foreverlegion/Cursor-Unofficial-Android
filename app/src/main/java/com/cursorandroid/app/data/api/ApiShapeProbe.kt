package com.cursorandroid.app.data.api

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

/** Debug builds only: logs response key paths and model-like values, never prompt or message text. */
object ApiShape {
    private const val TAG = "ApiShape"
    private const val MAX_PATHS = 80
    private const val MAX_BODY = 512L * 1024
    private val MODEL_KEY = Regex("model", RegexOption.IGNORE_CASE)

    fun paths(element: JsonElement, prefix: String = "", out: MutableSet<String> = LinkedHashSet()): Set<String> {
        if (out.size >= MAX_PATHS) return out
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                if (value is JsonObject || value is JsonArray) paths(value, path, out) else out += path
            }
            is JsonArray -> element.firstOrNull()?.let { paths(it, "$prefix[]", out) }
            else -> if (prefix.isNotEmpty()) out += prefix
        }
        return out
    }

    fun modelValues(element: JsonElement, prefix: String = "", out: MutableMap<String, String> = LinkedHashMap()): Map<String, String> {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                if (MODEL_KEY.containsMatchIn(path) && value is JsonPrimitive && value !is JsonNull) {
                    out[path] = value.content.take(80)
                } else {
                    modelValues(value, path, out)
                }
            }
            is JsonArray -> element.take(3).forEach { modelValues(it, "$prefix[]", out) }
            else -> Unit
        }
        return out
    }

    fun describe(label: String, body: String, json: Json): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val models = modelValues(root)
        return "$label keys=${paths(root)} model=$models"
    }

    fun log(label: String, body: String, json: Json) {
        describe(label, body, json)?.let { Log.d(TAG, it) }
    }

    fun interceptor(json: Json) = Interceptor { chain ->
        val response: Response = chain.proceed(chain.request())
        val type = response.body?.contentType()
        if (response.isSuccessful && type?.subtype == "json") {
            val body = response.peekBody(MAX_BODY).string()
            log("${chain.request().method} ${chain.request().url.encodedPath} -> ${response.code}", body, json)
        }
        response
    }
}

/** Debug builds only: the cancel request path and its HTTP status. No headers, no body. */
object CancelLog {
    private const val TAG = "CancelRun"

    fun interceptor() = Interceptor { chain ->
        val request = chain.request()
        val tracked = request.method == "POST" && request.url.encodedPath.endsWith("/cancel")
        if (!tracked) return@Interceptor chain.proceed(request)
        try {
            val response = chain.proceed(request)
            Log.d(TAG, "POST ${request.url.encodedPath} -> ${response.code}")
            response
        } catch (e: java.io.IOException) {
            Log.d(TAG, "POST ${request.url.encodedPath} -> ${e::class.java.simpleName}")
            throw e
        }
    }
}
