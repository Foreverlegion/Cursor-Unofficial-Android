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
    val retryAfterMs: Long? = null,
) : RuntimeException(message) {
    val isRateLimited: Boolean
        get() = code == 429 ||
            errorCode?.contains("rate_limit", ignoreCase = true) == true ||
            message.contains("exceeded the rate limit", ignoreCase = true)
    /** The Cursor API refused the key itself (revoked, rotated or wrong), so every further call would fail the same way. */
    val isInvalidKey: Boolean
        get() = code == 401 ||
            errorCode == SESSION_EXPIRED ||
            message.contains("Invalid User API Key", ignoreCase = true)
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
            isInvalidKey -> EXPIRED_MESSAGE
            isBusy -> "Agent is still working. Message queued."
            isNotCancellable -> "Run already finished."
            isStreamGone -> "Run stream ended. Refreshing."
            isRateLimited -> "Too many requests. Retrying shortly."
            else -> message
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        const val SESSION_EXPIRED = "session_expired"
        const val EXPIRED_MESSAGE = "Your Cursor sign-in expired. Sign in again."

        fun sessionExpired(): ApiException = ApiException(401, EXPIRED_MESSAGE, errorCode = SESSION_EXPIRED)

        /** Seconds or an HTTP date, as sent in a Retry-After header. Null when absent or unusable. */
        fun parseRetryAfter(value: String?, nowMs: Long = System.currentTimeMillis()): Long? {
            val raw = value?.trim().orEmpty()
            if (raw.isEmpty()) return null
            raw.toLongOrNull()?.let { return (it * 1000).coerceIn(0, MAX_RETRY_AFTER_MS) }
            val at = runCatching {
                java.time.ZonedDateTime.parse(raw, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli()
            }.getOrNull() ?: return null
            return (at - nowMs).coerceIn(0, MAX_RETRY_AFTER_MS)
        }

        private const val MAX_RETRY_AFTER_MS = 10 * 60_000L

        fun fromBody(code: Int, body: String, retryAfter: String? = null): ApiException {
            val raw = body.trim()
            val wait = parseRetryAfter(retryAfter)
            if (raw.isEmpty()) {
                return ApiException(code, "HTTP $code", retryAfterMs = wait)
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
            return ApiException(code, errMsg, errCode, wait)
        }
    }
}
