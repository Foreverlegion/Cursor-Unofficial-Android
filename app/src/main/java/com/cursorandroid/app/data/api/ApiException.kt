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
    val body: String? = null,
    val helpUrl: String? = null,
    val provider: String? = null,
    val environmentId: String? = null,
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
    val isAgentIdConflict: Boolean
        get() = errorCode == "agent_id_conflict" ||
            (code == 409 && message.contains("agent_id_conflict", ignoreCase = true))
    val isSecretNameAmbiguous: Boolean
        get() = errorCode == "secret_name_ambiguous" ||
            message.contains("secret_name_ambiguous", ignoreCase = true)
    val isArchivedAgent: Boolean
        get() = errorCode == "agent_archived" ||
            message.contains("agent_archived", ignoreCase = true)
    val isInvalidLastEvent: Boolean
        get() = errorCode == "invalid_last_event_id" ||
            (code == 400 && message.contains("invalid_last_event_id", ignoreCase = true))
    val isIntegrationDisconnected: Boolean
        get() = errorCode == "integration_not_connected"
    val isEnvironmentNameConflict: Boolean
        get() = errorCode == "environment_name_conflict"

    fun displayMessage(): String {
        return when {
            isInvalidKey -> EXPIRED_MESSAGE
            isAgentIdConflict -> "That agent id is already in use."
            isSecretNameAmbiguous -> "That secret name has more than one version. Pick one."
            isBusy -> "Agent is still working. Message queued."
            isNotCancellable -> "Run already finished."
            isArchivedAgent -> "This agent is archived. Unarchive it, then try again."
            isInvalidLastEvent -> "Stream position was rejected. Refreshing the run."
            isStreamGone -> "Run stream ended. Refreshing."
            isIntegrationDisconnected -> buildString {
                append("That integration is not connected")
                provider?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                append(".")
                helpUrl?.takeIf { it.isNotBlank() }?.let { append(" $it") }
            }
            isEnvironmentNameConflict -> buildString {
                append("An environment with that name already exists.")
                environmentId?.takeIf { it.isNotBlank() }?.let { append(" Id $it.") }
            }
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
                val root = json.parseToJsonElement(raw) as? JsonObject
                val err = root?.get("error")?.jsonObject
                ParsedError(
                    code = err.str("code") ?: root.str("code"),
                    message = err.str("message") ?: root.str("message"),
                    helpUrl = err.str("helpUrl") ?: root.str("helpUrl"),
                    provider = err.str("provider") ?: root.str("provider"),
                    environmentId = err.str("environmentId") ?: root.str("environmentId"),
                )
            }.getOrNull()
            val errMsg = parsed?.message?.takeIf { it.isNotBlank() } ?: raw
            return ApiException(
                code,
                errMsg,
                parsed?.code,
                wait,
                body = raw,
                helpUrl = parsed?.helpUrl,
                provider = parsed?.provider,
                environmentId = parsed?.environmentId,
            )
        }

        private data class ParsedError(
            val code: String?,
            val message: String?,
            val helpUrl: String?,
            val provider: String?,
            val environmentId: String?,
        )

        private fun JsonObject?.str(key: String): String? {
            return this?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    }
}
