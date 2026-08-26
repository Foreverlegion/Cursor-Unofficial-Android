package com.cursorandroid.app.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApiException(
    val code: Int,
    override val message: String,
    val errorCode: String? = null,
) : RuntimeException(message) {
    val isBusy: Boolean
        get() = errorCode == "agent_busy" || (code == 409 && message.contains("busy", ignoreCase = true))
    val isNotCancellable: Boolean
        get() = errorCode == "run_not_cancellable" ||
            (code == 409 && message.contains("not_cancellable", ignoreCase = true))
    val isStreamGone: Boolean
        get() = errorCode == "stream_expired" ||
            code == 410 ||
            message.contains("no longer available", ignoreCase = true) ||
            message.contains("stream_expired", ignoreCase = true)

    fun displayMessage(): String {
        return when {
            isBusy -> "Agent is still working. Message queued."
            isNotCancellable -> "Run already finished."
            isStreamGone -> "Run stream ended. Refreshing."
            else -> message
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromBody(code: Int, body: String): ApiException {
            val raw = body.trim()
            if (raw.isEmpty()) {
                return ApiException(code, "HTTP $code")
            }
            val parsed = runCatching {
                val root = json.parseToJsonElement(raw)
                val err = (root as? JsonObject)?.get("error")?.jsonObject
                val errCode = err?.get("code")?.jsonPrimitive?.contentOrNull
                val errMsg = err?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: (root as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                Pair(errCode, errMsg)
            }.getOrNull()
            val errCode = parsed?.first
            val errMsg = parsed?.second?.takeIf { it.isNotBlank() } ?: raw
            return ApiException(code, errMsg, errCode)
        }
    }
}
