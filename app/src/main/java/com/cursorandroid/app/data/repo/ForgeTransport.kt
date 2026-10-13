package com.cursorandroid.app.data.repo

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A forge HTTP result. [code] is 0 when the request never got an answer. Header names are lowercase. */
data class ForgeResponse(
    val code: Int,
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/** Blocking HTTP seam for forge calls, so the sync logic can be tested without a network. */
fun interface ForgeTransport {
    fun execute(call: ForgeCall): ForgeResponse
}

object OkHttpForgeTransport : ForgeTransport {
    private val jsonMedia = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun execute(call: ForgeCall): ForgeResponse {
        val builder = Request.Builder().url(call.url)
        call.headers.forEach { (key, value) -> builder.header(key, value) }
        val request = when (call.method) {
            "POST" -> builder.post((call.body ?: "{}").toRequestBody(jsonMedia)).build()
            else -> builder.get().build()
        }
        return runCatching {
            http.newCall(request).execute().use { response ->
                ForgeResponse(
                    code = response.code,
                    body = response.body?.string().orEmpty(),
                    headers = response.headers.names().associate { it.lowercase() to response.header(it).orEmpty() },
                )
            }
        }.getOrDefault(ForgeResponse(0))
    }
}
