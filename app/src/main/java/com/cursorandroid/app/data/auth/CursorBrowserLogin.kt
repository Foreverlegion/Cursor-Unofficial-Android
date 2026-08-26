package com.cursorandroid.app.data.auth

import com.cursorandroid.app.data.repo.ClientOrigin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.coroutineContext

class CursorBrowserLogin(
    private val http: OkHttpClient,
    private val json: Json,
) {
    fun handshake(): LoginHandshake {
        val uuid = UUID.randomUUID().toString()
        val verifier = randomVerifier()
        val challenge = sha256Url(verifier)
        val loginUrl = buildString {
            append(WEBSITE)
            append("/loginDeepControl?challenge=")
            append(enc(challenge))
            append("&uuid=")
            append(enc(uuid))
            append("&mode=login&redirectTarget=sdk")
        }
        return LoginHandshake(uuid = uuid, verifier = verifier, loginUrl = loginUrl)
    }

    suspend fun complete(handshake: LoginHandshake, keyName: String = KEY_NAME): String {
        return withContext(Dispatchers.IO) {
            val access = poll(handshake)
            mintKey(access, keyName)
        }
    }

    private suspend fun poll(handshake: LoginHandshake): String {
        var delayMs = 1_000L
        var errors = 0
        var useGet = false
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            coroutineContext.ensureActive()
            val result = if (useGet) {
                getPoll(handshake)
            } else {
                when (val posted = postPoll(handshake)) {
                    is PollResult.Pending -> {
                        when (val gotten = getPoll(handshake)) {
                            is PollResult.Tokens -> gotten
                            is PollResult.MissingRoute -> posted
                            else -> gotten
                        }
                    }
                    is PollResult.MissingRoute -> {
                        useGet = true
                        getPoll(handshake)
                    }
                    else -> posted
                }
            }
            when (result) {
                is PollResult.Tokens -> return result.accessToken
                is PollResult.Pending, is PollResult.MissingRoute -> {
                    errors = 0
                    delay(delayMs)
                    delayMs = (delayMs * 6 / 5).coerceAtMost(10_000L)
                }
                is PollResult.Failed -> {
                    errors += 1
                    if (errors >= 3) throw IllegalStateException(result.message)
                    delay(delayMs)
                }
            }
        }
        throw IllegalStateException("Login timed out. Open the Cursor page again.")
    }

    private fun postPoll(handshake: LoginHandshake): PollResult {
        val body = """{"uuid":${quote(handshake.uuid)},"verifier":${quote(handshake.verifier)}}"""
        val request = Request.Builder()
            .url("$API/auth/poll")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return executePoll(request, post = true)
    }

    private fun getPoll(handshake: LoginHandshake): PollResult {
        val request = Request.Builder()
            .url("$API/auth/poll?uuid=${enc(handshake.uuid)}&verifier=${enc(handshake.verifier)}")
            .header("Accept", "application/json")
            .get()
            .build()
        return executePoll(request, post = false)
    }

    private fun executePoll(request: Request, post: Boolean): PollResult {
        val response = runCatching { http.newCall(request).execute() }.getOrElse {
            return PollResult.Failed(it.message ?: "Poll failed")
        }
        return response.use { resp ->
            val text = resp.body?.string().orEmpty()
            when (resp.code) {
                200 -> {
                    val token = readAccessToken(text)
                    if (token.isNullOrBlank()) {
                        PollResult.Failed("Login response missing access token")
                    } else {
                        PollResult.Tokens(token)
                    }
                }
                404 -> {
                    if (post && isMissingRoute(text)) PollResult.MissingRoute else PollResult.Pending
                }
                else -> PollResult.Failed("Login poll HTTP ${resp.code}")
            }
        }
    }

    private fun mintKey(accessToken: String, name: String): String {
        val body = """{"name":${quote(name)}}"""
        val request = Request.Builder()
            .url("$API/aiserver.v1.DashboardService/CreateUserApiKey")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Connect-Protocol-Version", "1")
            .post(body.toRequestBody(JSON))
            .build()
        val response = http.newCall(request).execute()
        return response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Could not mint API key (HTTP ${resp.code})")
            }
            readApiKey(text) ?: throw IllegalStateException("Mint response missing api key")
        }
    }

    private fun readAccessToken(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        return root.string("accessToken") ?: root.string("access_token")
    }

    private fun readApiKey(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        return root.string("apiKey") ?: root.string("api_key")
    }

    private fun JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun isMissingRoute(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("not found") || lower.contains("not_found") || lower.contains("unknown")
    }

    private fun randomVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return b64(bytes)
    }

    private fun sha256Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return b64(digest)
    }

    private fun b64(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun quote(value: String): String = json.encodeToString(value)

    data class LoginHandshake(
        val uuid: String,
        val verifier: String,
        val loginUrl: String,
    )

    private sealed interface PollResult {
        data class Tokens(val accessToken: String) : PollResult
        data object Pending : PollResult
        data object MissingRoute : PollResult
        data class Failed(val message: String) : PollResult
    }

    companion object {
        private const val WEBSITE = "https://cursor.com"
        private const val API = "https://api2.cursor.sh"
        private const val KEY_NAME = ClientOrigin.ID
        private const val TIMEOUT_MS = 20L * 60L * 1000L
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
