package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.ModelSelection
import com.cursorandroid.app.data.api.Prompt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val GROK_AGENT_NAME = "Grok chat"

private val GROK_SYSTEM = """
    You are Grok in a casual mobile chat. Reply directly and conversationally.
    Do not edit files, run shell commands, or open pull requests unless the user clearly asks for coding work.
    Keep answers concise unless the user wants more detail.
""".trimIndent()

/** Models returned by GET /v1/models that look like Grok. */
fun filterGrokModels(items: List<ModelItem>): List<ModelItem> {
    return items.filter { item ->
        val id = item.id.lowercase()
        val aliases = item.aliases.orEmpty().joinToString(" ").lowercase()
        id.contains("grok") || aliases.contains("grok")
    }.sortedBy { it.displayName ?: it.id }
}

fun pickDefaultGrokModel(items: List<ModelItem>): ModelItem? {
    val grok = filterGrokModels(items)
    if (grok.isEmpty()) return null
    val preferred = listOf("grok-4.7", "grok-4.6", "grok-4")
    for (want in preferred) {
        grok.firstOrNull { it.id.equals(want, ignoreCase = true) }?.let { return it }
        grok.firstOrNull { it.aliases.orEmpty().any { alias -> alias.equals(want, ignoreCase = true) } }?.let { return it }
    }
    grok.firstOrNull { item ->
        item.variants.orEmpty().any { it.isDefault == true }
    }?.let { return it }
    return grok.first()
}

fun grokModelSelection(model: ModelItem?, params: List<ModelParam>): ModelSelection? {
    val id = model?.id?.trim().orEmpty()
    if (id.isEmpty()) return null
    val picked = params.filter { it.id.isNotBlank() && it.value.isNotBlank() }
    return ModelSelection(id = id, params = picked.takeIf { it.isNotEmpty() })
}

fun grokCreatePrompt(userText: String): Prompt {
    val user = userText.trim()
    val body = buildString {
        append(GROK_SYSTEM)
        append("\n\nUser message:\n")
        append(user)
    }
    return Prompt(text = body)
}

fun grokFollowUpPrompt(userText: String): Prompt = Prompt(text = userText.trim())

@Serializable
data class GrokMessage(
    @SerialName("id") val id: String,
    @SerialName("role") val role: String,
    @SerialName("text") val text: String,
    @SerialName("runId") val runId: String? = null,
    @SerialName("pending") val pending: Boolean = false,
)

@Serializable
data class GrokChatSession(
    @SerialName("agentId") val agentId: String? = null,
    @SerialName("modelId") val modelId: String = "",
    @SerialName("modelParams") val modelParams: List<ModelParam> = emptyList(),
    @SerialName("messages") val messages: List<GrokMessage> = emptyList(),
    @SerialName("activeRunId") val activeRunId: String? = null,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)

fun grokCreateRequest(userText: String, model: ModelSelection?): CreateAgentRequest {
    return CreateAgentRequest(
        prompt = grokCreatePrompt(userText),
        model = model,
        name = GROK_AGENT_NAME,
    )
}

fun grokDisplayUserText(text: String): String = visibleUserText(text)

fun needsNewGrokAgent(session: GrokChatSession, modelId: String, params: List<ModelParam>): Boolean {
    if (session.agentId.isNullOrBlank()) return false
    if (!session.modelId.equals(modelId, ignoreCase = true)) return true
    if (session.modelParams != params) return true
    return false
}

fun grokMessageId(prefix: String, runId: String): String = "$prefix-$runId"
