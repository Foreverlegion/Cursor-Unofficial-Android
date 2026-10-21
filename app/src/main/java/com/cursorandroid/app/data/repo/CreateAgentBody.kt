package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.envVarsError
import com.cursorandroid.app.data.api.sanitizePromptImages

/** Blank, or an error when a client agent id is malformed or combined with envVars. */
fun agentIdEnvVarsError(agentId: String?, envVars: Map<String, String>?): String? {
    val id = agentId?.trim().orEmpty()
    if (id.isEmpty()) return null
    val rest = id.removePrefix("bc-")
    if (!id.startsWith("bc-") || rest.isBlank() || id.any { it.isWhitespace() }) {
        return "Agent id must look like bc-…"
    }
    if (!envVars.isNullOrEmpty()) {
        return "agentId cannot be combined with env vars."
    }
    return null
}

/**
 * Stamps the phone client on the prompt. Session env vars are added only when the server mints the id.
 * A client agentId is sent alone: envVars, including CLIENT_APP, are omitted.
 */
fun assembleCreate(body: CreateAgentRequest): CreateAgentRequest {
    val id = body.agentId?.trim()?.takeIf { it.isNotEmpty() }
    val explicit = body.envVars?.takeIf { it.isNotEmpty() }
    val merged = if (id == null) (explicit ?: emptyMap()) + (ClientOrigin.KEY to ClientOrigin.ID) else null
    val error = agentIdEnvVarsError(id, explicit)
        ?: envVarsError(explicit)
        ?: envVarsError(body.runEnvVars)
        ?: envVarsError(merged)
    if (error != null) throw IllegalArgumentException(error)
    val prompt = ClientOrigin.stamp(body.prompt).let { stamped ->
        stamped.copy(images = sanitizePromptImages(stamped.images))
    }
    return if (id == null) {
        body.copy(prompt = prompt, agentId = null, envVars = merged)
    } else {
        body.copy(prompt = prompt, agentId = id, envVars = null)
    }
}
