package com.cursorandroid.app.data.api

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

sealed class StreamEvent {
    open val eventId: String? get() = null

    data class Status(val runId: String?, val status: String?, override val eventId: String? = null) : StreamEvent()
    data class Assistant(val text: String, override val eventId: String? = null) : StreamEvent()
    data class Thinking(val text: String, override val eventId: String? = null) : StreamEvent()
    data class ToolCall(
        val callId: String?,
        val name: String?,
        val status: String?,
        val args: String?,
        override val eventId: String? = null,
    ) : StreamEvent()
    data class Result(
        val runId: String?,
        val status: String?,
        val text: String?,
        override val eventId: String? = null,
    ) : StreamEvent()
    data class StreamError(val message: String, val recoverable: Boolean = false) : StreamEvent()
    data object Done : StreamEvent()
}

class SseStreamer(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://api.cursor.com/",
) {
    fun stream(agentId: String, runId: String, apiKey: String, lastEventId: String? = null): Flow<StreamEvent> {
        return callbackFlow {
            val agent = java.net.URLEncoder.encode(agentId, Charsets.UTF_8.name())
            val run = java.net.URLEncoder.encode(runId, Charsets.UTF_8.name())
            val url = "${baseUrl}v1/agents/$agent/runs/$run/stream"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "text/event-stream")
                .apply {
                    if (!lastEventId.isNullOrBlank()) {
                        header("Last-Event-ID", lastEventId)
                    }
                }
                .build()

            val listener = object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    val event = parse(id, type, data) ?: return
                    trySend(event)
                    if (event is StreamEvent.Done) {
                        close()
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val code = response?.code
                    val body = runCatching { response?.body?.string() }.getOrNull().orEmpty()
                    val parsed = ApiException.fromBody(code ?: 0, body.ifBlank { t?.message.orEmpty() })
                    val recoverable = code == 410 || parsed.isStreamGone ||
                        (t?.message?.contains("no longer available", ignoreCase = true) == true)
                    val message = when {
                        recoverable -> "stream_expired"
                        t != null && isCancel(t) -> "stream_canceled"
                        t != null -> t.message ?: "stream failed"
                        else -> parsed.message.ifBlank { "stream failed ($code)" }
                    }
                    if (message != "stream_canceled") {
                        trySend(StreamEvent.StreamError(message, recoverable))
                    }
                    close()
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            }

            val source = EventSources.createFactory(client).newEventSource(request, listener)
            awaitClose { source.cancel() }
        }
    }

    private fun parse(id: String?, type: String?, data: String): StreamEvent? {
        val obj = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
        return when (type) {
            "status" -> StreamEvent.Status(
                runId = obj.string("runId"),
                status = obj.string("status"),
                eventId = id,
            )
            "assistant" -> StreamEvent.Assistant(obj.string("text").orEmpty(), eventId = id)
            "thinking" -> StreamEvent.Thinking(obj.string("text").orEmpty(), eventId = id)
            "tool_call" -> StreamEvent.ToolCall(
                callId = obj.string("callId"),
                name = obj.string("name"),
                status = obj.string("status"),
                args = toolArgs(obj),
                eventId = id,
            )
            "result" -> StreamEvent.Result(
                runId = obj.string("runId"),
                status = obj.string("status"),
                text = obj.string("text"),
                eventId = id,
            )
            "error" -> {
                val message = obj.string("message") ?: "stream error"
                val recoverable = message.contains("no longer available", ignoreCase = true) ||
                    message.contains("stream_expired", ignoreCase = true)
                StreamEvent.StreamError(message, recoverable)
            }
            "done" -> StreamEvent.Done
            "heartbeat", "interaction_update" -> null
            else -> null
        }
    }

    private fun isCancel(t: Throwable): Boolean {
        val text = t.message.orEmpty()
        return text.contains("canceled", ignoreCase = true) ||
            text.contains("cancelled", ignoreCase = true) ||
            text.contains("Socket closed", ignoreCase = true)
    }

    private fun JsonObject?.string(key: String): String? {
        return this?.get(key)?.jsonPrimitive?.contentOrNull
    }

    private fun toolArgs(obj: JsonObject?): String? {
        val value = obj?.get("args") ?: return null
        return runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() ?: value.toString()
    }
}
