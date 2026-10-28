package com.cursorandroid.app.data.auth

import com.cursorandroid.app.data.api.ApiException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

data class DashboardUsageLine(
    val label: String,
    val percent: Double,
)

data class DashboardPeriodUsage(
    val cursorModelsPercent: Double?,
    val otherModelsPercent: Double?,
    val billingCycleEndMs: Long?,
    val extraLines: List<DashboardUsageLine> = emptyList(),
) {
    val hasBars: Boolean get() = cursorModelsPercent != null || otherModelsPercent != null
}

@Serializable
data class StoredDashboardPeriodUsage(
    @SerialName("cursor") val cursorModelsPercent: Double? = null,
    @SerialName("other") val otherModelsPercent: Double? = null,
    @SerialName("end") val billingCycleEndMs: Long? = null,
) {
    fun toPeriod(): DashboardPeriodUsage = DashboardPeriodUsage(
        cursorModelsPercent = cursorModelsPercent,
        otherModelsPercent = otherModelsPercent,
        billingCycleEndMs = billingCycleEndMs,
    )
}

fun DashboardPeriodUsage.toStored(): StoredDashboardPeriodUsage = StoredDashboardPeriodUsage(
    cursorModelsPercent = cursorModelsPercent,
    otherModelsPercent = otherModelsPercent,
    billingCycleEndMs = billingCycleEndMs,
)

/**
 * Fetches dashboard quota bars via api2.cursor.sh. Access tokens stay in memory only.
 */
class CursorDashboardUsage(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val tokenCache = AtomicReference<CachedToken?>(null)
    private var cacheKeyHash: String? = null

    fun fetchCurrentPeriodUsage(apiKey: String): DashboardPeriodUsage? {
        val trimmed = apiKey.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        val access = accessToken(trimmed) ?: return null
        val text = postUsage(access) ?: return null
        return parseDashboardPeriodUsage(text)
    }

    internal fun accessToken(apiKey: String): String? {
        val hash = sha256(apiKey)
        if (cacheKeyHash != hash) {
            tokenCache.set(null)
            cacheKeyHash = hash
        }
        val now = System.currentTimeMillis()
        tokenCache.get()?.let { cached ->
            if (now < cached.expiresAtMs - REFRESH_SKEW_MS) return cached.token
        }
        val exchanged = exchange(apiKey) ?: return null
        val exp = jwtExpiresAtMs(exchanged) ?: (now + DEFAULT_TOKEN_TTL_MS)
        tokenCache.set(CachedToken(exchanged, exp))
        return exchanged
    }

    internal fun exchange(apiKey: String): String? {
        val request = Request.Builder()
            .url("$API/auth/exchange_user_api_key")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post("{}".toRequestBody(JSON))
            .build()
        val response = runCatching { http.newCall(request).execute() }.getOrNull() ?: return null
        return response.use { resp ->
            val body = resp.body?.string().orEmpty()
            when (resp.code) {
                401, 403 -> null
                in 200..299 -> readAccessToken(body)
                429 -> throw ApiException(429, "Dashboard rate limited", retryAfterMs = ApiException.parseRetryAfter(resp.header("Retry-After"), System.currentTimeMillis()))
                else -> null
            }
        }
    }

    private fun postUsage(accessToken: String): String? {
        val request = Request.Builder()
            .url("$API/aiserver.v1.DashboardService/GetCurrentPeriodUsage")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Connect-Protocol-Version", "1")
            .post("{}".toRequestBody(JSON))
            .build()
        val response = runCatching { http.newCall(request).execute() }.getOrNull() ?: return null
        return response.use { resp ->
            val body = resp.body?.string().orEmpty()
            when (resp.code) {
                401, 403 -> {
                    tokenCache.set(null)
                    null
                }
                in 200..299 -> body
                429 -> throw ApiException(429, "Dashboard rate limited", retryAfterMs = ApiException.parseRetryAfter(resp.header("Retry-After"), System.currentTimeMillis()))
                else -> null
            }
        }
    }

    private fun readAccessToken(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return root.string("accessToken") ?: root.string("access_token")
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private data class CachedToken(val token: String, val expiresAtMs: Long)

    companion object {
        private const val API = "https://api2.cursor.sh"
        private const val PREFIX = "crsr_"
        private const val REFRESH_SKEW_MS = 5 * 60_000L
        private const val DEFAULT_TOKEN_TTL_MS = 3_600_000L
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun parseDashboardPeriodUsage(text: String): DashboardPeriodUsage? {
    val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val plan = root["planUsage"]?.jsonObject
    val cursor = plan?.percent("autoPercentUsed")
    val other = plan?.percent("apiPercentUsed")
    val end = root.epochMs("billingCycleEnd") ?: root.epochMs("billingCycleEndMs")
    val extras = mutableListOf<DashboardUsageLine>()
    plan?.percent("totalPercentUsed")?.let { extras += DashboardUsageLine("Included usage", it) }
    root["spendLimitUsage"]?.jsonObject?.let { spend ->
        spend.percent("totalPercentUsed")?.let { extras += DashboardUsageLine("On-demand spend", it) }
        spend.percent("percentUsed")?.let { extras += DashboardUsageLine("On-demand spend", it) }
    }
    if (cursor == null && other == null && extras.isEmpty()) return null
    return DashboardPeriodUsage(
        cursorModelsPercent = cursor,
        otherModelsPercent = other,
        billingCycleEndMs = end,
        extraLines = extras.distinctBy { it.label },
    )
}

internal fun jwtExpiresAtMs(token: String): Long? {
    val parts = token.split('.')
    if (parts.size < 2) return null
    val payload = runCatching {
        val padded = parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
        String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
    }.getOrNull() ?: return null
    val root = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
    return root.epochMs("exp")
}

private fun JsonObject.percent(key: String): Double? = this[key]?.let { jsonNumber(it) }

private fun JsonObject.epochMs(key: String): Long? {
    val raw = this[key]?.jsonPrimitive ?: return null
    raw.contentOrNull?.toLongOrNull()?.let { return it }
    raw.contentOrNull?.toDoubleOrNull()?.let { return it.toLong() }
    return null
}

private fun jsonNumber(element: JsonElement): Double? {
    val prim = element.jsonPrimitive
    prim.doubleOrNull?.let { return it }
    return prim.contentOrNull?.toDoubleOrNull()
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
